# Fan Control System

O Fan Control System centraliza o monitoramento e o controle de ventiladores ligados a microcontroladores ESP32. A aplicação reúne uma API REST em Spring Boot, painel web, PostgreSQL, MQTT e firmware embarcado para que um operador acompanhe dispositivos e administre seu funcionamento.

O projeto foi desenvolvido para uma disciplina de desenvolvimento de aplicações, com especialidade em IoT, sistemas embarcados e comunicação MQTT.

## Objetivo

Um administrador cadastra os dispositivos e configura seu funcionamento. Usuários autenticados consultam o estado dos ventiladores. Cada ESP32 lê o sensor PIR, aciona o relé, executa a automação local e se comunica com a aplicação central pelo broker MQTT.

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

O backend usa uma organização MVC. Controllers expõem as rotas, services concentram regras de negócio e a comunicação MQTT, repositories acessam o banco por Spring Data JPA e a view Thymeleaf entrega o painel responsivo.

| Camada | Responsabilidade |
|---|---|
| Controllers | Rotas HTTP REST e página web. |
| Services | Regras de negócio, comandos MQTT e processamento de eventos. |
| Repositories | Persistência e consultas com Spring Data JPA. |
| Models / Entities | Representação das tabelas `devices` e `app_users`. |
| DTOs e mappers | Entrada e saída da API sem expor entidades diretamente. |
| View | Painel web em Thymeleaf com textos em inglês. |

## Funcionalidades

- cadastro, consulta, renomeação e remoção de dispositivos;
- estado online/offline por heartbeat MQTT;
- leitura de movimento pelo sensor PIR e registro do último evento;
- acionamento manual do ventilador por relé;
- agenda semanal e desligamento automático por inatividade;
- autenticação JWT, senhas protegidas com BCrypt e perfis `ADMIN` e `USER`;
- autorização: administradores alteram dispositivos e usuários; usuários comuns somente consultam dispositivos;
- documentação interativa da API com Swagger/OpenAPI.

## Persistência e migrations

PostgreSQL armazena dispositivos, usuários e configurações de automação. O Flyway controla a estrutura do banco, enquanto o Hibernate somente a valida (`ddl-auto=validate`).

| Migration | Conteúdo |
|---|---|
| `V1__create_devices_table.sql` | Cria dispositivos e a restrição de status. |
| `V2__create_operator_users.sql` | Cria usuários, papéis e nome único sem diferenciar maiúsculas/minúsculas. |
| `V3__add_device_schedule_and_automation.sql` | Adiciona modo, auto-shutoff e agenda semanal aos dispositivos. |

## API e segurança

O projeto possui controllers com responsabilidades distintas:

| Controller | Responsabilidade |
|---|---|
| `WebController` | Entrega o painel em `GET /`. |
| `AuthController` | Cadastro, login e conta autenticada. |
| `DeviceController` | CRUD de dispositivos, comandos do ventilador e automação. |
| `UserController` | Listagem e remoção administrativa de usuários. |

No login, Spring Security valida a senha contra o hash BCrypt e emite um JWT assinado com HS256. O navegador envia o token em `Authorization: Bearer <JWT>` nas chamadas protegidas. O backend valida assinatura, expiração e papel do usuário antes de liberar cada ação.

Rotas públicas incluem a página inicial, cadastro, login e documentação Swagger. A leitura de dispositivos aceita `USER` e `ADMIN`; mudanças em dispositivos e usuários exigem `ADMIN`.

## Comunicação MQTT

O backend usa Spring Integration MQTT com QoS 1. O nome do dispositivo faz parte dos tópicos e precisa coincidir no cadastro e no firmware.

| Direção | Tópico | Função |
|---|---|---|
| ESP32 para backend | `fan/{deviceName}/heartbeat` | Atualiza presença e marca o dispositivo como online. |
| ESP32 para backend | `fan/{deviceName}/motion` | Registra movimento detectado pelo PIR. |
| ESP32 para backend | `fan/{deviceName}/status` | Informa `online` ou `offline`. |
| ESP32 para backend | `fan/{deviceName}/state` | Informa o estado físico do relé. |
| Backend para ESP32 | `fan/{deviceName}/command` | Envia comando manual de ligar ou desligar. |
| Backend para ESP32 | `fan/{deviceName}/config` | Envia agenda, modo e auto-shutoff. |

O backend marca um dispositivo como `OFFLINE` quando o intervalo desde o último sinal ultrapassa o limite configurado. A configuração é enviada como mensagem retida para que o ESP32 a recupere após uma reconexão.

## Firmware ESP32

O diretório [`firmware-esp32`](firmware-esp32) contém o projeto PlatformIO para uma placa `esp32dev`.

- GPIO 13: sensor PIR;
- GPIO 12: relé ativo em nível baixo;
- sincronização de data e hora por NTP no fuso UTC-3;
- agenda avaliada localmente a cada segundo;
- configuração gravada na memória não volátil NVS.

Ao receber uma configuração de agenda, o ESP32 pode continuar executando-a localmente mesmo se o backend ficar temporariamente indisponível. Comandos manuais colocam o dispositivo em modo `MANUAL_ON` ou `MANUAL_OFF`; a automação usa o modo `SCHEDULED`.

## Interface e documentação

O painel é responsivo e mantém seus textos em inglês. Sem login, apresenta cadastro e autenticação. Uma conta `USER` visualiza os dispositivos; uma conta `ADMIN` também gerencia dispositivos, automação e usuários.

A documentação Swagger/OpenAPI declara as rotas, modelos, respostas e autenticação Bearer JWT. Ela fica disponível em `/swagger-ui/index.html` quando a aplicação está em execução.

## Testes

A suíte Java cobre regras de dispositivos, respostas HTTP, JWT, permissões, Flyway e a especificação OpenAPI. O firmware também foi compilado com PlatformIO para validar a configuração da placa e das bibliotecas.

## Executar e demonstrar

Consulte [DEMONSTRATION.md](DEMONSTRATION.md) para requisitos, configuração local, gravação do firmware e roteiro de demonstração.

## Licença

Projeto acadêmico desenvolvido para a disciplina.
