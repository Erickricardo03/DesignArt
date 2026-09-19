# Pendências deliberadas para a Fase 4 (e antes do deploy)

Itens conhecidos que a Fase 3 (multitenancy) **não** resolve de propósito.

## Identidade e acesso (Fase 4)
- **`username` único global.** Hoje o login é só por `username`, único no sistema todo; isso também revela a existência de um username usado em outro tenant. Deve migrar para login por e-mail (único por tenant ou global, a decidir).
- **Criação do primeiro tenant e do primeiro SUPER_ADMIN.** Não existe caminho seguro em produção: sem seed, o sistema sobe sem nenhum login. `users.tenant_id` já é nullable para o SUPER_ADMIN.
- **Papéis `SUPER_ADMIN` / `TENANT_ADMIN` / `USER`.** Hoje só existem `ADMIN` e `COLABORADOR`.
- **Login por e-mail.**
- **Recuperação de senha e envio de link por e-mail** (remetente institucional: `contato@nexusdevelopment.tech`; SMTP ainda não configurado).
- **Revogação de sessões.** O JWT não tem revogação própria; hoje ele deixa de valer porque o filtro consulta o usuário e o tenant no banco a cada requisição (usuário inativo / tenant não ATIVO = 401).
- **Auditoria administrativa** (quem criou/alterou/desativou usuários e tenants).
- **Gerenciamento de status do tenant** (ATIVO / SUSPENSO / INATIVO) e billing: só o *enforcement* existe (`AccessPolicy`).
- **Landing pública por tenant/domínio** (resolução por hostname / `custom_domain`). Até lá a API exige login e a landing anônima não recebe avaliações/clientes dinâmicos.

## Antes do deploy
- **CORS:** hoje `*` com credenciais; restringir às origens reais.
- **`AuthService.login`:** ainda aceita `senha == hash armazenado` (fallback de comparação direta). Remover.
- Regras de negócio inválidas (`RuntimeException`) ainda respondem 500; padronizar para 4xx.
- `DespesaService`/`TarefaService`: `criadorNome` padrão `"Admin"` em tarefas sem criador.
- Frontend: "Status Setembro" e `dataVencimento` `2026-09-…` fixos na tela de clientes/faturas.
