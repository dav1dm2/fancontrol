# Guia para recriar o Fan Control System do zero

**Objetivo:** servir como roteiro de estudo e implementação para reconstruir o sistema no IntelliJ usando Spring Initializr. Este documento descreve o comportamento observado no repositório, separa o que já funciona do que ainda precisa ser decidido e propõe testes provisórios para desenvolver por etapas.

> Este é um guia de reconstrução, não uma afirmação de que o sistema atual está pronto para produção. As decisões marcadas como **definir** precisam ser confirmadas antes ou durante a implementação. Use o código atual como referência de requisitos e exemplos, não como uma base que precisa ser copiada linha por linha.

## 1. O que o sistema deve fazer

O Fan Control System é uma aplicação central para administrar dispositivos que monitoram e controlam ventiladores. O operador usa uma página web ou a API para cadastrar e consultar dispositivos, renomeá-los, removê-los e solicitar que o ventilador seja ligado ou desligado. Cada dispositivo comunica atividade e estado à aplicação por MQTT. A aplicação persiste os dados em PostgreSQL.

O fluxo pretendido é:

```text
Operador → página web → controller web → template Thymeleaf
Operador/programa → API REST → controller REST → service → repository → PostgreSQL
Dispositivo → MQTT → handler de mensagens → service → repository → PostgreSQL
Operador → comando REST → service → canal MQTT → broker → dispositivo
```

O sistema guarda o estado conhecido do dispositivo. Enviar um comando significa que a aplicação solicitou uma ação; isso, por si só, não prova que o hardware executou a solicitação. Uma confirmação só deve ser apresentada quando o dispositivo reportar seu estado de volta.

## 2. Inventário do repositório analisado

### Funcionalidades e peças já presentes

- Aplicação Java com Spring Boot e Maven, com Maven Wrapper incluído.
- Entidade JPA `Device`, com ID, nome único, status online/offline, estado do ventilador, última ativação do sensor e última comunicação.
- Enum `Status` com `ONLINE` e `OFFLINE`.
- API REST para listar, consultar, cadastrar, renomear e remover dispositivos.
- API para solicitar ligar e desligar o ventilador.
- Camadas `DeviceController`, `DeviceService` e `DeviceRepository`.
- DTO de entrada `DeviceRequest`, DTO de saída `DeviceResponse` e `DeviceMapper`.
- Respostas de erro centralizadas em `GlobalExceptionHandler` e `ApiErrorResponse`.
- Página Thymeleaf que usa JavaScript `fetch` para chamar a API.
- MQTT via Spring Integration e Eclipse Paho: publicação de comandos e subscrição de heartbeat, movimento, status e estado.
- Tarefa agendada que marca dispositivo como offline quando não recebe comunicação dentro do timeout configurado.
- Testes unitários do serviço e controller de dispositivos e um teste de carregamento do contexto.
- Plano anterior em `docs/av2-plan.md`, com ideias para autenticação, histórico, Swagger e Flyway. Ele contém descrições do estado antigo e não deve ser lido como inventário atual sem comparar com o código.

### O que não está concluído ou requer decisão

- Não há autenticação nem autorização de operadores.
- Não há histórico persistido de comandos.
- Não há migrations de banco; Hibernate está configurado para atualizar o esquema automaticamente.
- Não há definição oficial e validada do protocolo MQTT, payloads, identificador estável de dispositivo, autenticação do broker ou política de reconexão completa.
- Não há ambiente reproduzível com PostgreSQL e broker para desenvolvimento/testes (por exemplo, Docker Compose).
- Não há testes de integração reais de PostgreSQL/MQTT nem teste da interface no navegador.
- O painel, os títulos e mensagens estão em inglês enquanto o guia e o contexto são em português; decidir idioma e manter consistência.
- O template contém cercas de Markdown com três crases dentro do HTML. Revisar e remover ao recriar, pois não fazem parte da marcação HTML esperada.
- A entidade usa Lombok `@Data`, que gera `equals`, `hashCode` e `toString` para entidade JPA; considerar getters/setters explícitos ou anotações mais restritas.
- Erros e mensagens de depuração usam `System.out.println`; adotar logging configurável.
- Configuração atual contém usuário/senha de banco previsíveis e o servidor escuta em `0.0.0.0`; usar configuração local por variáveis de ambiente e escolher conscientemente o endereço de exposição.
- A busca por dispositivo em eventos MQTT carrega todos os dispositivos e filtra em memória; criar método de repositório por nome.
- O parsing MQTT de status/estado procura trechos de texto (`contains`). Definir JSON e validar tipo/campos explicitamente.
- O nome do dispositivo é usado no tópico MQTT. Nomes com espaços ou caracteres reservados podem não ser adequados como identificadores de tópico; definir um `deviceKey` estável e separado do nome amigável.
- A aplicação muda `fanState` logo após enviar comando. Para retratar o estado real, guardar separadamente o estado desejado e o estado reportado, ou só atualizar o estado reportado após confirmação.

## 3. Decisões a tomar antes de escrever as funcionalidades

Registre as respostas em `docs/decisoes.md` ou nesta seção ao iniciar a nova implementação.

| Tema | Pergunta que precisa de resposta | Sugestão inicial para o MVP |
|---|---|---|
| Interface | A demonstração terá página web, API, ou as duas? | Manter Thymeleaf simples e API JSON, como no projeto atual. |
| Idioma | Interface, documentação e nomes de campos serão em qual idioma? | Escolher um idioma e manter mensagens e interface consistentes. |
| Identidade do dispositivo | Como um microcontrolador será identificado sem depender do nome editável? | UUID ou chave curta imutável (`deviceKey`) distinta do nome. |
| MQTT | Quais tópicos e JSON exatos serão usados em cada sentido? | Definir uma tabela de tópicos e payloads antes de programar handler/dispositivo. |
| Estado de comando | Quando a tela pode dizer que o ventilador ligou? | Diferenciar solicitação enviada de estado confirmado pelo dispositivo. |
| Operadores | Haverá login no MVP? | Para uso local individual, deixar explícito que é protótipo; para demonstração com usuários, implementar autenticação antes de expor a rede. |
| Histórico | É requisito registrar quem enviou cada comando? | Caso sim, definir entidade e retenção antes da migration inicial. |
| Banco | PostgreSQL é obrigatório no desenvolvimento? | Usar PostgreSQL também nos testes de integração para reduzir diferenças; H2 pode servir apenas para testes rápidos se as diferenças forem consideradas. |
| Remoção | Ao apagar dispositivo, o histórico permanece? | Se houver auditoria, manter histórico e usar remoção lógica ou preservar referência; decidir antes da FK. |
| Docker | O ambiente será executado localmente ou em containers? | Adicionar Compose opcional para PostgreSQL e Mosquitto depois do fluxo básico. |
| Timeouts | De quanto em quanto tempo o dispositivo envia heartbeat e após quanto tempo fica offline? | Configurar ambos por propriedades e cobrir limites com testes usando relógio controlável. |

## 4. Criar o projeto no IntelliJ com Spring Initializr

1. No IntelliJ, escolha **New Project → Spring Boot** (ou **Spring Initializr**, conforme a versão da IDE).
2. Selecione Java, Maven, JDK 21 e empacotamento Jar. Escolha uma versão estável do Spring Boot oferecida pelo Initializr e compatível com o JDK. Evite escolher versões diferentes entre módulos sem motivo.
3. Use `com.example` como Group e `fancontrol` como Artifact/Name, ou escolha um domínio próprio se já tiver um.
4. Defina o pacote base, por exemplo `com.example.fancontrol`. A classe `FancontrolApplication` deve ficar nesse pacote raiz para que o Spring encontre os componentes abaixo dele.
5. Selecione inicialmente as dependências:

   - **Spring Web**: controllers REST e servidor web.
   - **Thymeleaf**: renderização da página HTML no servidor.
   - **Spring Data JPA**: entidades, repositories e persistência.
   - **PostgreSQL Driver**: conexão com PostgreSQL.
   - **Validation**: validação declarativa de campos com Jakarta Validation.
   - **Spring Integration**: canais e integração de mensagens.
   - **Lombok**: opcional; pode ser omitido enquanto aprende as classes Java, reduzindo dependências implícitas.

6. Gere o projeto, abra-o no IntelliJ e aguarde a importação Maven terminar.
7. Primeiro execute a classe principal com o teste de contexto ou rode `./mvnw spring-boot:run` no terminal integrado. Antes de ativar a configuração do PostgreSQL/MQTT, confirme que o projeto vazio sobe. Se as dependências dos serviços forem obrigatórias durante a inicialização, usar perfis de configuração para que testes e primeira execução não dependam de broker externo.
8. Faça um commit inicial antes de adicionar funcionalidades. Assim, cada etapa pode ser comparada e revertida sem perder o ponto de partida.

### Dependência MQTT

Depois de gerar o projeto, adicione a dependência `spring-integration-mqtt` ao `pom.xml`. Ela disponibiliza os componentes MQTT do Spring Integration. O cliente Paho usado pela implementação atual é transitivo em várias combinações de versões; confirme a documentação e a árvore Maven da versão selecionada antes de fixar manualmente `org.eclipse.paho.client.mqttv3`. Só declare a dependência explícita se o código realmente usa essa API diretamente ou se a versão escolhida exigir isso.

Não adicione de início JWT, OpenAPI, Flyway, Docker ou histórico se esses pontos ainda não forem requisito confirmado. Inclua-os quando a etapa correspondente for começar; isso facilita perceber o papel de cada dependência.

## 5. Estrutura MVC sugerida

Em Spring MVC, o controller recebe a requisição e escolhe uma resposta. Para páginas, a resposta pode ser uma view Thymeleaf. Para API REST, a resposta é JSON; DTOs definem esse contrato. `Service` e `Repository` são camadas auxiliares usadas pelo controller, não partes extras do MVC clássico.

```text
src/main/java/com/example/fancontrol/
├── FancontrolApplication.java
├── controller/
│   ├── WebController.java
│   └── DeviceController.java
├── service/
│   └── DeviceService.java
├── repository/
│   └── DeviceRepository.java
├── model/
│   ├── Device.java
│   └── Status.java
├── dto/
│   ├── DeviceRequest.java
│   └── DeviceResponse.java
├── mapper/
│   └── DeviceMapper.java
├── exception/
└── mqtt/

src/main/resources/
├── templates/       # views Thymeleaf
├── static/          # CSS, JavaScript e imagens
└── application.yml  # configuração base sem segredos
```

Responsabilidades e dependências:

1. **Model:** entidades e tipos do domínio (`Device`, `Status`). Não colocar código HTTP nem detalhes de HTML aqui.
2. **Repository:** acesso a dados. `DeviceRepository` pode estender `JpaRepository<Device, Long>` e expor consultas derivadas, por exemplo por `deviceKey`.
3. **Service:** regras do domínio, validação, transações e coordenação entre repository/MQTT. O service não deve conhecer `ResponseEntity` nem detalhes de tela.
4. **Controller REST:** converte HTTP em chamadas de service e retorna DTO/status HTTP. Não implementar regra de negócio complexa no controller.
5. **Controller web:** retorna o template e, se necessário, prepara dados para renderização no servidor. Se a página usar JavaScript e API, a view consome endpoints REST.
6. **DTO/Mapper:** define o que entra e sai da API. Nunca aceitar diretamente campos controlados pelo dispositivo (`status`, `fanState`, datas) no cadastro/renomeação.
7. **MQTT handler/config:** transforma a mensagem recebida em chamada de caso de uso; manter parsing de protocolo fora do controller web/REST.

## 6. Modelo e regras de negócio

### Modelo mínimo recomendado

`Device` pode começar com:

| Campo | Tipo sugerido | Regra |
|---|---|---|
| `id` | `Long` | Chave gerada pelo banco; usar `Long` evita limite desnecessário de IDs. |
| `deviceKey` | `String` ou UUID | Identificador imutável e único usado em MQTT. |
| `name` | `String` | Obrigatório, com tamanho máximo decidido, único se essa for a regra de negócio. |
| `status` | enum | `ONLINE` ou `OFFLINE`; atualizado por eventos e timeout. |
| `reportedFanState` | boolean/enum | Último estado informado pelo dispositivo. |
| `desiredFanState` | boolean/enum opcional | Estado solicitado pelo operador, se for necessário distinguir solicitação e confirmação. |
| `lastSensorActivation` | `Instant` | Instante UTC do último evento de movimento. |
| `lastSeen` | `Instant` | Instante UTC da última comunicação válida. |

O modelo atual não tem `deviceKey` nem `desiredFanState`, usa `LocalDateTime` e atualiza `fanState` ao publicar. Escolher a versão do modelo agora evita migrações improvisadas. Para dados de eventos, prefira instantes UTC (`Instant`) e converta para horário local apenas na apresentação.

### Regras observadas que devem ser preservadas ou explicitamente alteradas

- Criar dispositivo com nome válido; aparar espaços externos; iniciar offline e com ventilador reportado desligado.
- Nome repetido deve ter comportamento definido (o código atual verifica sem diferenciar maiúsculas/minúsculas e devolve conflito).
- Renomear não deve sobrescrever status, ventilador ou horários informados pelo dispositivo.
- Consultar ID inexistente retorna 404.
- Enviar comando para dispositivo offline atualmente impede ligar com conflito; decidir se desligar também deve ser proibido ou aceito como comando pendente.
- Excluir ID inexistente retorna 404.
- Heartbeat marca online e atualiza `lastSeen`.
- Movimento atualiza `lastSensorActivation`, `lastSeen` e marca online.
- Mensagem de status pode colocar offline ou online.
- Relato de estado atualiza estado do ventilador e `lastSeen`.
- Scheduler compara `lastSeen` com timeout configurável e marca offline. A regra para um dispositivo sem nunca ter reportado deve ser explicitada.

## 7. Contrato REST para o MVP

Contrato atual como referência (não exige prefixo `/api` diferente):

| Método e caminho | Uso | Sucesso recomendado | Erros principais |
|---|---|---:|---|
| `GET /api/devices` | Listar dispositivos | 200 | 500 |
| `GET /api/devices/{id}` | Consultar um dispositivo | 200 | 404 |
| `POST /api/devices` | Cadastrar `{ "name": "Sala" }` (e `deviceKey` se definido) | 201 | 400, 409 |
| `PUT /api/devices/{id}` | Atualizar campos editáveis, inicialmente nome | 200 | 400, 404, 409 |
| `DELETE /api/devices/{id}` | Remover | 204 | 404, 409 |
| `POST /api/devices/{id}/fan/on` | Solicitar ligar | 200 ou 202 | 404, 409, 503 |
| `POST /api/devices/{id}/fan/off` | Solicitar desligar | 200 ou 202 | 404, 409, 503 |

Escolha 200 se a resposta representa apenas a atualização registrada na aplicação, ou 202 se o trabalho está pendente e será confirmado depois. A resposta deve indicar claramente estado solicitado e estado reportado se forem diferentes.

Formato recomendado para erros: manter um objeto consistente com `timestamp`, `status`, `error`, `message` e `path`; evitar stack traces e detalhes internos na resposta. Adicionar códigos de erro estáveis se a interface precisar reagir a tipos específicos.

## 8. Protocolo MQTT a definir

Antes de implementar integração com um dispositivo físico, crie `docs/protocolo-mqtt.md` e registre:

- broker, porta, TLS/autenticação e política de credenciais;
- identificador único do dispositivo e regras de caracteres;
- quem publica e quem assina cada tópico;
- QoS, retained, sessão limpa, reconexão e mensagens duplicadas;
- JSON de cada mensagem, versão do protocolo e campos obrigatórios;
- timestamps e unidade/faixa de sensores;
- como distinguir comando enviado, recebido e concluído;
- tratamento de dispositivo desconhecido, payload malformado e versão incompatível.

Uma proposta inicial (confirmar com o firmware; não presumir que o hardware já usa este contrato):

| Direção | Tópico proposto | Payload de exemplo | Efeito esperado |
|---|---|---|---|
| Dispositivo → servidor | `fan/{deviceKey}/heartbeat` | `{"protocolVersion":1,"sentAt":"..."}` | Atualizar presença e `lastSeen`. |
| Dispositivo → servidor | `fan/{deviceKey}/motion` | `{"detected":true,"sentAt":"..."}` | Registrar ativação do sensor e presença. |
| Dispositivo → servidor | `fan/{deviceKey}/status` | `{"status":"ONLINE"}` | Atualizar conectividade somente após validar enum. |
| Dispositivo → servidor | `fan/{deviceKey}/state` | `{"fanOn":true,"sentAt":"..."}` | Atualizar estado reportado. |
| Servidor → dispositivo | `fan/{deviceKey}/command` | `{"commandId":"...","fanOn":true}` | Solicitar estado desejado. |
| Dispositivo → servidor | `fan/{deviceKey}/ack` | `{"commandId":"...","accepted":true,"fanOn":true}` | Confirmar recebimento/execução conforme protocolo. |

Payloads precisam ser desserializados para tipos validados. Não inferir boolean por `payload.contains("true")`, nem status por busca de substring. Mensagens duplicadas devem ser seguras: processar heartbeat repetido não pode criar registros duplicados ou estado inconsistente.

## 9. Configuração local e ambientes

Não versionar senhas reais. Criar configuração base sem segredo e um perfil local/documentado, por exemplo variáveis:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/fancontrol
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
MQTT_BROKER_URL=tcp://localhost:1883
MQTT_CLIENT_ID=fancontrol-server
MQTT_HEARTBEAT_TIMEOUT_SECONDS=30
```

Pontos para a configuração:

- escolher porta HTTP e endereço de bind explicitamente;
- ativar logging apropriado, sem credenciais nem payloads sensíveis;
- permitir teste de serviço sem conexão MQTT real;
- decidir perfil de teste para PostgreSQL/Mosquitto ou uso de containers;
- substituir `spring.jpa.hibernate.ddl-auto=update` por `validate`/`none` quando as migrations Flyway estiverem funcionando;
- manter `show-sql` e logs detalhados desligados no perfil de execução normal.

## 10. Sequência recomendada de reconstrução

### Etapa 0 — Projeto vazio executável

- Gerar via Initializr e confirmar pacote/classe principal.
- Abrir Maven e resolver dependências.
- Subir contexto sem banco ou broker externos.
- Fazer commit.

**Pronto quando:** aplicação inicia e um teste mínimo de contexto passa.

### Etapa 1 — Primeiro MVC sem banco

- Criar `WebController` com rota `/` retornando `index`.
- Criar `templates/index.html` limpo e com idioma escolhido.
- Criar controller REST de saúde temporário ou endpoint simples para provar JSON.
- Separar CSS/JS em `static/` em vez de crescer um template monolítico.

**Pronto quando:** `/` exibe a página e o endpoint REST responde no navegador.

### Etapa 2 — Model e persistência

- Definir modelo `Device` e restrições.
- Configurar PostgreSQL por ambiente local.
- Criar `DeviceRepository` e migration inicial Flyway antes de guardar dados importantes.
- Testar CRUD de repository no banco de teste.

**Pronto quando:** esquema sobe do zero e repository grava/consulta dispositivo.

### Etapa 3 — Casos de uso e API de cadastro

- Criar `DeviceRequest` com validação e `DeviceResponse`.
- Criar service com regras de nome/duplicidade/not-found.
- Criar endpoints list/get/create/rename/delete.
- Criar formato de erro global.

**Pronto quando:** API funciona sem acessar a entidade diretamente e os códigos HTTP estão cobertos.

### Etapa 4 — Página consumindo API

- Criar listagem, cadastro, renomeação e exclusão com formulários acessíveis.
- Exibir estado vazio, carregamento e erros.
- Escapar conteúdo inserido pelo usuário; preferir construir elementos DOM com `textContent` em vez de interpolar dados em `innerHTML`.
- Não usar handlers inline se o JS puder registrar listeners separados.

**Pronto quando:** operações na interface refletem API e erros aparecem de forma compreensível.

### Etapa 5 — Simulador de dispositivo e MQTT

- Primeiro rodar Mosquitto local/container e um script simulador, sem depender do hardware.
- Implementar os tópicos/payloads documentados e parsing validado.
- Implementar publicação de comando e confirmação.
- Implementar heartbeat e timeout; usar `Clock` injetável para testes previsíveis.
- Definir comportamento de broker indisponível e mensagens inválidas.

**Pronto quando:** fluxo simulador → broker → aplicação → banco e aplicação → broker → simulador está demonstrado.

### Etapa 6 — Autenticação, se fizer parte do escopo

- Confirmar se cadastro público de operadores é adequado; evitar expor criação irrestrita de contas.
- Criar entidade de usuário e armazenar senha com hash.
- Definir login e proteção das rotas; decidir sessão/cookie para Thymeleaf ou Bearer JWT para API.
- Proteger a página/API e permitir documentação conforme decisão.
- Cobrir acesso não autenticado, token inválido e permissões.

**Pronto quando:** usuário sem credencial não controla ventiladores e o fluxo de login funciona pela interface usada na demonstração.

### Etapa 7 — Histórico de comandos e documentação

- Se aprovado, criar registro de solicitação com dispositivo, operador, estado desejado, timestamp, resultado e identificador MQTT.
- Não representar “publicado” como “executado”; guardar estados como solicitado, confirmado ou expirado.
- Documentar e testar consulta paginada do histórico.
- Adicionar OpenAPI/Swagger e instruções de demonstração.

### Etapa 8 — Robustez e entrega

- Revisar configurações e segredos.
- Incluir Compose opcional para PostgreSQL e Mosquitto.
- Adicionar logs, health checks e política de retenção de dados.
- Executar a suíte de testes e seguir um roteiro reprodutível do banco vazio à demonstração.

## 11. Plano de testes provisórios

Os testes abaixo são uma proposta inicial para guiar o recomeço. Escreva-os junto de cada etapa, com foco em comportamento observável. Não tente testar detalhes privados do framework ou chamadas reais ao broker em todos os testes.

### Testes unitários do service

1. Cadastrar nome válido aparando espaços e criar estado inicial correto.
2. Rejeitar nome nulo, vazio, longo demais ou com caracteres inválidos conforme regra definida.
3. Rejeitar nomes repetidos sem diferenciar maiúsculas/minúsculas.
4. Renomear preservando estado reportado e horários.
5. Retornar not-found para ID inexistente em consulta, alteração e exclusão.
6. Recusar ligar dispositivo offline (se essa regra for mantida).
7. Criar comando e publicar payload/tópico corretos; confirmar que erro de publicação não é reportado como sucesso.
8. Não afirmar que ventilador ligou antes do ACK se o modelo separar desejado/reportado.
9. Aplicar heartbeat/movimento/status/estado a dispositivo conhecido; ignorar ou registrar adequadamente dispositivo desconhecido.
10. Tratar payload inválido sem alterar entidade.
11. Aplicar timeout somente quando `lastSeen` ultrapassar limite; cobrir exatamente antes, no limite e depois.

### Testes de controller/API com MockMvc

1. `GET /api/devices` retorna lista de DTOs com os campos públicos esperados.
2. `GET` de ID conhecido retorna 200; desconhecido retorna 404.
3. `POST` válido retorna 201 e representação criada.
4. `POST` inválido retorna 400; nome duplicado retorna 409.
5. `PUT` atualiza apenas campos editáveis e preserva campos reportados.
6. `DELETE` retorna 204; inexistente retorna 404.
7. Comandos retornam status e corpo de acordo com o contrato escolhido.
8. JSON malformado e tipos inválidos retornam erro uniforme.
9. Validar que campos inesperados como `status` e `fanState` não são aceitos como entrada.
10. Caso haja autenticação: rotas protegidas retornam 401 sem credencial e passam com credencial válida.

### Testes de persistência e integração

1. Banco vazio recebe migrations até a versão atual sem intervenção manual.
2. Restrições únicas e obrigatórias são aplicadas pelo PostgreSQL.
3. Operações CRUD consultam dados persistidos, não apenas mocks.
4. MQTT handler recebe cada tópico esperado e encaminha os valores parseados corretos ao caso de uso.
5. Tópico malformado, chave desconhecida e JSON inválido não derrubam o consumidor.
6. Teste com broker de teste/container valida publicação, subscrição, QoS e reconexão essencial.
7. Teste de contexto sobe com perfil de teste, sem depender do broker local do desenvolvedor.

### Testes de interface / aceite manual

1. Página abre com lista vazia e mostra mensagem adequada.
2. Cadastrar dispositivo atualiza lista; nome inválido e duplicado exibem erro.
3. Renomear, atualizar estado e excluir refletem a API.
4. Indisponibilidade da API mostra mensagem sem apagar conteúdo da tela.
5. Conteúdo de nome como `<script>` aparece como texto e não executa código.
6. A tela distingue dispositivo offline, comando enviado e estado confirmado.
7. Em autenticação, login/logout e expiração da sessão/token têm comportamento visível e seguro.

### O que considerar temporário

- Pode iniciar com testes unitários usando Mockito e MockMvc standalone enquanto banco/broker não estão prontos.
- Marcar explicitamente testes pendentes de integração; não chamar mocks de prova de comunicação real.
- Usar um simulador MQTT para demonstração antes de conectar hardware.
- Reavaliar o conjunto assim que o contrato de MQTT, autenticação e persistência forem decididos.

## 12. Critérios de conclusão do MVP

Considere o MVP reconstruído quando:

- o projeto pode ser criado/importado no IntelliJ e iniciado seguindo instruções documentadas;
- banco novo é preparado por migrations e não depende de alteração manual;
- cadastro/listagem/consulta/renomeação/exclusão funcionam por API e pela interface prevista;
- regra de online/offline tem comportamento e timeout documentados;
- contrato MQTT é compartilhado com o simulador/firmware e mensagens inválidas não mudam estado;
- comandos distinguem solicitação de confirmação real;
- erros têm respostas coerentes e os testes provisórios essenciais passam;
- qualquer funcionalidade deixada para depois está identificada como fora do MVP, com motivo.

## 13. Como usar este guia para recomeçar

1. Leia as decisões da seção 3 e anote as respostas ainda abertas.
2. Gere um projeto novo no IntelliJ, mantendo o projeto atual como referência histórica.
3. Siga a sequência da seção 10 sem adicionar todas as funcionalidades de uma vez.
4. Ao concluir uma etapa, compare com seu critério de pronto e registre decisões alteradas.
5. Use a seção 11 como checklist de testes da etapa corrente; expanda conforme surgirem regras reais.
6. Atualize este documento se o escopo mudar, principalmente o protocolo MQTT, a segurança e a distinção entre estado desejado e confirmado.

