export interface User {
  id?: number;
  username: string;
  nomeCompleto: string;
  email?: string;
  cargo?: string;
  role: string;
  salario?: number;
  avatarUrl?: string;
}

export interface LoginResponse {
  token: string;
  type: string;
  user: User;
}

export interface ChecklistItem {
  id?: number;
  descricao: string;
  concluido: boolean;
  ordem?: number;
}

export interface ArquivoFinalEntrega {
  id?: number;
  nome: string;
  urlOuBase64: string;
  tamanho?: string;
  tipo?: 'IMAGEM' | 'VIDEO' | 'DOCUMENTO';
  dataUpload?: string;
}

export interface ObservacaoTarefa {
  id?: number;
  autorNome: string;
  dataHora: string;
  texto: string;
}

export interface Tarefa {
  id?: number;
  titulo: string;
  descricao?: string;
  briefing?: string;
  loja: string;
  clienteId?: number;
  municipio?: string;
  status: 'A_FAZER' | 'EM_DESENVOLVIMENTO' | 'EM_REVISAO' | 'NAO_HOMOLOGADA' | 'ATRASADA' | 'CONCLUIDA' | 'CANCELADA';
  prioridade: 'BAIXA' | 'MEDIA' | 'ALTA' | 'URGENTE';
  dataGravacao?: string;
  dataEntrega?: string;
  criadorNome?: string;
  quemAtribuiu?: string;
  responsaveis: string[];
  checklist: ChecklistItem[];
  arquivosFinais?: ArquivoFinalEntrega[];
  observacoes?: ObservacaoTarefa[];
  statusAprovacao?: 'AGUARDANDO_CLIENTE' | 'APROVADO' | 'SOLICITOU_AJUSTE';
  tokenAprovacao?: string;
  percentualConcluido?: number;
  dataCriacao?: string;
  dataConclusao?: string;
}

export interface RoteiroCena {
  ordem: number;
  descricao: string;
}

export interface Roteiro {
  id?: number;
  titulo: string;
  loja: string;
  clienteNome?: string;
  tarefaId?: number;
  tarefaTitulo?: string;
  criadorNome?: string;
  dataGravacao?: string;
  conteudoScript: string;
  cenas?: RoteiroCena[];
  instrucoesCamera?: string;
  observacoesSet?: string;
  status: 'PENDENTE' | 'EM_GRAVACAO' | 'CONCLUIDO';
  feito: boolean;
  dataCriacao?: string;
  dataConclusao?: string;
}

export interface LogoCliente {
  id?: number;
  clienteNome: string;
  variante?: string;
  formato?: string;
  arquivoUrlOuBase64: string;
  tamanho?: string;
  corPrimaria?: string;
  dataUpload?: string;
}

export interface FotoEvento {
  id?: number;
  codigoFoto?: string;
  titulo: string;
  urlOuBase64: string;
  preco: number;
  marcaDaguaTexto?: string;
  visualizacoes?: number;
  vendas?: number;
}

export interface Evento {
  id?: number;
  nome: string;
  slug?: string;
  localizacao: string;
  dataEvento: string;
  horario: string;
  precoFotoVendida: number;
  publicoEstimado: number;
  bannerUrl: string;
  descricao?: string;
  status: string;
  fotos: FotoEvento[];
}

export interface VendaFoto {
  id?: number;
  codigoVenda: string;
  clienteNome: string;
  clienteEmail?: string;
  eventoNome?: string;
  qtdFotos: number;
  qtdVideos: number;
  valorTotal: number;
  status: 'PAGO' | 'PENDENTE' | 'ATRASADO';
  dataDisponivelInfo?: string;
  dataVenda?: string;
}

export interface Despesa {
  id?: number;
  descricao: string;
  categoria: string;
  valor: number;
  dataDespesa: string;
  formaPagamento?: string;
  status: 'PAGO' | 'PENDENTE';
  observacoes?: string;
}

export interface Cliente {
  id?: number;
  nome: string;
  categoria?: string;
  segmento?: string;
  planoContrato?: string;
  telefone?: string;
  email?: string;
  contato?: string;
  diaFaturamento?: number;
  valorMensal?: number;
  municipio?: string;
  logoUrl?: string;
  status: 'ATIVO' | 'INATIVO';
  dataCadastro?: string;
}

export interface Fatura {
  id?: number;
  clienteId: number;
  clienteNome: string;
  mesReferencia: string; // Ex: '09/2026'
  valor: number;
  dataVencimento: string; // Ex: '2026-09-17'
  dataPagamento?: string; // Ex: '2026-09-17'
  status: 'PAGO' | 'PENDENTE' | 'ATRASADO';
  observacoes?: string;
  dataCriacao?: string;
}

export interface ServicoCatalogo {
  id?: number;
  nome: string;
  preco: number;
  categoria?: string;
  descricao: string;
  status: 'ATIVO' | 'INATIVO';
}

export interface Colaborador {
  id?: number;
  nomeCompleto: string;
  username: string;
  email: string;
  cargo: string;
  salario: number;
  role: 'ADMIN' | 'OPERACIONAL' | 'EDITOR' | 'FOTOGRAFO' | 'CLIENTE';
  ativo: boolean;
  avatarUrl?: string;
}

export interface Municipio {
  id?: number;
  nome: string;
  uf: string;
  ativo: boolean;
}

export interface FaixaDesconto {
  id?: number;
  qtdMinima: number;
  percentualDesconto: number;
}

export interface ConfiguracaoLoja {
  diasRetencao: number;
  faixasDesconto: FaixaDesconto[];
}

export interface AtividadeHistorico {
  id?: number;
  dataHora: string;
  colaboradorNome: string;
  colaboradorEmail: string;
  acao: string;
  informacoesAdicionais: string;
}

export interface SaudeFinanceira {
  margemOperacional: number;
  folhaSalarial: number;
  custosFixos: number;
  faturasEmAberto: number;
  totalRecebidoMes: number;
  totalAReceberMes: number;
}

export interface Aviso {
  tarefaId: number;
  titulo: string;
  loja: string;
  tipo: 'VENCIDA' | 'PROXIMA';
  dataEntrega: string;
  prioridade: string;
  status: string;
  mensagem: string;
}

export interface ProducaoMensal {
  mes: string;
  mesAbreviado: string;
  atendimentos: number;
  concluidos: number;
}

export interface ColaboradorRanking {
  nome: string;
  totalTarefasConcluidas: number;
  cargo: string;
  avatarUrl?: string;
}

export interface DashboardStats {
  aFazer: number;
  emDesenvolvimento: number;
  emRevisaoOuNaoHomologada: number;
  atrasadas: number;
  concluidas: number;
  totalTarefas: number;
  ganhosNoMes: number;
  aReceber: number;
  atrasados: number;
  visitasNaPagina: number;
  avisos: Aviso[];
  producaoMensal: ProducaoMensal[];
  rankingColaboradores: ColaboradorRanking[];
  ultimasVendas: VendaFoto[];
  saudeFinanceira?: SaudeFinanceira;
}

export interface RelatorioMensalItem {
  tarefaId: number;
  tituloDemanda: string;
  loja: string;
  criadorNome: string;
  participantes: string[];
  status: string;
  prioridade: string;
  dataEntrega: string;
  percentualConcluido: number;
  mesAno: string;
}

export interface FluxoCaixa {
  totalEntradas: number;
  totalSaidas: number;
  lucroLiquido: number;
  comparativosMensais: {
    mes: string;
    mesAbreviado: string;
    entradas: number;
    saidas: number;
    lucro: number;
  }[];
  ultimasDespesas: Despesa[];
}
