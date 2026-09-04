# Design Arte — Sistema Integrado de Gestão (Agência Criativa & Operacional)

Sistema completo de gestão operacional, roteirização audiovisual, repositório de assets, cobertura de eventos com proteção anti-print e controle financeiro para a agência **Design Arte**.

---

## 🛠️ Tecnologias Utilizadas

### Backend
- **Java 21**
- **Spring Boot 3.3.4**
- **Spring Security + JWT (JSON Web Tokens)**
- **Spring Data JPA + Hibernate**
- **Banco de Dados H2 (In-Memory)**
- **Maven**

### Frontend
- **Angular 19/20 Standalone**
- **Angular Signals & Zoneless Change Detection**
- **Vanilla CSS3 Moderno com Dark/Light Theme Tokens**
- **Design 100% Responsivo (Mobile, Tablet, Desktop)**
- **Bootstrap Icons**

---

## 🔑 Credenciais de Acesso

- **Usuário**: `admin`
- **Senha**: `admin`

---

## 📂 Estrutura do Projeto

```
DesignArt/
├── backend/                  # API REST em Spring Boot
│   ├── src/main/java/com/designart/
│   │   ├── config/           # Configurações de Segurança, JWT e CORS
│   │   ├── controller/       # Endpoints REST (Tarefas, Roteiros, Eventos, Financeiro, etc.)
│   │   ├── dto/              # Objetos de Transferência de Dados
│   │   ├── model/            # Entidades JPA (User, Tarefa, Roteiro, Evento, Venda, Despesa)
│   │   ├── repository/       # Interfaces Spring Data JPA
│   │   └── service/          # Regras de Negócio e Serviços
│   └── pom.xml
├── frontend/                 # Aplicação Angular Standalone
│   ├── src/app/
│   │   ├── core/             # Serviços de API, Autenticação, Tema e Modelos
│   │   ├── pages/            # Telas (Dashboard, Tarefas, Roteiros, Logos, Eventos, Financeiro, Relatórios, Login, Landing)
│   │   └── shared/           # Componentes Compartilhados (Sidebar, Header, Watermark Viewer)
│   └── styles.css            # Sistema de Design Global
├── por loja das tarefas feitas.pdf  # Documento de Especificação Original
└── README.md
```

---

## 🚀 Como Executar Localmente

### 1. Iniciar o Backend
```bash
cd backend
mvn spring-boot:run
```
> O backend estará acessível em: `http://localhost:8080`  
> Console H2 em: `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:designartdb`)

### 2. Iniciar o Frontend
```bash
cd frontend
npm install
npm start
```
> O frontend estará acessível em: `http://localhost:4200`

---

## ✨ Funcionalidades

1. **Dashboard & Indicadores**:
   - Avisos automáticos de prazos (tarefas atrasadas e próximas do vencimento).
   - Contadores de status de demandas.
   - Gráfico de atendimentos/produção mensal.
   - Ranking de Top 10 colaboradores para cálculo de gratificações.
   - Métricas financeiras e feed de últimas vendas.

2. **Gestão de Tarefas & Demandas**:
   - Cadastro, edição completa, exclusão e mudança de status.
   - Checklist interativo com cálculo de `% de conclusão` em tempo real.
   - Filtros por loja, status e prioridade.

3. **Roteiros & Gravações no Set**:
   - Scripts detalhados com cenas, diálogos e especificações de iluminação/lentes.
   - Modo Leitura otimizado para celulares e tablets no set.
   - Botão para alternar status de gravação concluída.

4. **Repositório de Logos dos Clientes**:
   - Upload e catálogo de logos em alta resolução para editores.
   - Pré-visualização com fundo quadriculado para transparências (PNG/SVG).
   - Download direto e cópia de link.

5. **Cobertura de Eventos & Galeria Protegida**:
   - Cadastro de eventos esportivos e corporativos.
   - Proteção de fotos com marca d'água reforçada: `PROIBIDA A CIRCULAÇÃO • DESIGN ARTE`.
   - Bloqueio contra printscreen, atalhos de captura e download não autorizado.

6. **Financeiro & Fluxo de Caixa**:
   - Histórico de vendas de fotos com filtros (*Todos, Pagos, Pendentes, Atrasados*).
   - Lançamento de despesas operacionais categorizadas.
   - Gráfico comparativo de Entradas, Saídas e Lucro mensal.
   - Exportação de dados para CSV/Planilha.

7. **Relatórios Gerenciais Mensais por Loja**:
   - Relatórios consolidados por loja, criador da tarefa, participantes/executores e demandas feitas.
   - Layout oficial pronto para impressão (`window.print()`).

8. **Dark Mode & Light Mode**:
   - Alternância de tema com persistência local em todos os módulos.

9. **100% Responsivo**:
   - Barra de navegação rápida inferior para smartphones.
   - Menu lateral gaveta com aceleração de hardware.
   - Tabelas com rolagem tátil suave.
