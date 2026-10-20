# Proposta de escopo e plano de implementação — AV2

**Projeto:** Fan Control System
**Documento:** escopo da AV2 revisado e aprovado na Etapa 1
**Data:** 20 de outubro de 2026

## 1. Visão do projeto

O Fan Control System é uma aplicação central para cadastrar e monitorar dispositivos microcontrolados que comandam ventiladores. Os dispositivos reportam eventos e estado por MQTT; a aplicação central registra essas informações, disponibiliza uma API e permite que um operador solicite que um ventilador seja ligado ou desligado.

O projeto continua focado em dispositivos e ventiladores. Para a AV2, a proposta é acrescentar autenticação de operadores, histórico simples dos comandos, documentação da API e controle versionado do esquema do banco. A implementação deve permanecer pequena e adequada a uma demonstração acadêmica.

## 2. Estado atual observado no repositório

O código já contém:

- API Spring Boot com `DeviceController`, `DeviceService` e `DeviceRepository`;
- entidade `Device`, com nome, estado online/offline, estado do ventilador e horários de atividade;
- operações para listar, consultar, cadastrar e remover dispositivos;
- operações para solicitar que o ventilador ligue ou desligue;
- integração MQTT para receber heartbeat, movimento, status e estado, e publicar comandos;
- persistência JPA/PostgreSQL;
- página Thymeleaf simples para visualizar dispositivos e enviar comandos.

Há partes da AV1 que ainda precisam ser completadas ou organizadas:

- a API recebe e devolve diretamente a entidade `Device`; não há DTOs nem mapeadores próprios;
- não há endpoint `PUT` ou `PATCH` para atualizar o dispositivo;
- as exceções de negócio não estão convertidas por um handler global em respostas HTTP padronizadas;
- o Hibernate está configurado com `ddl-auto=update`, sem migrations Flyway.

Também não foram encontrados autenticação JWT, Spring Security, Swagger/OpenAPI ou configuração Docker.

## 3. Decisões aprovadas na Etapa 1

- O domínio permanece focado em dispositivos conectados e controle de ventiladores.
- “Produtos/Categorias” são exemplos conhecidos de outros sistemas, não requisitos para este projeto.
- O cadastro simples de operador faz parte do escopo e é aceitável para a demonstração.
- O professor exige mais de um controller; não exige exatamente cinco. Vamos criar controllers conforme as responsabilidades reais do sistema e evitar divisões artificiais.
- O MVP será demonstrado pela API/Swagger. Como a página HTML atual chama diretamente a API, ela também precisará enviar o JWT para continuar funcionando depois que as rotas forem protegidas; a adaptação deve ser simples e limitada ao fluxo de login e chamadas autenticadas.
- O histórico ficará restrito às solicitações humanas para ligar/desligar ventiladores.

## 4. Interpretação dos requisitos da AV2

| Requisito | Aplicação proposta ao projeto |
|---|---|
| JWT com Spring Security | Login de operador; API administrativa protegida por token. |
| Swagger | Página interativa com rotas, parâmetros, modelos e respostas documentados. |
| Flyway | Migrations SQL versionadas para criar e evoluir o esquema do banco. |
| Docker (opcional) | Imagem da aplicação e, se houver tempo, ambiente com PostgreSQL e Mosquitto. |
| Novos endpoints | Autenticação, perfil do operador, atualização de dispositivo e consulta de histórico. |
| Mais de um controller | Controllers REST separados por responsabilidades reais do sistema; não há meta artificial de cinco. |
| Exemplo “Produtos/Categorias” | Não será implementado: são exemplos de outros domínios, não exigências deste projeto. |

## 5. Escopo mínimo do MVP da AV2

### Funcionalidades

1. Um operador pode criar uma conta para a demonstração e fazer login.
2. O login devolve um JWT; as operações de administração e comando exigem esse token.
3. O operador pode listar, consultar, cadastrar, atualizar e remover dispositivos.
4. O operador pode solicitar ligar ou desligar um ventilador.
5. O sistema registra um histórico enxuto das solicitações de comando e permite consultá-lo.
6. O Swagger apresenta as rotas e informa como autenticar usando o JWT.
7. Flyway cria e evolui as tabelas necessárias sem depender de `ddl-auto=update`.

### Entidades propostas

| Entidade | Dados mínimos | Observações |
|---|---|---|
| `Device` (existente) | ID, nome único, status, estado do ventilador, última ativação do sensor, última comunicação | Preservar o modelo atual e acrescentar apenas o que for necessário. |
| `User` (nova) | ID, nome, e-mail único, senha com hash | Senha nunca deve ser armazenada em texto simples. Para o MVP, não são necessários perfis ou permissões complexos. |
| `CommandRecord` (nova) | ID, dispositivo, estado solicitado, data/hora, operador | Registra a solicitação enviada pela aplicação. Não deve afirmar que o dispositivo executou o comando até receber confirmação MQTT. |

O histórico serve como auditoria simples da ação humana. Não é necessário criar um sistema genérico de auditoria nem guardar cada mensagem MQTT para satisfazer o escopo mínimo.

### Controllers REST do escopo

O `WebController` atual atende à página HTML e não precisa ser contado como controller REST. O escopo terá quatro controllers REST, separados por funções que já fazem sentido no domínio:

| Controller | Responsabilidade e rotas indicativas |
|---|---|
| `AuthController` | `POST /api/auth/register`, `POST /api/auth/login` e `GET /api/auth/me`. Cadastro simples, autenticação e consulta do operador autenticado. |
| `DeviceController` | `GET /api/devices`, `GET /api/devices/{id}`, `POST /api/devices`, `PUT /api/devices/{id}`, `DELETE /api/devices/{id}`. |
| `FanCommandController` | `POST /api/devices/{id}/fan/on` e `POST /api/devices/{id}/fan/off`. Solicitar comandos e registrar a solicitação. |
| `CommandHistoryController` | `GET /api/commands` e, se útil, `GET /api/commands/{id}`. Consultar os registros de comandos. |

Essas rotas formam o escopo aprovado para iniciar o desenvolvimento. O cadastro e o login serão públicos; perfil, dispositivos, comandos e histórico exigirão JWT. Não haverá papéis ou permissões diferentes entre operadores nesta versão. O evento MQTT continua sendo uma comunicação dispositivo-aplicação e não deve ser tratado como login de usuário.

### Regras simples do MVP

- O nome do dispositivo é obrigatório e único.
- O status do dispositivo continua sendo `ONLINE` ou `OFFLINE`.
- O usuário faz login com e-mail e senha; a senha armazenada deve estar protegida por hash.
- O token JWT é necessário para as rotas administrativas e para enviar comandos.
- O registro de comando descreve uma solicitação feita pelo operador, não uma confirmação física de que o ventilador ligou ou desligou.
- A aplicação central continua sendo responsável por emitir comandos; o dispositivo apenas reporta eventos e estado.
- O banco local existente deve ser migrado com cuidado para não perder os dispositivos já cadastrados.

## 6. Itens fora do escopo mínimo

Para reduzir complexidade, não proponho inicialmente:

- produtos ou categorias, salvo se o professor disser que são obrigatórios;
- papéis diferentes entre operadores, permissões detalhadas ou fluxo de recuperação de senha;
- refresh token, revogação de token ou integração com provedor externo de identidade;
- filas de comandos, retentativas sofisticadas ou confirmação transacional do MQTT;
- auditoria completa de todas as mensagens de sensores;
- interface web completa com telas de login e administração;
- Docker como requisito obrigatório, pois o professor indicou que é opcional.

## 7. Plano de implementação para desenvolvermos juntos

### Etapa 1 — Revisar e aprovar o escopo

- Confirmar que o domínio permanece focado em ventiladores e dispositivos.
- Confirmar se “Produtos/Categorias” são apenas exemplos ou exigência do professor.
- Confirmar se cadastro simples de operador é aceitável para a demonstração.
- Aprovar as entidades e quatro controllers REST propostos neste documento.

**Entrega:** concluída. O escopo, o cadastro simples, a ausência de Produtos/Categorias e a organização em quatro controllers REST foram aprovados antes de iniciar as próximas etapas.

### Etapa 2 — Completar a organização da API da AV1

- Criar DTOs de entrada e saída para dispositivos.
- Criar mapeadores simples entre DTOs e entidades.
- Adicionar atualização de dispositivo com `PUT`.
- Criar tratamento global de exceções e respostas HTTP coerentes para validação, recurso inexistente e conflito de nome.
- Preservar a integração MQTT e a interface existente durante a reorganização.

**Entrega:** concluída na branch `codex/av2-mvp`.

#### Alterações realizadas

- `DeviceRequest` recebe somente o nome em cadastro e atualização. Isso impede que a API aceite do cliente campos que pertencem ao dispositivo, como estado, fan ligado/desligado e horários recebidos por MQTT.
- `DeviceResponse` e `DeviceMapper` definem e convertem a representação pública do dispositivo. A resposta mantém os campos usados pela página atual: `id`, `name`, `status`, `fanState`, `lastSensorActivation` e `lastSeen`.
- `PUT /api/devices/{id}` atualiza apenas o nome. Os valores de estado e os horários existentes são preservados, pois são informados pelo dispositivo ou pelo sistema.
- Cadastro retorna `201 Created`; remoção retorna `204 No Content`. Os demais casos bem-sucedidos continuam retornando `200 OK`.
- A validação agora retorna `400 Bad Request`; dispositivo inexistente retorna `404 Not Found`; nome duplicado e tentativa incompatível com o estado do dispositivo retornam `409 Conflict`. Falha ao enviar comando ao broker retorna `503 Service Unavailable`.
- `GlobalExceptionHandler` padroniza erros como JSON com horário, status, nome do erro, mensagem e caminho solicitado. Erros inesperados retornam mensagem genérica, sem expor detalhes internos.
- A verificação de nome duplicado passou a usar consultas do repositório, ignorando maiúsculas/minúsculas. O banco continua tendo sua restrição de unicidade como proteção adicional.
- As rotas MQTT continuam publicando os mesmos tópicos e payloads; o formato das respostas continua compatível com as propriedades lidas pela página HTML, portanto a interface não precisou ser alterada nesta etapa.

#### Verificação da Etapa 2

- `DeviceControllerTest`, `DeviceServiceTest` e `FancontrolApplicationTests`: **25 testes passaram, 0 falhas e 0 erros**.
- Os testes cobrem o formato das respostas, `POST`, `PUT`, `DELETE`, erros `400`, `404` e `409`, preservação de estado ao renomear e publicação do comando MQTT usando canal simulado.
- O teste de contexto iniciou a aplicação, conectou ao PostgreSQL local e ativou os componentes MQTT. A suíte foi executada fora do sandbox para permitir acesso ao serviço local e a instrumentação JVM do Mockito.

### Etapa 3 — Introduzir Flyway com preservação dos dados

- Criar a migration inicial para o esquema existente e as tabelas novas aprovadas.
- Definir o procedimento para bancos novos e para o banco local já usado.
- Desativar a atualização automática do esquema pelo Hibernate somente quando a migration estiver pronta.
- Manter as credenciais e configurações locais fora dos arquivos versionados.

**Entrega:** esquema reproduzível e versionado, sem descartar dispositivos existentes.

### Etapa 4 — Implementar usuário e autenticação JWT

- Criar a entidade, repositório, DTOs e serviço de usuário.
- Proteger a senha com hash.
- Implementar cadastro e login.
- Emitir e validar JWT usando Spring Security.
- Deixar públicas apenas as rotas necessárias, como cadastro/login e documentação.
- Exigir token para operações de dispositivos, comandos e histórico.
- Adaptar a página HTML existente para fazer login e enviar o JWT nas chamadas, mantendo o painel funcional sem criar uma interface administrativa nova.

**Entrega:** fluxo demonstrável de cadastro/login e rotas protegidas.

### Etapa 5 — Implementar controllers e histórico

- Completar `DeviceController` com CRUD de dispositivos.
- Criar `UserController` para consultar o perfil autenticado.
- Separar comandos de ventilador em `FanCommandController`.
- Registrar cada solicitação de comando em `CommandRecord`.
- Criar `CommandHistoryController` para consulta do histórico.
- Manter os quatro controllers REST com responsabilidades claras, conforme o escopo aprovado.

**Entrega:** funcionalidades mínimas do MVP disponíveis por API.

### Etapa 6 — Documentar a API no Swagger

- Configurar título, versão e descrição da API.
- Documentar rotas, parâmetros, corpos de requisição, respostas e erros esperados.
- Configurar o esquema Bearer JWT para facilitar chamadas autenticadas pela interface Swagger.
- Liberar acesso à documentação sem exigir token, se essa for a decisão de segurança do MVP.

**Entrega:** documentação interativa adequada para demonstração ao professor.

### Etapa 7 — Docker opcional

- Criar imagem da aplicação.
- Se houver tempo, criar Docker Compose para aplicação, PostgreSQL e Mosquitto.
- Configurar endereços e credenciais por variáveis de ambiente.

**Entrega:** execução reproduzível em containers, caso o tempo permita.

### Etapa 8 — Preparar a demonstração

- Descrever a sequência curta: criar conta, fazer login, cadastrar dispositivo, consultar dispositivo, enviar comando e consultar histórico.
- Conferir que Swagger mostra as respostas reais das rotas.
- Demonstrar que as migrations inicializam o banco da apresentação.
- Confirmar com o professor ou na rubrica se a contagem de controllers e o escopo são aceitos.

**Entrega:** roteiro objetivo para apresentar os requisitos da AV2.

## 8. Decisões já tomadas e observação de implementação

As decisões de escopo acima foram confirmadas. O histórico de comandos será parte do MVP; a adaptação da página existente será necessária para que ela continue usando a API depois da ativação da autenticação.

**Esta etapa alterou somente este documento de planejamento.** A implementação funcional começa na Etapa 2, depois desta aprovação do escopo.
