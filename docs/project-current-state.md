# Fan Control System — Estado atual do projeto

**Data da revisão:** 3 de novembro de 2026<br>
**Estado analisado:** branch `feature/device-scheduling-firmware`

## 1. Objetivo do sistema

O Fan Control System centraliza o monitoramento e o controle de ventiladores ligados a microcontroladores ESP32. A aplicação Spring Boot oferece uma API REST, uma página web e integração MQTT com os dispositivos. Um operador humano pode consultar dispositivos; o administrador pode cadastrá-los, renomeá-los, removê-los, ligar ou desligar o ventilador e configurar automação semanal.

O dispositivo ESP32 é responsável pela leitura do sensor PIR, acionamento do relé, execução local da automação e comunicação com o broker MQTT. A aplicação central mantém o cadastro, o estado observado e as configurações desejadas.

## 2. Estado de versionamento e escopo atual

- A implementação de autenticação, JWT, Flyway e a interface inicial foi concluída anteriormente.
- Swagger/OpenAPI foi adicionado no commit `edb3f7d`.
- Há uma continuação local ainda não commitada: agendamento semanal, desligamento automático por inatividade e firmware ESP32 correspondente.
- A compilação Maven do estado atual foi executada com sucesso em 3 de novembro de 2026 usando `./mvnw -DskipTests compile`.
- A migration V3 e o firmware ainda precisam de validação integrada contra o PostgreSQL, broker MQTT e uma placa ESP32 antes de serem considerados concluídos.

## 3. Arquitetura e organização MVC

O backend usa Spring Boot com uma separação compatível com MVC:

| Camada | Responsabilidade no projeto |
|---|---|
| Controllers | Expõem rotas HTTP REST e encaminham cada requisição ao serviço correspondente. |
| Services | Aplicam regras de negócio, publicam comandos MQTT e processam eventos recebidos dos dispositivos. |
| Repositories | Persistem e consultam entidades usando Spring Data JPA. |
| Models / Entities | Representam tabelas do PostgreSQL, como `devices` e `app_users`. |
| DTOs | Definem os dados que entram e saem da API, sem expor diretamente as entidades. |
| Mappers | Convertem entidades em DTOs de resposta; `DeviceMapper` também converte o agendamento salvo como JSON em lista de horários. |
| View | A página Thymeleaf `index.html` funciona como painel web responsivo, com textos em inglês. |

### Controllers existentes

| Controller | Função |
|---|---|
| `WebController` | Serve a página principal em `GET /`. |
| `AuthController` | Cadastro, login e consulta da conta autenticada. |
| `DeviceController` | CRUD de dispositivos, comandos de ligar/desligar e configuração de automação. |
| `UserController` | Listagem e remoção administrativa de usuários. |

O projeto já possui mais de um controller REST por responsabilidades reais. Não há necessidade técnica de criar controllers artificiais apenas para aumentar a quantidade.

## 4. Domínio e persistência

### Dispositivo (`Device`)

Cada dispositivo possui:

- identificador e nome único;
- conectividade `ONLINE` ou `OFFLINE`;
- estado conhecido do ventilador;
- horário da última ativação do sensor PIR;
- horário do último sinal recebido;
- modo operacional: `MANUAL_ON`, `MANUAL_OFF` ou `SCHEDULED`;
- opção e intervalo de desligamento automático por inatividade;
- agenda semanal armazenada como JSON na coluna `weekly_schedule`.

### Usuário (`OperatorUser`)

Cada conta possui identificador, nome único, hash BCrypt da senha e papel:

- `ADMIN`: administra dispositivos e usuários;
- `USER`: consulta dispositivos.

O cadastro público sempre cria `USER`. O administrador inicial é criado por configuração local, não por uma escolha no formulário. Embora o modelo suporte mais de uma conta `ADMIN`, não existe rota pública ou interface para criar outro administrador.

### Flyway migrations

O Hibernate está configurado com `ddl-auto=validate`: ele valida as entidades, mas não cria ou modifica tabelas. As mudanças de banco pertencem ao Flyway:

| Migration | Conteúdo |
|---|---|
| `V1__create_devices_table.sql` | Cria a tabela de dispositivos e a restrição de status. |
| `V2__create_operator_users.sql` | Cria usuários, papéis e índice de nome único sem diferenciar maiúsculas/minúsculas. |
| `V3__add_device_schedule_and_automation.sql` | Adiciona os campos de modo, auto-shutoff e agenda semanal aos dispositivos. Está presente no diretório local e ainda faz parte da continuação não commitada. |

## 5. API REST atual

### Autenticação

| Método e rota | Acesso | Função |
|---|---|---|
| `POST /api/auth/register` | Público | Cria uma conta `USER`. |
| `POST /api/auth/login` | Público | Valida nome e senha e devolve um JWT. |
| `GET /api/auth/me` | Autenticado | Retorna a conta associada ao JWT. |

### Dispositivos

| Método e rota | Acesso | Função |
|---|---|---|
| `GET /api/devices` | `USER` ou `ADMIN` | Lista dispositivos e configurações. |
| `GET /api/devices/{id}` | `USER` ou `ADMIN` | Consulta um dispositivo. |
| `POST /api/devices` | `ADMIN` | Cadastra dispositivo. |
| `PUT /api/devices/{id}` | `ADMIN` | Renomeia dispositivo. |
| `DELETE /api/devices/{id}` | `ADMIN` | Remove dispositivo. |
| `POST /api/devices/{id}/fan/on` | `ADMIN` | Publica comando para ligar o ventilador. |
| `POST /api/devices/{id}/fan/off` | `ADMIN` | Publica comando para desligar o ventilador. |
| `PUT /api/devices/{id}/schedule` | `ADMIN` | Salva e publica a configuração de agenda e auto-shutoff. Está na continuação local. |

### Usuários

| Método e rota | Acesso | Função |
|---|---|---|
| `GET /api/users` | `ADMIN` | Lista somente identificador e nome das contas. |
| `DELETE /api/users/{id}` | `ADMIN` | Remove uma conta, exceto o último administrador. |

## 6. Segurança: Spring Security e JWT

O sistema usa Spring Security sem sessão no servidor. No login, a senha recebida é comparada ao hash BCrypt armazenado no PostgreSQL. Quando a autenticação é válida, a aplicação emite um JWT assinado com HS256, usando o segredo local `JWT_SECRET`.

O token contém o ID da conta e expira, por padrão, após uma hora. O painel guarda o token no navegador e o envia em cada chamada protegida no cabeçalho:

```text
Authorization: Bearer <JWT>
```

Para cada requisição, o sistema valida assinatura, emissor e expiração. Depois consulta a conta no banco para obter o papel atual. Portanto, um token deixa de funcionar se a conta for removida, mesmo antes do prazo normal de expiração.

Rotas públicas: página inicial, cadastro, login, especificação OpenAPI e Swagger UI. Rotas de leitura de dispositivos aceitam `USER` e `ADMIN`; alterações em dispositivos e usuários exigem `ADMIN`.

Configurações privadas — banco, segredo JWT e administrador inicial — ficam em `.env`, que é ignorado pelo Git. O arquivo `.env.example` contém apenas exemplos.

## 7. Integração MQTT

O broker MQTT é configurado pela variável `MQTT_BROKER_URL`. O backend usa Spring Integration MQTT e QoS 1 para as trocas principais.

### Dispositivo para aplicação

| Tópico | Payload atual | Efeito no backend |
|---|---|---|
| `fan/{deviceName}/heartbeat` | `ping` | Atualiza `lastSeen`, marca o dispositivo `ONLINE` e reenvia a configuração se ele acabou de retornar. |
| `fan/{deviceName}/motion` | `detected` | Atualiza última ativação do sensor e presença. |
| `fan/{deviceName}/status` | `online` ou `offline` | Atualiza a conectividade; ao ficar offline, o estado do ventilador é tratado como desligado. |
| `fan/{deviceName}/state` | `true` ou `false` | Atualiza o estado físico reportado do ventilador. |

Um agendador Spring executa a cada 10 segundos. Quando o último sinal ultrapassa `mqtt.heartbeat.timeout.seconds`, o dispositivo passa para `OFFLINE`.

### Aplicação para dispositivo

| Tópico | Payload | Efeito esperado no ESP32 |
|---|---|---|
| `fan/{deviceName}/command` | `{"fan": true}` ou `{"fan": false}` | Coloca o dispositivo em modo manual, atualiza o relé e publica o estado. |
| `fan/{deviceName}/config` | JSON de `DeviceScheduleDto` | Atualiza modo, auto-shutoff e agenda semanal. A mensagem é retida para que um ESP32 reconectado possa recebê-la. |

Ao receber um heartbeat de um dispositivo que estava offline, o backend monta a configuração persistida e a publica novamente em `fan/{deviceName}/config`.

## 8. Firmware ESP32 em continuação

O diretório `firmware-esp32/` contém um projeto PlatformIO para `esp32dev`, usando Arduino, PubSubClient e ArduinoJson.

O firmware:

- lê um sensor PIR no GPIO 13;
- comanda um relé ativo em nível baixo no GPIO 12;
- publica movimento, heartbeat, presença e mudança de estado;
- assina os tópicos de comando e configuração do dispositivo;
- sincroniza o relógio via NTP com fuso UTC-3;
- avalia a agenda semanal uma vez por segundo;
- desliga fora da janela agendada;
- pode desligar após o tempo configurado sem movimento;
- grava modo, tempo e até 14 janelas de agenda na memória não volátil NVS.

Os comandos manuais alteram o modo para `MANUAL_ON` ou `MANUAL_OFF`. O modo `SCHEDULED` é aplicado localmente pelo ESP32, o que permite que a agenda continue funcionando mesmo se o backend estiver temporariamente indisponível depois de a configuração ter sido recebida.

## 9. Interface web

A página principal é uma interface única responsiva em inglês.

- Sem sessão, apresenta login e cadastro.
- `USER` visualiza os dispositivos.
- `ADMIN` também pode adicionar, renomear, remover, ligar e desligar dispositivos, além de listar e remover usuários.
- A continuação local adiciona cartões com modo, estado, agenda e auto-shutoff, além de um modal para configurar a automação.

O navegador inclui o JWT nas chamadas da API. A interface melhora a experiência, mas as regras decisivas são aplicadas novamente no backend pelo Spring Security.

## 10. Swagger / OpenAPI

Swagger/OpenAPI foi implementado com `springdoc-openapi`.

- A configuração declara o esquema Bearer JWT.
- Controllers e DTOs possuem anotações com descrição de rotas, parâmetros, respostas e modelos.
- O Swagger UI é público, enquanto as chamadas de negócio continuam protegidas.
- A documentação pode ser acessada pela aplicação em `/swagger-ui/index.html`.

Há um teste dedicado que verifica se o OpenAPI declara título, esquema Bearer e os endpoints principais.

## 11. Testes e verificações conhecidas

O repositório contém:

- testes unitários de `DeviceService`;
- testes MVC de `DeviceController`;
- testes de integração de segurança para JWT, papéis e remoção de contas;
- teste de contexto/Flyway;
- teste de documentação OpenAPI.

A última verificação desta revisão compilou o código principal com sucesso. A suíte completa e o teste físico do firmware não foram executados nesta revisão, pois a implementação de agenda ainda está local e sua migration pode alterar o banco de desenvolvimento.

## 12. Pontos a decidir ou validar antes de considerar a continuação concluída

Estes itens foram identificados na revisão e não foram alterados:

1. **Credenciais no firmware:** SSID, senha Wi-Fi e endereço do broker estão escritos diretamente em `firmware-esp32/src/main.cpp`. O firmware não deve ser enviado ao GitHub com dados reais; uma configuração local ignorada pelo Git será necessária antes do commit.
2. **Limites da agenda:** o firmware aceita no máximo 14 janelas semanais, mas a API ainda não limita a quantidade. A API também valida o formato das horas, porém não valida se o fim ocorre depois do início nem impede janelas sobrepostas.
3. **Entrega da configuração:** a configuração é salva no banco antes da publicação MQTT. Se o broker falhar, a API retorna erro, mas a configuração já pode estar persistida e será reenviada no próximo heartbeat. É preciso decidir se esse comportamento eventual é o desejado para a apresentação.
4. **Nomes usados em tópicos MQTT:** o nome do dispositivo forma parte do tópico. A validação atual aceita qualquer texto não vazio; restringir nomes a caracteres seguros para tópicos evitaria espaços, barras e curingas acidentais.
5. **Datas JSON:** `DeviceResponse` contém `LocalDateTime`, enquanto existe um `ObjectMapper` criado manualmente em `JacksonConfig`. Convém testar uma resposta com datas preenchidas para confirmar a serialização correta de Java Time.
6. **Idioma da documentação:** o painel está em inglês, mas as descrições Swagger continuam em português. Isso não impede o funcionamento; é apenas uma decisão de consistência visual a tomar.
7. **Histórico de comandos:** foi deliberadamente adiado. Ele seria uma evolução de auditoria, não uma dependência para o controle atual funcionar.
8. **Docker:** permanece opcional e ainda não está presente.

## 13. Síntese

O projeto atual já atende à base técnica da AV2: API MVC, DTOs, mapper, JPA/PostgreSQL, exceções HTTP, Flyway, autenticação JWT com Spring Security, múltiplos controllers, MQTT, painel web e Swagger/OpenAPI.

A funcionalidade de automação representa uma expansão coerente do domínio original. Ela está em uma etapa local de integração: compila, possui migration e firmware correspondente, mas ainda precisa ser validada ponta a ponta e versionada de forma segura.
