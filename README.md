# Fan Control System

Sistema centralizado para monitorar e controlar ventiladores conectados a dispositivos ESP32. O projeto une uma API Spring Boot, PostgreSQL, Mosquitto MQTT, painel web, autenticação JWT e firmware para ESP32 com sensor PIR e relé.

O administrador cadastra e configura dispositivos. Usuários autenticados podem consultar o estado dos ventiladores. Cada ESP32 recebe comandos e configurações por MQTT, informa sua presença, reporta movimento e publica o estado físico do relé.

## Recursos implementados

- CRUD de dispositivos com nome único;
- painel web responsivo em inglês;
- autenticação JWT com Spring Security;
- perfis `ADMIN` e `USER`;
- senhas protegidas com BCrypt;
- controle de acesso por papel;
- PostgreSQL com JPA e migrations Flyway;
- integração MQTT entre Spring Boot e ESP32;
- monitoramento por heartbeat e marcação automática de dispositivo offline;
- acionamento manual de ventilador;
- agenda semanal e desligamento automático por ausência de movimento;
- persistência da configuração de automação no PostgreSQL e na memória NVS do ESP32;
- documentação interativa com Swagger/OpenAPI.

Para uma visão técnica detalhada da arquitetura, consulte [docs/project-current-state.md](docs/project-current-state.md).

## Arquitetura

```text
Browser / Swagger UI
        |
        | HTTP + JWT
        v
Spring Boot application
  Controllers -> Services -> Repositories -> PostgreSQL
        |
        | MQTT
        v
Mosquitto broker <----> ESP32 + PIR sensor + relay + fan
```

## Requisitos

- Java 21;
- PostgreSQL 16 ou compatível;
- Mosquitto MQTT broker;
- Git;
- acesso à mesma rede local para o computador que executa o broker e o ESP32;
- para compilar ou gravar o firmware: PlatformIO com uma placa compatível com `esp32dev`.

O Maven Wrapper já está no repositório. Não é necessário instalar Maven separadamente.

## Serviços locais necessários

### PostgreSQL

Crie um banco chamado `fancontrol`. O usuário configurado precisa ter permissão para criar e alterar tabelas, pois o Flyway executará as migrations no primeiro início.

### Mosquitto

O broker deve escutar na porta TCP `1883`. Para a demonstração local, o backend pode acessar `tcp://localhost:1883`; o ESP32 deve usar o endereço IP local do computador que hospeda o Mosquitto.

Em uma rede local simples, não há TLS nem autenticação MQTT configurados. Essa configuração é adequada para demonstração em rede controlada.

### Rede local

O computador com Spring Boot, PostgreSQL e Mosquitto pode ser o mesmo. O ESP32 precisa se conectar à mesma rede Wi-Fi ou hotspot e alcançar a porta `1883` desse computador.

Se o broker estiver no notebook, use o IPv4 local dele no firmware. Exemplo ilustrativo:

```text
MQTT_SERVER = 192.168.1.XXX
```

Libere a porta 1883 no firewall local quando necessário. O painel e Swagger usam a porta 8080.

## Configuração do backend

1. Copie o arquivo de exemplo:

   ```bash
   cp .env.example .env
   ```

2. Edite `.env` e informe as credenciais locais. Exemplo:

   ```properties
   DB_URL=jdbc:postgresql://localhost:5432/fancontrol
   DB_USERNAME=postgres
   DB_PASSWORD=sua-senha-local

   MQTT_BROKER_URL=tcp://localhost:1883
   MQTT_CLIENT_ID=fancontrol-server

   JWT_SECRET=um-segredo-local-com-pelo-menos-32-caracteres
   JWT_EXPIRATION_SECONDS=3600

   ADMIN_INITIAL_NAME=admin
   ADMIN_INITIAL_PASSWORD=uma-senha-local-com-pelo-menos-8-caracteres
   ```

O `.env` não deve ser enviado ao GitHub. Na primeira inicialização, a aplicação cria o administrador indicado por `ADMIN_INITIAL_NAME` e `ADMIN_INITIAL_PASSWORD`, caso esse nome ainda não exista.

## Execução da aplicação

No diretório do projeto:

```bash
./mvnw spring-boot:run
```

No primeiro início em um banco vazio, Flyway executa as migrations automaticamente:

- V1: tabela de dispositivos;
- V2: tabela de usuários;
- V3: campos de agendamento e automação.

Depois, acesse:

- Painel: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Especificação OpenAPI: `http://localhost:8080/v3/api-docs`

## Configuração e gravação do firmware

O firmware está em `firmware-esp32/` e usa PlatformIO.

1. Abra a pasta `firmware-esp32` no PlatformIO.
2. Em `src/main.cpp`, substitua os valores censurados de `WIFI_SSID`, `WIFI_PASSWORD` e `MQTT_SERVER` pelos valores da sua rede local.
3. Confirme que `DEVICE_NAME` corresponde exatamente ao nome cadastrado no painel, por exemplo `garage2`.
4. Conecte a placa ESP32 e faça o upload pelo PlatformIO.
5. Abra o monitor serial em 115200 baud para acompanhar Wi-Fi, MQTT, NTP, sensor e relé.

O firmware usa:

- GPIO 13: entrada do sensor PIR;
- GPIO 12: saída do relé;
- relé ativo em nível baixo: `LOW` liga e `HIGH` desliga.

Ele sincroniza a hora via NTP para executar a agenda semanal. Portanto, o ESP32 precisa ter acesso à internet ao menos durante a sincronização de horário.

## Protocolo MQTT

O nome do dispositivo participa do tópico e deve ser igual no cadastro e no firmware.

| Direção | Tópico | Exemplo de payload |
|---|---|---|
| ESP32 → backend | `fan/{deviceName}/heartbeat` | `ping` |
| ESP32 → backend | `fan/{deviceName}/motion` | `detected` |
| ESP32 → backend | `fan/{deviceName}/status` | `online` ou `offline` |
| ESP32 → backend | `fan/{deviceName}/state` | `true` ou `false` |
| Backend → ESP32 | `fan/{deviceName}/command` | `{"fan":true}` |
| Backend → ESP32 | `fan/{deviceName}/config` | JSON de agenda, modo e auto-shutoff |

O tópico de configuração é enviado como mensagem MQTT retida. Isso permite que um ESP32 receba a última configuração depois de se reconectar ao broker.

## Fluxo de demonstração

1. Inicie PostgreSQL e Mosquitto.
2. Configure `.env` e execute o backend.
3. Faça login no painel com o administrador inicial.
4. Cadastre um dispositivo com o mesmo nome usado em `DEVICE_NAME` no firmware.
5. Ligue o ESP32 e confirme que o painel mostra o dispositivo como `ONLINE`.
6. Envie comandos de ligar e desligar pelo painel ou Swagger.
7. Gere movimento no sensor PIR e confirme a atualização de `Last Motion`.
8. Como administrador, configure uma agenda semanal e o auto-shutoff; o ESP32 recebe a configuração no tópico MQTT e a salva na NVS.
9. Cadastre uma conta comum para demonstrar que ela apenas consulta dispositivos.

## Testes

Execute a suíte Java com:

```bash
./mvnw test
```

Ela cobre regras de dispositivos, respostas HTTP, JWT, permissões, Flyway e documentação OpenAPI. A migration V3 será aplicada ao banco configurado se ainda não estiver presente.

## Licença

Projeto acadêmico desenvolvido para a disciplina.
