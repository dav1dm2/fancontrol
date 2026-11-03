# Executar e demonstrar o Fan Control System

Este guia resume os recursos necessários para reproduzir a aplicação e realizar uma demonstração local.

## Requisitos

- Java 21;
- PostgreSQL 16 ou compatível;
- Mosquitto MQTT broker;
- acesso à mesma rede local para o computador e o ESP32;
- PlatformIO e uma placa compatível com `esp32dev` somente para usar o dispositivo físico.

O Maven Wrapper já está incluído. Não é necessário instalar Maven separadamente.

## Serviços locais

Crie no PostgreSQL um banco chamado `fancontrol`. O usuário configurado deve poder criar e alterar tabelas, pois o Flyway aplica as migrations no primeiro início.

Inicie o Mosquitto na porta TCP `1883`. Para a demonstração, o backend pode usar `tcp://localhost:1883`. O ESP32 deve usar o IPv4 local da máquina que hospeda o broker, por exemplo `192.168.1.XXX`.

Em uma rede local de demonstração, MQTT não usa TLS nem autenticação. Libere a porta 1883 no firewall se necessário. O painel web usa a porta 8080.

## Configuração do backend

Copie o arquivo de exemplo e informe valores locais:

```bash
cp .env.example .env
```

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

O arquivo `.env` fica fora do Git. Ao iniciar em um banco vazio, a aplicação cria o administrador inicial informado nessas variáveis e o Flyway cria a estrutura necessária.

Execute o backend na raiz do projeto:

```bash
./mvnw spring-boot:run
```

Abra:

- Painel: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI: `http://localhost:8080/v3/api-docs`

## Dispositivo físico

O firmware está em `firmware-esp32/`.

1. Abra essa pasta no PlatformIO.
2. Em `src/main.cpp`, substitua os valores censurados de `WIFI_SSID`, `WIFI_PASSWORD` e `MQTT_SERVER` pelos valores da rede local.
3. Defina `DEVICE_NAME` com o mesmo nome que será cadastrado no painel, por exemplo `garage2`.
4. Conecte a placa e faça o upload.
5. Abra o monitor serial em 115200 baud para acompanhar Wi-Fi, MQTT, NTP, PIR e relé.

O firmware usa GPIO 13 para o PIR e GPIO 12 para o relé ativo em nível baixo.

## Roteiro de demonstração

1. Inicie PostgreSQL, Mosquitto e o backend.
2. Faça login no painel com o administrador inicial.
3. Cadastre um dispositivo com o mesmo nome configurado em `DEVICE_NAME`.
4. Ligue o ESP32 e confirme que o painel mostra o dispositivo como `ONLINE`.
5. Acione o ventilador pelo painel e mostre a alteração física do relé e do estado retornado pelo dispositivo.
6. Gere movimento no sensor PIR e confirme a atualização de `Last Motion`.
7. Configure uma agenda semanal ou desligamento automático e explique que a configuração chega ao ESP32 por MQTT e fica salva na NVS.
8. Cadastre uma conta comum para mostrar que ela consulta os dispositivos, mas não recebe permissões administrativas.
9. Abra o Swagger para apresentar rotas, respostas e autenticação Bearer JWT.

## Testes automatizados

```bash
./mvnw test
```

O projeto também pode compilar o firmware pelo PlatformIO antes da gravação.
