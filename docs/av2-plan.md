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
- O MVP será demonstrado pela API/Swagger. Como a página HTML chama diretamente a API, ela também envia o JWT nas chamadas autenticadas.
- Conforme a ampliação aprovada pelo usuário antes da Etapa 4, haverá dois papéis: `ADMIN` e `USER`. Cadastro público cria somente `USER`; um administrador inicial é provisionado por configuração local.
- O histórico continuará restrito às solicitações humanas para ligar/desligar ventiladores e permanece para uma etapa posterior.

## 4. Interpretação dos requisitos da AV2

| Requisito | Aplicação proposta ao projeto |
|---|---|
| JWT com Spring Security | Login de operador; leitura de dispositivos para usuários autenticados e alterações reservadas ao administrador. |
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
| `OperatorUser` (nova) | ID, nome único, senha com hash, papel (`ADMIN` ou `USER`) | Senha nunca é armazenada em texto simples. Cadastro público cria papel `USER`; o primeiro administrador vem da configuração local. |
| `CommandRecord` (nova) | ID, dispositivo, estado solicitado, data/hora, operador | Registra a solicitação enviada pela aplicação. Não deve afirmar que o dispositivo executou o comando até receber confirmação MQTT. |

O histórico serve como auditoria simples da ação humana. Não é necessário criar um sistema genérico de auditoria nem guardar cada mensagem MQTT para satisfazer o escopo mínimo.

### Controllers REST do escopo

O `WebController` atual atende à página HTML e não precisa ser contado como controller REST. O escopo terá quatro controllers REST, separados por funções que já fazem sentido no domínio:

| Controller | Responsabilidade e rotas indicativas |
|---|---|
| `AuthController` | `POST /api/auth/register`, `POST /api/auth/login` e `GET /api/auth/me`. Cadastro, autenticação e consulta do operador autenticado. |
| `DeviceController` | `GET /api/devices`, `GET /api/devices/{id}`, `POST /api/devices`, `PUT /api/devices/{id}`, `DELETE /api/devices/{id}`. Consulta para ambos os papéis; escrita para `ADMIN`. Comandos permanecem neste controller nesta implementação mínima. |
| `UserController` | `GET /api/users` e `DELETE /api/users/{id}`. Lista somente nomes/IDs e remove usuários; exclusivo de `ADMIN`. |
| `CommandHistoryController` (planejado) | Consulta os registros de comandos quando o histórico for implementado. |

O cadastro e o login são públicos; perfil e leitura de dispositivos exigem JWT. Operações de escrita/comando em dispositivos e gerenciamento de usuários exigem papel `ADMIN`. O evento MQTT continua sendo uma comunicação dispositivo-aplicação e não é tratado como login de usuário.

### Regras simples do MVP

- O nome do dispositivo é obrigatório e único.
- O status do dispositivo continua sendo `ONLINE` ou `OFFLINE`.
- O operador faz login com nome e senha; a senha armazenada deve estar protegida por hash.
- O token JWT é necessário para consultar dispositivos e perfil; somente `ADMIN` pode alterar dispositivos, enviar comandos ou gerenciar usuários.
- O cadastro público sempre cria `USER`; a configuração local provisiona o primeiro `ADMIN` sem expor cadastro de administradores na API.
- Não é possível remover o último administrador.
- O registro de comando descreve uma solicitação feita pelo operador, não uma confirmação física de que o ventilador ligou ou desligou.
- A aplicação central continua sendo responsável por emitir comandos; o dispositivo apenas reporta eventos e estado.
- O banco local existente deve ser migrado com cuidado para não perder os dispositivos já cadastrados.

## 6. Itens fora do escopo mínimo

Para reduzir complexidade, não proponho inicialmente:

- produtos ou categorias, salvo se o professor disser que são obrigatórios;
- fluxo de recuperação de senha, refresh token ou provedor externo de identidade;
- revogação individual de token ou integração com provedor externo de identidade;
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

**Entrega:** concluída. O esquema atual de dispositivos é criado por migration em bancos vazios, e bancos existentes podem ser registrados como versão inicial pelo Flyway. O banco local antigo foi recriado vazio a pedido do usuário, depois de um backup local.

#### Alterações realizadas

- Adicionado o starter Flyway do Spring Boot e o módulo PostgreSQL do Flyway.
- Criada `V1__create_devices_table.sql` com a tabela `devices`, os campos e tipos equivalentes à entidade atual, ID identity, nome único e restrição para os estados `ONLINE` e `OFFLINE`.
- Habilitado `baseline-on-migrate` com versão inicial `1`: ao encontrar um esquema já preenchido sem histórico Flyway, a aplicação registra a versão inicial e não tenta recriar `devices`. Em um banco vazio, Flyway executa a migration V1.
- Alterado `spring.jpa.hibernate.ddl-auto` de `update` para `validate`. Hibernate agora confere a compatibilidade do mapeamento sem criar ou alterar tabelas.
- Movidas as configurações de banco e MQTT para variáveis de ambiente, com importação opcional de `.env`. `.env` foi adicionado ao `.gitignore`; `.env.example` documenta as variáveis necessárias sem incluir credenciais locais.
- Em um novo checkout, copiar `.env.example` para `.env` e ajustar `DB_PASSWORD` para a senha local do PostgreSQL. A aplicação carrega esse arquivo local automaticamente.
- A migration inicial cobre somente o modelo `Device` atual. As tabelas de usuário e histórico serão adicionadas em migrations futuras, junto com as respectivas funcionalidades.

#### Verificação da Etapa 3

- Antes da recriação, o banco antigo foi reconhecido e registrado pelo Flyway como baseline `1`; Flyway não executou V1 sobre a tabela existente, e Hibernate validou o esquema.
- O banco antigo foi recriado vazio a pedido do usuário. Antes da exclusão foi salvo um backup local em `/tmp/fancontrol-before-flyway-20261028.dump`.
- Com o banco vazio, a aplicação executou `V1` com sucesso, criou `devices` e `flyway_schema_history`, e Hibernate validou a estrutura.
- Suíte completa: **26 testes passaram, 0 falhas e 0 erros**.
- Verificação HTTP em execução: listagem retornou `200` e `[]`; cadastro retornou `201`; atualização retornou `200`; remoção retornou `204`. O dispositivo temporário foi removido e a tabela ficou vazia.
- Na inicialização seguinte, Flyway validou a migration existente e informou que o banco estava atualizado, sem executá-la novamente.

### Etapa 4 — Implementar usuário e autenticação JWT

- Criar a entidade, repositório, DTOs e serviço de usuário.
- Proteger a senha com hash.
- Implementar cadastro e login.
- Emitir e validar JWT usando Spring Security.
- Deixar públicas apenas a página, o cadastro e o login (a documentação será liberada na Etapa 6).
- Exigir token para leitura de dispositivos e perfil; restringir alterações de dispositivos, comandos e usuários ao papel `ADMIN`.
- Adaptar a página existente para login e envio do JWT, com controles administrativos condicionais e layout responsivo.

**Entrega:** concluída na branch `codex/av2-mvp`.

#### Alterações realizadas

- Criados `OperatorUser`, `Role`, repositório, DTOs e serviços de autenticação/gerenciamento. O modelo usa nome e senha, conforme o fluxo solicitado.
- Senhas são gravadas com BCrypt; a API nunca devolve hash nem senha. O cadastro público atribui `USER` no servidor e não aceita papel enviado pelo cliente.
- Criadas as rotas `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/auth/me`, `GET /api/users` e `DELETE /api/users/{id}`. A listagem administrativa retorna somente ID e nome.
- JWT assinado com HS256, segredo configurado localmente, expiração de uma hora e autenticação sem sessão. O papel atual é carregado do banco em cada requisição; tokens de contas removidas deixam de funcionar imediatamente.
- Regras de acesso: `USER` e `ADMIN` podem consultar todos os dispositivos; apenas `ADMIN` pode criar, editar, remover e comandar dispositivos, além de listar/remover usuários. O último administrador não pode ser removido.
- O primeiro `ADMIN` é criado uma única vez a partir de `ADMIN_INITIAL_NAME` e `ADMIN_INITIAL_PASSWORD` no `.env` local. Os valores não são versionados. O `.env.example` contém somente nomes e exemplos não secretos.
- A página HTML foi substituída por um painel responsivo em português, com login/cadastro, estado de sessão, lista de dispositivos, ações administrativas condicionais, lista de usuários sem credenciais e mensagens de erro. Cadastro público cria usuários apenas de consulta.
- Migration `V2__create_operator_users.sql` adiciona `app_users`, papel com restrição `ADMIN`/`USER` e índice de nome único sem diferenciar maiúsculas/minúsculas. A V1 e os dispositivos existentes foram preservados.
- O formato JSON de erro do filtro de segurança foi implementado para respostas `401` e `403`.

#### Verificação da Etapa 4

- Suíte Maven: **29 testes passaram, 0 falhas e 0 erros**. Inclui API sem token (`401`), cadastro comum, bloqueio de escrita/listagem administrativa para `USER` (`403`), operações administrativas, ausência de credenciais na lista e invalidação de token após remover a conta.
- A migration V2 foi validada no PostgreSQL local; Hibernate iniciou em modo de validação e não recriou o esquema de dispositivos.
- A aplicação iniciou em `http://localhost:8080/` e o navegador exibiu a tela de login/cadastro. A integração MQTT permaneceu ligada durante a inicialização.
- O banco de desenvolvimento manteve os dispositivos existentes. Dados de teste temporários são limpos pelos testes.

#### Limites que continuam para etapas seguintes

- Swagger/OpenAPI permanece para a Etapa 6.
- Histórico persistido de comandos permanece para a etapa dedicada ao histórico; comando atual continua usando o endpoint existente de dispositivos.
- Docker é opcional e permanece para a Etapa 7.

### Etapa 5 — Implementar histórico de comandos

- Avaliar se vale separar os comandos do `DeviceController` em `FanCommandController` sem alterar os tópicos MQTT.
- Registrar cada solicitação de comando em `CommandRecord`.
- Criar `CommandHistoryController` para consulta do histórico.
- Manter controllers REST com responsabilidades claras; a quantidade deve refletir as funções reais do MVP.

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
