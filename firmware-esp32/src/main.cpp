#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <time.h>

// ==========================================
// CONFIGURAÇÕES DE REDE E BROKER MQTT
// ==========================================
const char* WIFI_SSID     = "sapato_de_barXXX";    // Substitua pelo nome da sua rede local
const char* WIFI_PASSWORD = "12345567XXX";         // Substitua pela senha da sua rede local
const char* MQTT_SERVER   = "10.234.243.XXX";      // Substitua pelo IP local do host com Mosquitto
const int   MQTT_PORT     = 1883;

// Nome do dispositivo (deve bater com o nome cadastrado no Spring Boot)
const char* DEVICE_NAME   = "garage2";

// ==========================================
// PINOS DE HARDWARE
// ==========================================
// Pino conectado ao pino de Sinal (OUT) do sensor PIR
const int PIN_PIR_SENSOR = 13;

// Pino conectado ao pino de controle do Relé (IN)
// Relé ativo em LOW: acionamento LOW liga o ventilador
const int PIN_RELAY_FAN  = 12;

// ==========================================
// SERVIDOR NTP PARA SINCRONIZAÇÃO DE HORA
// Fuso horário: UTC-3 (Horário de Brasília)
// ==========================================
const char* NTP_SERVER_1 = "pool.ntp.org";
const char* NTP_SERVER_2 = "a.st1.ntp.br";
const long  GMT_OFFSET_SEC = -3 * 3600;
const int   DAYLIGHT_OFFSET_SEC = 0;

// ==========================================
// ESTRUTURAS DE DADOS DE AGENDAMENTO
// ==========================================
enum ScheduleMode {
    MANUAL_ON,
    MANUAL_OFF,
    SCHEDULED
};

struct ScheduleWindow {
    int dayOfWeek; // 1 = Segunda, 7 = Domingo
    int startMinute; // minutos desde 00:00 (ex: 08:30 = 8*60+30 = 510)
    int endMinute;
};

const int MAX_WINDOWS = 14;
ScheduleWindow weeklyWindows[MAX_WINDOWS];
int windowCount = 0;

ScheduleMode currentMode = MANUAL_OFF;
bool autoShutoffEnabled = false;
int autoShutoffSeconds = 300;

// Estado operacional
bool fanRunning = false;
unsigned long lastMotionMillis = 0;
unsigned long lastHeartbeatMillis = 0;
unsigned long lastEvaluationMillis = 0;

// Objetos de Rede e Armazenamento NVS
WiFiClient espClient;
PubSubClient mqttClient(espClient);
Preferences preferences;

// Tópicos MQTT
char topicCommand[64];
char topicConfig[64];
char topicState[64];
char topicMotion[64];
char topicHeartbeat[64];

// ==========================================
// PROTÓTIPOS
// ==========================================
void setupPins();
void connectWiFi();
void connectMQTT();
void syncNTP();
void onMqttMessage(char* topic, byte* payload, unsigned int length);
void handleCommandJson(const char* jsonStr);
void handleConfigJson(const char* jsonStr);
void evaluateFanState();
void setFanPhysical(bool turnOn);
void loadConfigFromNVS();
void saveConfigToNVS();
int parseTimeToMinutes(const char* timeStr);

// ==========================================
// SETUP
// ==========================================
void setup() {
    Serial.begin(115200);
    delay(1000);
    Serial.println("\n--- INICIANDO ESP32 FAN CONTROLLER ---");

    setupPins();

    // Monta tópicos MQTT dinamicamente com base no DEVICE_NAME
    snprintf(topicCommand,   sizeof(topicCommand),   "fan/%s/command",   DEVICE_NAME);
    snprintf(topicConfig,    sizeof(topicConfig),    "fan/%s/config",    DEVICE_NAME);
    snprintf(topicState,     sizeof(topicState),     "fan/%s/state",     DEVICE_NAME);
    snprintf(topicMotion,    sizeof(topicMotion),    "fan/%s/motion",    DEVICE_NAME);
    snprintf(topicHeartbeat, sizeof(topicHeartbeat), "fan/%s/heartbeat", DEVICE_NAME);

    // Carrega última programação salva na memória não-volátil (NVS Flash)
    loadConfigFromNVS();

    // Conecta Wi-Fi
    connectWiFi();

    // Sincroniza relógio interno via NTP
    syncNTP();

    // Configura MQTT
    mqttClient.setServer(MQTT_SERVER, MQTT_PORT);
    mqttClient.setCallback(onMqttMessage);
    mqttClient.setBufferSize(2048); // Suporta JSON de agendamento semanal completo
}

// ==========================================
// LOOP PRINCIPAL
// ==========================================
void loop() {
    // Reconexão Wi-Fi automática se cair
    if (WiFi.status() != WL_CONNECTED) {
        connectWiFi();
    }

    // Reconexão MQTT automática se Wi-Fi estiver ok
    if (WiFi.status() == WL_CONNECTED && !mqttClient.connected()) {
        connectMQTT();
    }
    mqttClient.loop();

    unsigned long now = millis();

    // Leitura do Sensor PIR de Movimento
    static int lastPirReading = LOW;
    int currentPirReading = digitalRead(PIN_PIR_SENSOR);
    if (currentPirReading == HIGH && lastPirReading == LOW) {
        Serial.println(">>> [SENSOR] Movimento detectado!");
        lastMotionMillis = now;

        if (mqttClient.connected()) {
            mqttClient.publish(topicMotion, "detected");
        }
    }
    lastPirReading = currentPirReading;

    // Heartbeat periódico a cada 5 segundos
    if (now - lastHeartbeatMillis >= 5000) {
        lastHeartbeatMillis = now;
        if (mqttClient.connected()) {
            mqttClient.publish(topicHeartbeat, "ping");
        }
    }

    // Avaliação da lógica do ventilador a cada 1 segundo
    if (now - lastEvaluationMillis >= 1000) {
        lastEvaluationMillis = now;
        evaluateFanState();
    }
}

// ==========================================
// HARDWARE / GPIO
// ==========================================
void setupPins() {
    pinMode(PIN_PIR_SENSOR, INPUT);
    pinMode(PIN_RELAY_FAN, OUTPUT);

    // Relé ativo em LOW: começa desligado (HIGH)
    digitalWrite(PIN_RELAY_FAN, HIGH);
    fanRunning = false;
}

void setFanPhysical(bool turnOn) {
    if (fanRunning != turnOn) {
        fanRunning = turnOn;
        // Relé ativo em LOW: LOW liga, HIGH desliga
        digitalWrite(PIN_RELAY_FAN, turnOn ? LOW : HIGH);
        Serial.printf(">>> [VENTILADOR] Estado alterado para: %s\n", turnOn ? "LIGADO" : "DESLIGADO");

        // Informa a plataforma da mudança de estado físico
        if (mqttClient.connected()) {
            mqttClient.publish(topicState, turnOn ? "true" : "false", true);
        }
    }
}

// ==========================================
// LÓGICA DE DECISÃO E AGENDAMENTO
// ==========================================
void evaluateFanState() {
    // 1. Modo Manual
    if (currentMode == MANUAL_ON) {
        setFanPhysical(true);
        return;
    }
    if (currentMode == MANUAL_OFF) {
        setFanPhysical(false);
        return;
    }

    // 2. Modo Programado (SCHEDULED)
    // Obtém dia da semana e minuto atual
    time_t rawTime;
    time(&rawTime);
    struct tm timeInfo;
    if (!localtime_r(&rawTime, &timeInfo) || timeInfo.tm_year < (2020 - 1900)) {
        // Se ainda não sincronizou hora com NTP, mantém desligado por segurança
        setFanPhysical(false);
        return;
    }

    // tm_wday: 0 = Domingo, 1 = Segunda ... 6 = Sábado
    // Ajuste para nosso padrão: 1 = Segunda, ..., 7 = Domingo
    int currentDayOfWeek = (timeInfo.tm_wday == 0) ? 7 : timeInfo.tm_wday;
    int currentMinuteOfDay = (timeInfo.tm_hour * 60) + timeInfo.tm_min;

    bool withinScheduledWindow = false;
    for (int i = 0; i < windowCount; i++) {
        if (weeklyWindows[i].dayOfWeek == currentDayOfWeek) {
            if (currentMinuteOfDay >= weeklyWindows[i].startMinute &&
                currentMinuteOfDay < weeklyWindows[i].endMinute) {
                withinScheduledWindow = true;
                break;
            }
        }
    }

    // Se estiver fora do horário programado, desliga
    if (!withinScheduledWindow) {
        setFanPhysical(false);
        return;
    }

    // Se está dentro do horário, checa a regra do auto-shutoff por inatividade
    if (autoShutoffEnabled) {
        unsigned long elapsedSinceMotion = (millis() - lastMotionMillis) / 1000;
        if (lastMotionMillis > 0 && elapsedSinceMotion >= (unsigned long)autoShutoffSeconds) {
            // Ausência prolongada detectada: desliga mesmo dentro do horário
            setFanPhysical(false);
            return;
        }
    }

    // Dentro do horário e sem timeout de ausência: liga
    setFanPhysical(true);
}

// ==========================================
// PROCESSAMENTO MQTT
// ==========================================
void onMqttMessage(char* topic, byte* payload, unsigned int length) {
    char message[length + 1];
    memcpy(message, payload, length);
    message[length] = '\0';

    Serial.printf(">>> [MQTT INBOUND] Tópico: %s | Payload: %s\n", topic, message);

    if (strcmp(topic, topicCommand) == 0) {
        handleCommandJson(message);
    } else if (strcmp(topic, topicConfig) == 0) {
        handleConfigJson(message);
    }
}

void handleCommandJson(const char* jsonStr) {
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, jsonStr);
    if (err) {
        Serial.printf("Erro ao deserializar comando: %s\n", err.c_str());
        return;
    }

    if (doc["fan"].is<bool>()) {
        bool fanReq = doc["fan"].as<bool>();
        currentMode = fanReq ? MANUAL_ON : MANUAL_OFF;
        Serial.printf("Comando manual recebido: %s\n", fanReq ? "LIGAR" : "DESLIGAR");
        setFanPhysical(fanReq);
        saveConfigToNVS();
    }
}

void handleConfigJson(const char* jsonStr) {
    JsonDocument doc;
    DeserializationError err = deserializeJson(doc, jsonStr);
    if (err) {
        Serial.printf("Erro ao deserializar config: %s\n", err.c_str());
        return;
    }

    // Atualiza modo
    const char* modeStr = doc["mode"];
    if (modeStr != nullptr) {
        if (strcmp(modeStr, "MANUAL_ON") == 0) currentMode = MANUAL_ON;
        else if (strcmp(modeStr, "MANUAL_OFF") == 0) currentMode = MANUAL_OFF;
        else if (strcmp(modeStr, "SCHEDULED") == 0) currentMode = SCHEDULED;
    }

    // Atualiza auto-shutoff
    if (doc["autoShutoffEnabled"].is<bool>()) {
        autoShutoffEnabled = doc["autoShutoffEnabled"].as<bool>();
    }
    if (doc["autoShutoffSeconds"].is<int>()) {
        autoShutoffSeconds = doc["autoShutoffSeconds"].as<int>();
    }

    // Atualiza lista de janelas semanais
    JsonArray arr = doc["weeklySchedule"].as<JsonArray>();
    windowCount = 0;
    for (JsonObject item : arr) {
        if (windowCount >= MAX_WINDOWS) break;
        int d = item["dayOfWeek"] | 1;
        const char* st = item["startTime"] | "00:00";
        const char* et = item["endTime"] | "00:00";

        weeklyWindows[windowCount].dayOfWeek = d;
        weeklyWindows[windowCount].startMinute = parseTimeToMinutes(st);
        weeklyWindows[windowCount].endMinute = parseTimeToMinutes(et);
        windowCount++;
    }

    Serial.printf("Configuração atualizada: Modo=%d, AutoShutoff=%d (%ds), Janelas=%d\n",
                  currentMode, autoShutoffEnabled, autoShutoffSeconds, windowCount);

    saveConfigToNVS();
    evaluateFanState();
}

int parseTimeToMinutes(const char* timeStr) {
    if (!timeStr || strlen(timeStr) < 5) return 0;
    int h = (timeStr[0] - '0') * 10 + (timeStr[1] - '0');
    int m = (timeStr[3] - '0') * 10 + (timeStr[4] - '0');
    return (h * 60) + m;
}

// ==========================================
// PERSISTÊNCIA NVS (MEMÓRIA FLASH)
// ==========================================
void loadConfigFromNVS() {
    preferences.begin("fanctl", true);
    currentMode = (ScheduleMode)preferences.getInt("mode", MANUAL_OFF);
    autoShutoffEnabled = preferences.getBool("autoShOff", false);
    autoShutoffSeconds = preferences.getInt("shOffSec", 300);
    windowCount = preferences.getInt("winCount", 0);
    if (windowCount > MAX_WINDOWS) windowCount = MAX_WINDOWS;

    for (int i = 0; i < windowCount; i++) {
        char kD[16], kS[16], kE[16];
        snprintf(kD, sizeof(kD), "w_d_%d", i);
        snprintf(kS, sizeof(kS), "w_s_%d", i);
        snprintf(kE, sizeof(kE), "w_e_%d", i);
        weeklyWindows[i].dayOfWeek = preferences.getInt(kD, 1);
        weeklyWindows[i].startMinute = preferences.getInt(kS, 0);
        weeklyWindows[i].endMinute = preferences.getInt(kE, 0);
    }
    preferences.end();
    Serial.println("Configurações anteriores recuperadas com sucesso da NVS Flash.");
}

void saveConfigToNVS() {
    preferences.begin("fanctl", false);
    preferences.putInt("mode", (int)currentMode);
    preferences.putBool("autoShOff", autoShutoffEnabled);
    preferences.putInt("shOffSec", autoShutoffSeconds);
    preferences.putInt("winCount", windowCount);

    for (int i = 0; i < windowCount; i++) {
        char kD[16], kS[16], kE[16];
        snprintf(kD, sizeof(kD), "w_d_%d", i);
        snprintf(kS, sizeof(kS), "w_s_%d", i);
        snprintf(kE, sizeof(kE), "w_e_%d", i);
        preferences.putInt(kD, weeklyWindows[i].dayOfWeek);
        preferences.putInt(kS, weeklyWindows[i].startMinute);
        preferences.putInt(kE, weeklyWindows[i].endMinute);
    }
    preferences.end();
}

// ==========================================
// CONEXÃO WI-FI & MQTT & NTP
// ==========================================
void connectWiFi() {
    if (WiFi.status() == WL_CONNECTED) return;
    Serial.printf("Conectando ao Wi-Fi %s...", WIFI_SSID);
    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    unsigned long startAttempt = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < 10000) {
        delay(500);
        Serial.print(".");
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("\nWi-Fi Conectado! IP da ESP: %s\n", WiFi.localIP().toString().c_str());
    } else {
        Serial.println("\nFalha momentânea no Wi-Fi. Tentará novamente no próximo ciclo.");
    }
}

void syncNTP() {
    if (WiFi.status() != WL_CONNECTED) return;
    Serial.println("Sincronizando hora com servidor NTP...");
    configTime(GMT_OFFSET_SEC, DAYLIGHT_OFFSET_SEC, NTP_SERVER_1, NTP_SERVER_2);

    struct tm timeInfo;
    int retries = 0;
    while (!getLocalTime(&timeInfo) && retries++ < 10) {
        delay(500);
        Serial.print(".");
    }
    if (retries < 10) {
        Serial.printf("\nHora sincronizada: %02d:%02d:%02d (Dia da semana: %d)\n",
                      timeInfo.tm_hour, timeInfo.tm_min, timeInfo.tm_sec, timeInfo.tm_wday);
    } else {
        Serial.println("\nAviso: Não foi possível obter hora NTP agora.");
    }
}

void connectMQTT() {
    static unsigned long lastConnectAttempt = 0;
    if (millis() - lastConnectAttempt < 3000) return;
    lastConnectAttempt = millis();

    Serial.print("Conectando ao Broker MQTT...");
    String clientId = "ESP32-" + String(DEVICE_NAME) + "-" + String((uint32_t)ESP.getEfuseMac(), HEX);

    // Conecta configurando o Last Will and Testament (LWT) no tópico status
    char topicStatus[64];
    snprintf(topicStatus, sizeof(topicStatus), "fan/%s/status", DEVICE_NAME);

    if (mqttClient.connect(clientId.c_str(), topicStatus, 1, true, "offline")) {
        Serial.println(" CONECTADO!");
        // Avisa que está online
        mqttClient.publish(topicStatus, "online", true);

        // Se inscreve nos comandos e configurações
        mqttClient.subscribe(topicCommand, 1);
        mqttClient.subscribe(topicConfig, 1);

        // Envia heartbeat inicial imediatamente
        mqttClient.publish(topicHeartbeat, "ping");
    } else {
        Serial.printf(" Falha, rc=%d. Tentará novamente...\n", mqttClient.state());
    }
}
