package com.designart.config;

import com.designart.model.*;
import com.designart.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Inicializador de dados.
 * <p>
 * Dois mecanismos INDEPENDENTES protegem produção de receber dados fictícios:
 * 1) a propriedade {@code app.seed-demo-data} (false por padrão, só "true" no
 *    perfil dev);
 * 2) a checagem direta do perfil ativo via {@link Environment}, que recusa
 *    rodar sob o perfil "prod" mesmo que a propriedade acima seja mal configurada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    // Tenant FICTÍCIO de desenvolvimento local (perfil dev, H2 em memória).
    // Nunca é criado em produção — ver checagens de perfil em run().
    private static final String DEV_TENANT_SLUG = "dev-local";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ClienteRepository clienteRepository;
    private final TarefaRepository tarefaRepository;
    private final RoteiroRepository roteiroRepository;
    private final LogoClienteRepository logoClienteRepository;
    private final EventoRepository eventoRepository;
    private final FotoEventoRepository fotoEventoRepository;
    private final VendaFotoRepository vendaFotoRepository;
    private final DespesaRepository despesaRepository;
    private final AvaliacaoRepository avaliacaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.designart.security.PasswordPolicy passwordPolicy;
    private final Environment environment;

    @Value("${app.seed-demo-data:false}")
    private boolean seedDemoData;

    @Value("${dev.admin.email:}")
    private String devAdminEmail;

    @Value("${dev.admin.password:}")
    private String devAdminPassword;

    @Override
    public void run(String... args) {
        inicializarUsuarioDevOpcional();

        boolean producao = environment.matchesProfiles("prod");
        if (producao || !seedDemoData) {
            log.info("Seed de dados de demonstração DESATIVADO (perfil de produção ou app.seed-demo-data=false). " +
                    "Banco iniciado sem dados fictícios de negócio.");
            return;
        }

        log.warn("Seed de dados de DEMONSTRAÇÃO ativado — use isso apenas em ambiente de desenvolvimento.");
        Long tenantId = obterOuCriarTenantDev().getId();
        inicializarClientes(tenantId);
        inicializarTarefas(tenantId);
        inicializarRoteiros(tenantId);
        inicializarLogos(tenantId);
        inicializarEventosEFotos(tenantId);
        inicializarVendas(tenantId);
        inicializarDespesas(tenantId);
        inicializarAvaliacoes(tenantId);
    }

    /**
     * Cria, opcionalmente, UM usuário administrador de conveniência para
     * desenvolvimento local — nunca em produção, nunca com senha fixa no
     * código. Só age se DEV_ADMIN_EMAIL/DEV_ADMIN_PASSWORD estiverem
     * definidas como variável de ambiente e ainda não existir nenhum usuário.
     * A criação segura do primeiro SUPER_ADMIN real é escopo da Fase 4.
     */
    private void inicializarUsuarioDevOpcional() {
        if (environment.matchesProfiles("prod")) {
            return;
        }
        if (userRepository.count() > 0) {
            return;
        }
        if (devAdminEmail == null || devAdminEmail.isBlank()
                || devAdminPassword == null || devAdminPassword.isBlank()) {
            log.warn("Nenhum usuário cadastrado e as variáveis de ambiente DEV_ADMIN_EMAIL/DEV_ADMIN_PASSWORD " +
                    "não foram definidas. O backend iniciará SEM nenhum login disponível até que você defina essas " +
                    "variáveis (apenas para desenvolvimento) ou implemente o bootstrap seguro do SUPER_ADMIN (Fase 4).");
            return;
        }

        String email = com.designart.security.EmailAddress.normalizeOrNull(devAdminEmail);
        if (email == null) {
            log.warn("DEV_ADMIN_EMAIL não é um e-mail válido; usuário de desenvolvimento NÃO foi criado.");
            return;
        }
        try {
            passwordPolicy.validate(devAdminPassword, email); // mesma política de produção
        } catch (com.designart.exception.InvalidRequestException e) {
            log.warn("DEV_ADMIN_PASSWORD não atende à política de senha ({}); usuário de desenvolvimento NÃO foi criado.",
                    e.getMessage());
            return;
        }

        userRepository.save(User.builder()
                .tenantId(obterOuCriarTenantDev().getId())
                .password(passwordEncoder.encode(devAdminPassword))
                .nomeCompleto("Administrador (Dev)")
                .email(email)
                .cargo("Administrador de Desenvolvimento")
                .role(com.designart.security.Role.TENANT_ADMIN)
                .ativo(true)
                .build());
        log.warn("Usuário TENANT_ADMIN de DESENVOLVIMENTO criado a partir de variáveis de ambiente ({}).", email);
    }

    /** Só chamado fora de produção (checagens de perfil acima). */
    private Tenant obterOuCriarTenantDev() {
        return tenantRepository.findBySlug(DEV_TENANT_SLUG)
                .orElseGet(() -> tenantRepository.save(Tenant.builder()
                        .name("Tenant de Desenvolvimento (fictício)")
                        .slug(DEV_TENANT_SLUG)
                        .build()));
    }

    private void inicializarClientes(Long tenantId) {
        if (clienteRepository.countByTenantId(tenantId) == 0) {
            clienteRepository.save(Cliente.builder().tenantId(tenantId)
                    .nome("JM MODA FITNESS")
                    .segmento("Moda & Fitness")
                    .contato("Carlos Mendes")
                    .telefone("(82) 99881-2233")
                    .email("contato@jmmodafitness.com.br")
                    .build());

            clienteRepository.save(Cliente.builder().tenantId(tenantId)
                    .nome("ATELIÊ DA YSA")
                    .segmento("Moda Feminina & Costura Criativa")
                    .contato("Ysadora Lima")
                    .telefone("(82) 99772-4455")
                    .email("ysa@ateliedaysa.com.br")
                    .build());

            clienteRepository.save(Cliente.builder().tenantId(tenantId)
                    .nome("ÓTICAS PRIME")
                    .segmento("Saúde Visual & Moda")
                    .contato("Roberto Albuquerque")
                    .telefone("(82) 99663-8899")
                    .email("prime@oticasprime.com.br")
                    .build());

            clienteRepository.save(Cliente.builder().tenantId(tenantId)
                    .nome("BARRA RUN")
                    .segmento("Eventos Esportivos & Maratonas")
                    .contato("Organização Barra Run")
                    .telefone("(82) 99554-1122")
                    .email("contato@barrarun.com.br")
                    .build());
        }
    }

    private void inicializarTarefas(Long tenantId) {
        if (tarefaRepository.countByTenantId(tenantId) == 0) {
            // Tarefa 1: Produção de conteúdo de marketing - Ateliê da Ysa (Mock exato da Página 2 e 3 do PDF)
            Tarefa t1 = Tarefa.builder().tenantId(tenantId)
                    .titulo("Produção de conteúdo de marketing")
                    .loja("ATELIÊ DA YSA")
                    .status("EM_DESENVOLVIMENTO")
                    .prioridade("ALTA")
                    .dataGravacao(LocalDate.of(2026, 7, 10))
                    .dataEntrega(LocalDate.of(2026, 7, 17))
                    .criadorNome("Lucas Matheus")
                    .responsaveis(List.of("Edyllaine Silva", "Igor Santos", "Ingrid Ferreira"))
                    .briefing("Produzir todo o material de divulgação de campanha. Atividades a executar: Criar o roteiro dos vídeos (Reels). Gravar as cenas necessárias. Editar e finalizar os Reels. Produzir os Stories (imagem e vídeo) conforme o planejamento. Criar as artes para feed, Stories e demais formatos solicitados. Enviar todo o material para aprovação. Realizar os ajustes solicitados. Entregar os arquivos finais dentro do prazo. Entrega esperada: Reels finalizados. Stories prontos para publicação. Artes em alta qualidade. Arquivos organizados na pasta do cliente.")
                    .checklist(new ArrayList<>())
                    .build();

            List<String> itensT1 = List.of(
                    "Planejar conteúdo da semana",
                    "Criar roteiro dos Reels",
                    "Gravar vídeos",
                    "Editar Reels",
                    "Criar Stories",
                    "Produzir artes para feed e Stories",
                    "Revisar textos e identidade visual",
                    "Live",
                    "Enviar para aprovação",
                    "Realizar ajustes",
                    "Agendar ou entregar material final"
            );

            for (int i = 0; i < itensT1.size(); i++) {
                t1.getChecklist().add(TarefaChecklistItem.builder().tenantId(tenantId)
                        .descricao(itensT1.get(i))
                        .concluido(i < 2) // Primeiros 2 concluídos (Planejar conteúdo e Criar roteiro)
                        .ordem(i + 1)
                        .tarefa(t1)
                        .build());
            }
            tarefaRepository.save(t1);

            // Tarefa 2: JM MODA FITNESS
            Tarefa t2 = Tarefa.builder().tenantId(tenantId)
                    .titulo("Gravação Coleção Fitness Inverno")
                    .loja("JM MODA FITNESS")
                    .status("EM_REVISAO")
                    .prioridade("ALTA")
                    .dataGravacao(LocalDate.of(2026, 6, 5))
                    .dataEntrega(LocalDate.of(2026, 6, 15))
                    .criadorNome("Lucas Matheus")
                    .responsaveis(List.of("Osmar Israel", "Maiara Callind"))
                    .briefing("Gravação dos takes em 4K e edição dinâmica com transições rápidas.")
                    .checklist(new ArrayList<>())
                    .build();

            List<String> itensT2 = List.of("Elaborar Roteiro", "Gravação no Set", "Edição e Color Grading", "Aprovação do Cliente");
            for (int i = 0; i < itensT2.size(); i++) {
                t2.getChecklist().add(TarefaChecklistItem.builder().tenantId(tenantId)
                        .descricao(itensT2.get(i))
                        .concluido(i < 3)
                        .ordem(i + 1)
                        .tarefa(t2)
                        .build());
            }
            tarefaRepository.save(t2);

            // Tarefa 3: Tarefa atrasada para teste de alerta
            Tarefa t3 = Tarefa.builder().tenantId(tenantId)
                    .titulo("Reformulação de Catálogo Digital")
                    .loja("ÓTICAS PRIME")
                    .status("A_FAZER")
                    .prioridade("URGENTE")
                    .dataEntrega(LocalDate.now().minusDays(2))
                    .criadorNome("Lucas Matheus")
                    .responsaveis(List.of("Rebeca Magalhães", "Wythcel Carvalho"))
                    .briefing("Atualização dos modelos de óculos de sol da nova estação.")
                    .checklist(new ArrayList<>())
                    .build();
            tarefaRepository.save(t3);

            // Tarefa 4: Tarefa próxima do vencimento
            Tarefa t4 = Tarefa.builder().tenantId(tenantId)
                    .titulo("Roteirização e Captação de Depoimentos")
                    .loja("JM MODA FITNESS")
                    .status("EM_DESENVOLVIMENTO")
                    .prioridade("ALTA")
                    .dataEntrega(LocalDate.now().plusDays(1))
                    .criadorNome("Lucas Matheus")
                    .responsaveis(List.of("Edyllaine Silva", "Igor Santos"))
                    .briefing("Gravação de depoimentos de alunos do plano VIP.")
                    .checklist(new ArrayList<>())
                    .build();
            tarefaRepository.save(t4);
        }
    }

    private void inicializarRoteiros(Long tenantId) {
        if (roteiroRepository.countByTenantId(tenantId) == 0) {
            roteiroRepository.save(Roteiro.builder().tenantId(tenantId)
                    .titulo("NOVO ESPAÇO FITNESS | JM MODA FITNESS")
                    .loja("JM MODA FITNESS")
                    .criadorNome("LUCAS MATHEUS")
                    .dataGravacao(LocalDate.of(2026, 8, 12))
                    .status("EM_GRAVACAO")
                    .feito(false)
                    .conteudoScript("CENA 1 (00:00 - 00:05): Drone aproximando da fachada nova. Locução marcante: 'Prepare-se para o seu melhor treino'.\n\nCENA 2 (00:05 - 00:15): Planos detalhe dos novos pesos e esteiras de última geração em ritmo acelerado.\n\nCENA 3 (00:15 - 00:25): Atleta executando agachamento livre com iluminação de recorte e névoa suave de fundo.\n\nCENA 4 (00:25 - 00:30): Encerramento com CTA: 'Novo Espaço Fitness. Venha hoje mesmo!' e exibição da Logo.")
                    .observacoesSet("Lente 24-70mm f/2.8, iluminação em 5600K com bastões RGB azuis. Levar microfone de lapela sem fio.")
                    .build());

            roteiroRepository.save(Roteiro.builder().tenantId(tenantId)
                    .titulo("LANÇAMENTO COLEÇÃO PRIMAVERA | ATELIÊ DA YSA")
                    .loja("ATELIÊ DA YSA")
                    .criadorNome("LUCAS MATHEUS")
                    .dataGravacao(LocalDate.of(2026, 8, 20))
                    .status("PENDENTE")
                    .feito(false)
                    .conteudoScript("CENA 1: Modelo entrando no ateliê em slow-motion admirando os vestidos.\n\nCENA 2: Close-up nos tecidos bordados à mão e detalhes da costura autêntica.\n\nCENA 3: Ysa explicando a inspiração botânica da nova coleção.")
                    .observacoesSet("Iluminação suave natural de janela difusa com rebatedor dourado.")
                    .build());
        }
    }

    private void inicializarLogos(Long tenantId) {
        if (logoClienteRepository.countByTenantId(tenantId) == 0) {
            logoClienteRepository.save(LogoCliente.builder().tenantId(tenantId)
                    .clienteNome("JM MODA FITNESS")
                    .variante("Logo Principal Colorida")
                    .formato("PNG")
                    .tamanho("2.4 MB - 4000x2500px")
                    .corPrimaria("#E11D48")
                    .arquivoUrlOuBase64("https://images.unsplash.com/photo-1517838277536-f5f99be501cd?w=400&auto=format&fit=crop&q=60")
                    .build());

            logoClienteRepository.save(LogoCliente.builder().tenantId(tenantId)
                    .clienteNome("ATELIÊ DA YSA")
                    .variante("Versão Negativa Branca")
                    .formato("SVG")
                    .tamanho("450 KB - Vetor")
                    .corPrimaria("#8B5CF6")
                    .arquivoUrlOuBase64("https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=400&auto=format&fit=crop&q=60")
                    .build());

            logoClienteRepository.save(LogoCliente.builder().tenantId(tenantId)
                    .clienteNome("ÓTICAS PRIME")
                    .variante("Símbolo & Marca")
                    .formato("PNG")
                    .tamanho("1.8 MB - 3000x3000px")
                    .corPrimaria("#0EA5E9")
                    .arquivoUrlOuBase64("https://images.unsplash.com/photo-1572635196237-14b3f281503f?w=400&auto=format&fit=crop&q=60")
                    .build());
        }
    }

    private void inicializarEventosEFotos(Long tenantId) {
        if (eventoRepository.countByTenantId(tenantId) == 0) {
            Evento e1 = Evento.builder().tenantId(tenantId)
                    .nome("BARRA RUN 2026")
                    .localizacao("Barra de São Miguel - AL, Brasil")
                    .dataEvento(LocalDate.of(2026, 8, 1))
                    .horario("05:00 às 09:00")
                    .precoFotoVendida(new BigDecimal("10.00"))
                    .publicoEstimado(500)
                    .bannerUrl("https://images.unsplash.com/photo-1452626038306-9aae5e071dd3?w=800&auto=format&fit=crop&q=60")
                    .descricao("Maior corrida de rua da Barra de São Miguel com percursos de 5km e 10km na orla marítima.")
                    .status("PUBLICADO")
                    .fotos(new ArrayList<>())
                    .build();

            List<String> fotosMock = List.of(
                    "https://images.unsplash.com/photo-1552674605-db6ffd4facb5?w=600&auto=format&fit=crop&q=60",
                    "https://images.unsplash.com/photo-1530549387789-4c1017266635?w=600&auto=format&fit=crop&q=60",
                    "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=600&auto=format&fit=crop&q=60",
                    "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=600&auto=format&fit=crop&q=60"
            );

            for (int i = 0; i < fotosMock.size(); i++) {
                e1.getFotos().add(FotoEvento.builder().tenantId(tenantId)
                        .codigoFoto("BR26-" + String.format("%03d", i + 1))
                        .titulo("Corrida 10k - Ponto Km 3 #" + (i + 1))
                        .urlOuBase64(fotosMock.get(i))
                        .preco(new BigDecimal("10.00"))
                        .marcaDaguaTexto("PROIBIDA A CIRCULAÇÃO • DESIGN ARTE")
                        .visualizacoes(120 + (i * 35))
                        .vendas(5 + (i * 2))
                        .evento(e1)
                        .build());
            }
            eventoRepository.save(e1);
        }
    }

    private void inicializarVendas(Long tenantId) {
        if (vendaFotoRepository.countByTenantId(tenantId) == 0) {
            // Mock exato da Página 4 e 5 do PDF
            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257168767")
                    .clienteNome("Adrianny Evelyn")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(2)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("22.50"))
                    .status("PAGO")
                    .dataDisponivelInfo("Disponível em 28/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 19, 42))
                    .build());

            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257157586")
                    .clienteNome("Jadiel Soares")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(1)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("9.00"))
                    .status("PAGO")
                    .dataDisponivelInfo("Disponível em 28/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 19, 19))
                    .build());

            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257146110")
                    .clienteNome("Emanoella Esterfanny")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(1)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("9.00"))
                    .status("PAGO")
                    .dataDisponivelInfo("Disponível em 28/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 15, 0))
                    .build());

            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257145261")
                    .clienteNome("Michellandy Melo dos Santos")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(6)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("54.00"))
                    .status("PAGO")
                    .dataDisponivelInfo("Disponível em 28/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 14, 43))
                    .build());

            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257139503")
                    .clienteNome("Jessyka Marques")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(1)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("9.00"))
                    .status("PAGO")
                    .dataDisponivelInfo("Disponível em 28/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 13, 3))
                    .build());

            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257126789")
                    .clienteNome("Adrielle Santos")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(3)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("27.00"))
                    .status("PAGO")
                    .dataDisponivelInfo("Disponível em 28/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 12, 10))
                    .build());

            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257114145")
                    .clienteNome("Beatriz Ferreira")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(2)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("18.00"))
                    .status("PAGO")
                    .dataDisponivelInfo("Disponível em 28/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 11, 36))
                    .build());

            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257033145")
                    .clienteNome("Thacylla Cavalcante")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(3)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("27.00"))
                    .status("PENDENTE")
                    .dataDisponivelInfo("Disponível em 29/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 11, 33))
                    .build());

            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257023677")
                    .clienteNome("João Pedro Pagrian")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(1)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("13.50"))
                    .status("PENDENTE")
                    .dataDisponivelInfo("Disponível em 29/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 8, 55))
                    .build());

            vendaFotoRepository.save(VendaFoto.builder().tenantId(tenantId)
                    .codigoVenda("#257023604")
                    .clienteNome("Mariana Correia")
                    .eventoNome("BARRA RUN 2026")
                    .qtdFotos(1)
                    .qtdVideos(0)
                    .valorTotal(new BigDecimal("10.00"))
                    .status("ATRASADO")
                    .dataDisponivelInfo("Disponível em 25/07/2026")
                    .dataVenda(LocalDateTime.of(2026, 7, 27, 8, 52))
                    .build());
        }
    }

    private void inicializarDespesas(Long tenantId) {
        if (despesaRepository.countByTenantId(tenantId) == 0) {
            despesaRepository.save(Despesa.builder().tenantId(tenantId)
                    .descricao("Locação de Lentes Cinema 50mm / 85mm")
                    .categoria("Equipamentos")
                    .valor(new BigDecimal("450.00"))
                    .dataDespesa(LocalDate.of(2026, 7, 22))
                    .formaPagamento("PIX")
                    .status("PAGO")
                    .observacoes("Lentes para gravação da JM Fitness")
                    .build());

            despesaRepository.save(Despesa.builder().tenantId(tenantId)
                    .descricao("Combustível e Deslocamento Equipe Barra Run")
                    .categoria("Transporte")
                    .valor(new BigDecimal("280.00"))
                    .dataDespesa(LocalDate.of(2026, 7, 25))
                    .formaPagamento("Cartão de Crédito")
                    .status("PAGO")
                    .build());

            despesaRepository.save(Despesa.builder().tenantId(tenantId)
                    .descricao("Assinatura Mensal Adobe Creative Cloud Team")
                    .categoria("Software/Assinaturas")
                    .valor(new BigDecimal("680.00"))
                    .dataDespesa(LocalDate.of(2026, 7, 10))
                    .formaPagamento("Boleto")
                    .status("PAGO")
                    .build());

            despesaRepository.save(Despesa.builder().tenantId(tenantId)
                    .descricao("Cachê Fotógrafo Convidado - Barra Run")
                    .categoria("Equipe/Cachês")
                    .valor(new BigDecimal("800.00"))
                    .dataDespesa(LocalDate.of(2026, 8, 1))
                    .formaPagamento("PIX")
                    .status("PAGO")
                    .build());
        }
    }

    private void inicializarAvaliacoes(Long tenantId) {
        if (avaliacaoRepository.countByTenantId(tenantId) == 0) {
            avaliacaoRepository.save(Avaliacao.builder().tenantId(tenantId)
                    .clienteNome("Thaysa Wanderley")
                    .cargoEmpresa("CEO, Ateliê da Ysa")
                    .texto("A Designarte organizou nossa comunicação e transformou completamente a forma como nos posicionamos e vendemos online.")
                    .nota(5)
                    .ativo(true)
                    .build());

            avaliacaoRepository.save(Avaliacao.builder().tenantId(tenantId)
                    .clienteNome("Leo e Adrielle")
                    .cargoEmpresa("CEOs, VivaMais")
                    .texto("O time uniu estratégia comercial e estética de forma incrível. Cada entrega superava as nossas expectativas de qualidade.")
                    .nota(5)
                    .ativo(true)
                    .build());

            avaliacaoRepository.save(Avaliacao.builder().tenantId(tenantId)
                    .clienteNome("Alexandro Junior")
                    .cargoEmpresa("CEO, Sr. Junior")
                    .texto("Eles entenderam a alma da nossa marca e criaram uma presença digital elegante, clara, consistente e altamente conversível.")
                    .nota(5)
                    .ativo(true)
                    .build());

            avaliacaoRepository.save(Avaliacao.builder().tenantId(tenantId)
                    .clienteNome("Erico e Ana")
                    .cargoEmpresa("CEO, Drogaria Central")
                    .texto("Eles entenderam a alma da nossa marca e criaram uma presença digital elegante")
                    .nota(5)
                    .ativo(true)
                    .build());
        }
    }
}
