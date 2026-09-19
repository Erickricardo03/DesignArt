-- ============================================================================
-- V3__identity_and_roles.sql   (Fase 4.1 — identidade e autorização)
--
-- Migration INCREMENTAL e DEFENSIVA sobre V1/V2 (imutáveis).
--
--   * login passa a ser por E-MAIL (normalizado, obrigatório, globalmente único);
--   * roles: ADMIN -> TENANT_ADMIN, COLABORADOR -> USER, e SUPER_ADMIN;
--   * regra estrutural: (role = 'SUPER_ADMIN') = (tenant_id IS NULL);
--   * token_version (revogação de sessões), campos de segurança da conta;
--   * catálogo fechado de permissões e de status de tenant.
--
-- PRINCÍPIOS: dados válidos e coerentes são migrados; qualquer situação
-- ambígua ou incompatível ABORTA a migration com mensagem clara. Esta migration
-- NUNCA inventa e-mail, NUNCA inventa role, NUNCA atribui tenant arbitrário e
-- NUNCA apaga dados para "fazer passar". O Flyway executa cada migration em
-- uma transação: se qualquer guarda abortar, NADA é alterado.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1) GUARDAS (somente leitura). Cada uma explica como corrigir manualmente.
-- ----------------------------------------------------------------------------
DO $$
DECLARE
    n BIGINT;
BEGIN
    -- E-mail ausente: o login passa a ser por e-mail; a migration não inventa e-mails.
    SELECT count(*) INTO n FROM users WHERE email IS NULL OR btrim(email) = '';
    IF n > 0 THEN
        RAISE EXCEPTION 'V3 abortada: % usuário(s) sem e-mail (nulo ou vazio). Informe o e-mail real de cada um antes de aplicar a V3 (a migration não inventa e-mails).', n;
    END IF;

    -- E-mail com formato inválido (após normalizar) ou longo demais.
    SELECT count(*) INTO n FROM users
     WHERE length(btrim(email)) > 254
        OR lower(btrim(email)) !~ '^[^@[:space:]]+@[^@[:space:].]+(\.[^@[:space:].]+)+$';
    IF n > 0 THEN
        RAISE EXCEPTION 'V3 abortada: % usuário(s) com e-mail de formato inválido. Corrija-os manualmente antes de aplicar a V3.', n;
    END IF;

    -- E-mail duplicado após normalização (trim + lowercase): a unicidade é global.
    SELECT count(*) INTO n FROM (
        SELECT lower(btrim(email)) FROM users GROUP BY lower(btrim(email)) HAVING count(*) > 1
    ) d;
    IF n > 0 THEN
        RAISE EXCEPTION 'V3 abortada: existem % e-mail(s) duplicados após normalização (trim + lowercase). O e-mail passa a ser globalmente único; resolva as duplicidades manualmente (a migration não escolhe qual conta manter).', n;
    END IF;

    -- Role ausente ou desconhecida.
    SELECT count(*) INTO n FROM users
     WHERE role IS NULL OR role NOT IN ('ADMIN', 'COLABORADOR', 'SUPER_ADMIN', 'TENANT_ADMIN', 'USER');
    IF n > 0 THEN
        RAISE EXCEPTION 'V3 abortada: % usuário(s) com role nula ou desconhecida. Só são migrados ADMIN -> TENANT_ADMIN e COLABORADOR -> USER; a migration não inventa roles.', n;
    END IF;

    -- Tenant inconsistente com o role (regra: SUPER_ADMIN <=> tenant_id NULL).
    SELECT count(*) INTO n FROM users WHERE role = 'SUPER_ADMIN' AND tenant_id IS NOT NULL;
    IF n > 0 THEN
        RAISE EXCEPTION 'V3 abortada: % SUPER_ADMIN com tenant_id preenchido. SUPER_ADMIN não pertence a nenhum tenant.', n;
    END IF;
    SELECT count(*) INTO n FROM users WHERE role <> 'SUPER_ADMIN' AND tenant_id IS NULL;
    IF n > 0 THEN
        RAISE EXCEPTION 'V3 abortada: % usuário(s) comum(ns) (nao SUPER_ADMIN) sem tenant_id. A migration não atribui tenant arbitrário: associe cada um ao tenant correto antes de aplicar a V3.', n;
    END IF;

    -- Permissões: catálogo fechado (FINANCEIRO, EQUIPE, CONFIGURACOES), sem nulos e sem duplicatas.
    SELECT count(*) INTO n FROM user_permissoes
     WHERE permissao IS NULL OR permissao NOT IN ('FINANCEIRO', 'EQUIPE', 'CONFIGURACOES');
    IF n > 0 THEN
        RAISE EXCEPTION 'V3 abortada: % permissão(ões) nula(s) ou fora do catálogo (FINANCEIRO, EQUIPE, CONFIGURACOES). Corrija manualmente; a migration não descarta permissões.', n;
    END IF;
    SELECT count(*) INTO n FROM (
        SELECT 1 FROM user_permissoes GROUP BY user_id, permissao HAVING count(*) > 1
    ) d;
    IF n > 0 THEN
        RAISE EXCEPTION 'V3 abortada: % permissão(ões) duplicada(s) para o mesmo usuário. Remova as duplicatas manualmente; a migration não apaga dados.', n;
    END IF;

    -- Status de tenant: catálogo fechado.
    SELECT count(*) INTO n FROM tenants
     WHERE status IS NULL OR status NOT IN ('ATIVO', 'SUSPENSO', 'INATIVO');
    IF n > 0 THEN
        RAISE EXCEPTION 'V3 abortada: % tenant(s) com status fora de ATIVO/SUSPENSO/INATIVO.', n;
    END IF;
END $$;

-- ----------------------------------------------------------------------------
-- 2) MIGRAÇÃO DE DADOS (apenas transformações sem perda de informação).
-- ----------------------------------------------------------------------------
UPDATE users SET email = lower(btrim(email));
UPDATE users SET role = 'TENANT_ADMIN' WHERE role = 'ADMIN';
UPDATE users SET role = 'USER'         WHERE role = 'COLABORADOR';
-- ativo NULL já era tratado pelo código como INATIVO (Boolean.TRUE.equals(null) = false):
-- preservar esse comportamento (falha fechada), sem ativar ninguém por conta própria.
UPDATE users SET ativo = false WHERE ativo IS NULL;

-- ----------------------------------------------------------------------------
-- 3) ESTRUTURA: users
-- ----------------------------------------------------------------------------
ALTER TABLE users ALTER COLUMN email  SET NOT NULL;
ALTER TABLE users ALTER COLUMN role   SET NOT NULL;
ALTER TABLE users ALTER COLUMN ativo  SET NOT NULL;
ALTER TABLE users ALTER COLUMN ativo  SET DEFAULT true;

-- Preparação para contas convidadas (senha definida no aceite do convite, etapa futura).
-- password nulo NUNCA autentica. username vira legado (não usado para login).
ALTER TABLE users ALTER COLUMN password DROP NOT NULL;
ALTER TABLE users ALTER COLUMN username DROP NOT NULL;

ALTER TABLE users ADD COLUMN token_version     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP(6);
ALTER TABLE users ADD COLUMN failed_logins     INTEGER NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN locked_until      TIMESTAMP(6);
ALTER TABLE users ADD COLUMN last_login_at     TIMESTAMP(6);

-- E-mail globalmente único (armazenado já normalizado, então UNIQUE simples = case-insensitive).
ALTER TABLE users ADD CONSTRAINT users_email_key UNIQUE (email);
ALTER TABLE users ADD CONSTRAINT users_email_normalized_chk
    CHECK (email = lower(btrim(email)) AND email <> '' AND length(email) <= 254);
ALTER TABLE users ADD CONSTRAINT users_email_format_chk
    CHECK (email ~ '^[^@[:space:]]+@[^@[:space:].]+(\.[^@[:space:].]+)+$');

-- Role: catálogo fechado.
ALTER TABLE users ADD CONSTRAINT users_role_chk
    CHECK (role IN ('SUPER_ADMIN', 'TENANT_ADMIN', 'USER'));

-- REGRA ESTRUTURAL: SUPER_ADMIN <=> tenant_id NULL.
--   * usuário comum com tenant_id NULL  -> impossível;
--   * SUPER_ADMIN com tenant            -> impossível.
-- Compatível com os fluxos planejados: TENANT_ADMIN/USER sempre nascem com tenant
-- (criados dentro de um tenant) e o SUPER_ADMIN (bootstrap/administração global)
-- sempre nasce sem tenant.
ALTER TABLE users ADD CONSTRAINT users_role_tenant_chk
    CHECK ((role = 'SUPER_ADMIN') = (tenant_id IS NULL));

ALTER TABLE users ADD CONSTRAINT users_token_version_chk CHECK (token_version >= 0);
ALTER TABLE users ADD CONSTRAINT users_failed_logins_chk CHECK (failed_logins >= 0);

-- ----------------------------------------------------------------------------
-- 4) user_permissoes: catálogo fechado, sem nulos, sem duplicatas.
-- ----------------------------------------------------------------------------
ALTER TABLE user_permissoes ALTER COLUMN permissao SET NOT NULL;
ALTER TABLE user_permissoes ADD CONSTRAINT user_permissoes_user_permissao_key UNIQUE (user_id, permissao);
ALTER TABLE user_permissoes ADD CONSTRAINT user_permissoes_permissao_chk
    CHECK (permissao IN ('FINANCEIRO', 'EQUIPE', 'CONFIGURACOES'));

-- ----------------------------------------------------------------------------
-- 5) tenants: status em catálogo fechado.
-- ----------------------------------------------------------------------------
ALTER TABLE tenants ADD CONSTRAINT tenants_status_chk
    CHECK (status IN ('ATIVO', 'SUSPENSO', 'INATIVO'));
