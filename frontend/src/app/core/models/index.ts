export interface User {
  id?: number;
  username: string;
  nomeCompleto: string;
  email?: string;
  cargo?: string;
  role: string;
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

export interface Tarefa {
  id?: number;
  titulo: string;
  descricao?: string;
  briefing?: string;
  loja: string;
  clienteId?: number;
  status: 'A_FAZER' | 'EM_DESENVOLVIMENTO' | 'EM_REVISAO' | 'NAO_HOMOLOGADA' | 'ATRASADA' | 'CONCLUIDA';
  prioridade: 'BAIXA' | 'MEDIA' | 'ALTA' | 'URGENTE';
  dataGravacao?: string;
  dataEntrega?: string;
  criadorNome?: string;
  responsaveis: string[];
  checklist: ChecklistItem[];
  percentualConcluido?: number;
  dataCriacao?: string;
  dataConclusao?: string;
}

export interface Roteiro {
  id?: number;
  titulo: string;
  loja: string;
  criadorNome?: string;
  dataGravacao?: string;
  conteudoScript: string;
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
