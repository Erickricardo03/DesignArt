# Fase 4: decisões e pendências

Acompanhamento da Fase 4 (identidade, autenticação e administração do SaaS).
Executada de forma incremental; cada etapa é aprovada antes da seguinte.

## Etapa 4.1 — Fundação de identidade e autorização (implementada)
- Roles `SUPER_ADMIN` / `TENANT_ADMIN` / `USER` (enum no Java, CHECK no banco) e permissões `FINANCEIRO` / `EQUIPE` / `CONFIGURACOES` (catálogo fechado).
- Regra estrutural **`SUPER_ADMIN <=> tenant_id IS NULL`**: no banco (CHECK), na entidade e no `AccessPolicy`.
- Login por **e-mail** (normalizado, obrigatório, **globalmente único**). `username` é legado, nullable e não é usado para login (remoção em etapa futura).
- JWT: `sub` = ID do usuário, claim `tv` = `users.token_version`, validade de **8 horas**. Incrementar `token_version` invalida todos os JWT do usuário.
- Política de senha central: mínimo 10 caracteres, máximo **72 bytes UTF-8** (limite do BCrypt), sem NUL, diferente do e-mail.
- Matriz de autorização aplicada no backend + teste de cobertura com **default negado** (`EndpointAuthorizationCoverageTest`).
- Migration `V3__identity_and_roles.sql` **defensiva**: migra dados válidos (ADMIN→TENANT_ADMIN, COLABORADOR→USER) e **aborta** com mensagem clara diante de e-mail nulo/vazio/inválido/duplicado, role desconhecida, usuário comum sem tenant, SUPER_ADMIN com tenant, permissão inválida/duplicada ou status de tenant inválido. Nunca inventa e-mail, role ou tenant e nunca apaga dados.

### Transitório (removido nas etapas seguintes)
- **O TENANT_ADMIN ainda define a senha do usuário** em `/api/usuarios` (validada pela política). Decisão aprovada (D3): usuários passarão a ser **convidados por e-mail** e o administrador **nunca** definirá a senha.

## Etapa 4.2 — Auditoria de segurança (implementada)
- Tabela `audit_events` (V4), **append-only**: triggers no PostgreSQL bloqueiam `UPDATE`, `DELETE` e `TRUNCATE`; sem FKs (o histórico não depende de outras linhas); snapshots de ator/alvo (e-mail, role, tenant).
- `AuditService` é o único ponto de gravação. API tipada (enums + `AuditActor`/`AuditTarget`/`AuditMetadata`): não aceita texto livre, body, headers, senha, hash, token ou JWT. O metadata é montado só por código (chaves fixas, valores enumerados).
- **Transações:** eventos de sucesso participam da MESMA transação da operação (`MANDATORY`); `LOGIN_FAILURE` usa transação independente e nunca vira 500.
- Eventos instrumentados: `LOGIN_SUCCESS`, `LOGIN_FAILURE`, `USER_CREATED`, `USER_UPDATED`, `USER_DELETED`, `USER_ACTIVATED`, `USER_DEACTIVATED`, `USER_ROLE_CHANGED`, `USER_PERMISSIONS_CHANGED`, `SESSIONS_REVOKED`.
- **Reservados para as próximas etapas** (só entram no enum quando a funcionalidade existir; convites/reset entraram na 4.3): `SUPER_ADMIN_BOOTSTRAPPED`, `TENANT_CREATED`, `TENANT_SUSPENDED`, `TENANT_ACTIVATED`, `TENANT_STATUS_CHANGED`.
- `LOGIN_FAILURE` **nunca** grava o texto digitado no campo de e-mail (o usuário pode ter digitado a senha ali); só o motivo (categoria fechada) e o contexto técnico.
- IP do cliente: por padrão SOMENTE o endereço da conexão. `X-Forwarded-For` é ignorado até configurar `app.security.trusted-proxies` (`TRUSTED_PROXIES`, IPs/CIDRs separados por vírgula). **Quando a aplicação ficar atrás do proxy da Nexus:** definir `TRUSTED_PROXIES` com o(s) IP(s) do proxy e garantir que ele SOBRESCREVA `X-Forwarded-For` com o IP real da conexão recebida.
- User-Agent: saneado (controles removidos, JWT/Bearer/Basic/hash redigidos) e limitado a 200 caracteres; texto inerte (qualquer tela futura deve escapá-lo ao exibir, nunca renderizar como HTML).
- Não há endpoint de leitura da auditoria nesta etapa (a consulta administrativa entra com `/api/admin/**`; o TENANT_ADMIN poderá ter uma visão limitada do próprio tenant).

### Endurecimento recomendado para produção (pendente)
- **Papéis de banco separados:** hoje a aplicação usa o mesmo papel para migrar e executar, e o dono da tabela pode remover triggers. Ideal: papel de migração (dono) distinto do papel de execução, este com apenas `INSERT`/`SELECT` em `audit_events`.
- **Volume e retenção:** falhas de login anônimas geram uma linha cada. O rate limiting (etapa de tokens/e-mail) reduz o risco de crescimento abusivo; definir política de retenção/arquivamento (a tabela é append-only, então o arquivamento exige um procedimento administrativo próprio).
- `failureIndependent`: um erro ao gravar `LOGIN_FAILURE` é logado (sem dados sensíveis) e a resposta segue; monitorar esse log.

## Etapa 4.3 — Tokens, convites, recuperação de senha e e-mail (implementada)
- Tabela `user_action_tokens` (V5): guarda **somente o SHA-256** (hex) do token; `purpose` (PASSWORD_RESET | INVITE), expiração, `used_at`/`revoked_at` (mutuamente exclusivos), IP e criador. Sem `tenant_id`: a posse vem de `user_id` (FK `ON DELETE CASCADE`).
- Token: 32 bytes de `SecureRandom`, Base64 URL-safe sem padding (43 caracteres). Nunca é logado, auditado, retornado por API, colocado em exceção ou metadata. TTL: reset **30 min**, convite **72 h** (`Clock` injetável, UTC).
- Consumo **atômico** por `UPDATE ... WHERE used_at IS NULL AND revoked_at IS NULL AND expires_at > :now`; exatamente um vencedor sob concorrência (testado em H2 e PostgreSQL 16). A política de senha é validada ANTES do consumo (senha fraca não queima o link). Resposta única e genérica para inválido/expirado/usado/revogado/finalidade errada.
- `POST /api/auth/forgot-password` (202 sempre, mesma resposta para conta inexistente/inativa/tenant suspenso/pendente/limitada), `POST /api/auth/reset-password`, `POST /api/auth/accept-invite`, `POST /api/usuarios` (convite, só TENANT_ADMIN, sempre no próprio tenant, nunca SUPER_ADMIN, admin nunca define senha), `POST /api/usuarios/{id}/reenviar-convite` (revoga o token anterior).
- Reset concluído: consome token, grava BCrypt, `token_version++` (JWT antigos morrem), revoga demais tokens, zera lockout, audita `PASSWORD_RESET_COMPLETED` + `SESSIONS_REVOKED` na MESMA transação e envia aviso "senha alterada" (sem link).
- **E-mail:** interface `EmailSender` (SMTP / Desabilitado). Config só por ambiente: `MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_FROM, MAIL_FROM_NAME, MAIL_STARTTLS, MAIL_SSL, APP_PUBLIC_URL, NEXUS_MAIL_ENABLED` (padrão `false`). Remetente futuro: `contato@nexusdevelopment.tech`. Links são montados **apenas** a partir de `APP_PUBLIC_URL` (Host/Origin/X-Forwarded-* são ignorados). Habilitado sem configuração completa **falha ao subir**. Com e-mail desabilitado: forgot responde 202 sem gerar token; convite responde 503 sem criar usuário.
- **Consistência transação × e-mail:** transação curta grava usuário/token/auditoria e faz commit; o e-mail sai DEPOIS do commit (assíncrono, fora da transação). Se o envio falha, uma compensação em transação própria **revoga o token** e audita `*_EMAIL_FAILED`. Resíduo aceito: queda do processo entre o commit e o envio deixa um token cujo valor puro nunca saiu da memória (inutilizável; expira em 30 min/72 h; convite pode ser reenviado). `TokenCleanupJob` remove tokens vencidos.
- **Rate limiting** (em memória, janela deslizante, `Clock`, configurável `nexus.ratelimit.*`): login por IP e por IP+identidade; forgot por IP (429) e por identidade (silencioso, 202); reset e accept-invite por IP; criação/reenvio de convite por administrador. **Limitação:** estado por instância — válido para instância única; com várias instâncias usar Redis/gateway. Atrás de proxy, definir `TRUSTED_PROXIES`, senão todos compartilham o IP do proxy.
- **Lockout moderado:** a cada 5 falhas consecutivas de senha em conta existente, bloqueio de 5/10/20/30 min (teto 30); resposta de login permanece genérica; sucesso, reset e aceite zeram o contador. Trade-off: um atacante pode travar temporariamente uma conta por até 30 min (mitigado por limites por IP, teto e desbloqueio via redefinição de senha).
- **Auditoria nova:** `PASSWORD_RESET_REQUESTED/COMPLETED`, `INVITE_CREATED/RESENT/ACCEPTED`, `ACCOUNT_LOCKED`, `PASSWORD_RESET_EMAIL_FAILED`, `INVITE_EMAIL_FAILED` — sem token, senha, hash ou JWT.
- **Frontend:** `/forgot-password`, `/reset-password`, `/accept-invite`; o token é lido da URL e **removido na hora** (`history.replaceState`), fica só em memória, `<meta name="referrer" content="no-referrer">` enquanto a página está aberta; link "Esqueci minha senha" no login; Configurações: sem campo de senha, "Enviar convite", selo "CONVITE PENDENTE" e "Reenviar convite". O backend também envia `Referrer-Policy: no-referrer`.

### Pendências / riscos da 4.3
- SUPER_ADMIN pode usar o fluxo de recuperação por e-mail; **MFA continua obrigatório antes do lançamento público** do painel SUPER_ADMIN.
- Rate limit em memória (instância única). Forgot faz o trabalho de banco na thread da requisição (e o e-mail é assíncrono): pode restar pequena diferença de tempo entre conta existente e inexistente.
- Definir provedor SMTP real, SPF/DKIM/DMARC do domínio remetente e `APP_PUBLIC_URL` de produção antes do deploy.
- Usuário convidado pendente fica `ativo=false` sem senha; se o convite nunca for aceito, o registro permanece (o administrador pode reenviar ou excluir).
- Testes de integração usam slugs de tenant fixos: rodar a suíte em PostgreSQL exige banco novo.

## Próximas etapas da Fase 4 (não implementadas)
1. ~~Auditoria (`audit_events`).~~ (feita na 4.2)
2. ~~Tokens de ação, e-mail/SMTP, esqueci/redefinir senha, convite e revogação, com rate limiting.~~ (feita na 4.3)
3. Bootstrap do primeiro SUPER_ADMIN (`NEXUS_BOOTSTRAP_ENABLED=false` por padrão, `EMAIL` + `PASSWORD_HASH` gerado offline).
4. `/api/admin/**` (tenants, usuários globais, iniciar recuperação de senha, logout forçado, auditoria). SUPER_ADMIN **não** acessa dados de negócio dos tenants.
5. ~~Gestão de usuários do TENANT_ADMIN por convite.~~ (feita na 4.3)
6. Frontend: ~~esqueci/redefinir/aceitar convite~~ (4.3), guards por papel/permissão, interceptor de 401/403, painel SUPER_ADMIN.
7. CORS final (última etapa): remover `@CrossOrigin("*")` e configurar DEV/PROD por ambiente.

## Decisões registradas para o futuro
- **D6 — JWT no `localStorage` (temporário).** Aceito nesta fase. **Migração futura:** cookie `HttpOnly` + `Secure` + `SameSite` (Strict/Lax) com proteção CSRF, idealmente com access token curto + refresh com rotação. Enquanto isso: CSP e demais cabeçalhos no hosting, validade curta (8h) e revogação por `token_version`.
- **D9 — MFA para SUPER_ADMIN é OBRIGATÓRIO antes do lançamento público do painel SUPER_ADMIN.** Fora do escopo da Fase 4.
- **D1 — e-mail global.** Relaxar para "único por tenant" depois é uma mudança segura (troca de índice + resolução do tenant no login). O modelo de longo prazo para a mesma pessoa em vários tenants é separar identidade de associação (`user` × `tenant_membership`).

## Antes do deploy
- **CORS:** hoje `*` com credenciais; restringir às origens reais (última etapa da Fase 4).
- Regras de negócio inválidas legadas (`RuntimeException`) ainda respondem 500; padronizar para 4xx (o código novo já usa exceções de domínio 400/403/409).
- `TarefaService`: `criadorNome` padrão `"Admin"` em tarefas sem criador (deve vir do usuário autenticado).
- Frontend, resíduos fictícios: formulário de tarefas pré-preenchido com nomes reais da equipe (`tarefas.component.ts`), "Igor Santos" como fallback em projetos concluídos, "Status Setembro" e `dataVencimento` `2026-09-…` fixos em clientes/faturas.
- Módulos do frontend sem backend (equipe, serviços, faturas, municípios, histórico, financeiro/*, configuracoes/loja, aprovação): definir o que fica visível.
- Portal público de aprovação e landing pública por domínio/tenant: fase futura (fail-closed até lá).
- Limite de 72 bytes do BCrypt: a política impede senhas maiores, e o login recusa entradas acima disso.
