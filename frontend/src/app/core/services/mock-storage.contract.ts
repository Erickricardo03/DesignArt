import {
  Tarefa,
  Roteiro,
  LogoCliente,
  Evento,
  FotoEvento,
  VendaFoto,
  Despesa,
  DashboardStats,
  RelatorioMensalItem,
  FluxoCaixa,
  Cliente,
  Fatura,
  ServicoCatalogo,
  Colaborador,
  Municipio,
  ConfiguracaoLoja,
  AtividadeHistorico,
  SaudeFinanceira,
  Avaliacao,
} from '../models';

/**
 * Contrato do "banco" local usado apenas em desenvolvimento (ver
 * ApiService.execute()). Existem duas implementações:
 *  - mock-storage.service.ts: dados fictícios completos, usada em DEV.
 *  - mock-storage.service.prod.ts: stub sem nenhum dado, trocada via
 *    fileReplacements em builds de produção (ver angular.json) para que os
 *    dados fictícios não sejam compilados no bundle de produção.
 *
 * Este arquivo é a única fonte da verdade da "forma" do contrato — assim as
 * duas implementações não podem divergir silenciosamente (o TypeScript acusa
 * erro de build se alguma delas ficar incompleta).
 */
export interface IMockStorage {
  getDashboardStats(): DashboardStats;
  getSaudeFinanceira(): SaudeFinanceira;

  getClientes(): Cliente[];
  getClienteById(id: number): Cliente | undefined;
  createCliente(cliente: Partial<Cliente>): Cliente;
  updateCliente(id: number, cliente: Partial<Cliente>): Cliente;
  deleteCliente(id: number): boolean;

  getFaturas(mesReferencia?: string, status?: string, clienteId?: number): Fatura[];
  createFatura(fatura: Partial<Fatura>): Fatura;
  updateFatura(id: number, fatura: Partial<Fatura>): Fatura;
  toggleStatusFatura(id: number): Fatura;
  deleteFatura(id: number): boolean;

  getServicos(): ServicoCatalogo[];
  createServico(servico: Partial<ServicoCatalogo>): ServicoCatalogo;
  updateServico(id: number, servico: Partial<ServicoCatalogo>): ServicoCatalogo;
  deleteServico(id: number): boolean;

  getColaboradores(): Colaborador[];
  createColaborador(colaborador: Partial<Colaborador>): Colaborador;
  updateColaborador(id: number, colaborador: Partial<Colaborador>): Colaborador;
  deleteColaborador(id: number): boolean;

  getAvaliacoes(apenasAtivas?: boolean): Avaliacao[];
  createAvaliacao(avaliacao: Partial<Avaliacao>): Avaliacao;
  updateAvaliacao(id: number, avaliacao: Partial<Avaliacao>): Avaliacao;
  deleteAvaliacao(id: number): boolean;

  getMunicipios(): Municipio[];
  createMunicipio(m: Partial<Municipio>): Municipio;
  deleteMunicipio(id: number): boolean;

  getConfigLoja(): ConfiguracaoLoja;
  updateConfigLoja(config: Partial<ConfiguracaoLoja>): ConfiguracaoLoja;

  getHistorico(): AtividadeHistorico[];

  getTarefas(loja?: string, status?: string, prioridade?: string): Tarefa[];
  getTarefaById(id: number): Tarefa | undefined;
  getTarefaByToken(token: string): Tarefa | undefined;
  createTarefa(tarefa: Partial<Tarefa>): Tarefa;
  updateTarefa(id: number, tarefa: Partial<Tarefa>): Tarefa;
  deleteTarefa(id: number): boolean;
  addObservacaoTarefa(tarefaId: number, texto: string, autorNome?: string): Tarefa;
  addArquivoFinal(
    tarefaId: number,
    arquivo: { nome: string; urlOuBase64: string; tipo?: 'IMAGEM' | 'VIDEO' | 'DOCUMENTO' }
  ): Tarefa;
  removeArquivoFinal(tarefaId: number, arquivoId: number): Tarefa;
  responderAprovacao(
    tarefaId: number,
    statusAprovacao: 'APROVADO' | 'SOLICITOU_AJUSTE',
    feedback?: string
  ): Tarefa;

  getRoteiros(loja?: string, status?: string): Roteiro[];
  getRoteiroById(id: number): Roteiro | undefined;
  createRoteiro(roteiro: Partial<Roteiro>): Roteiro;
  updateRoteiro(id: number, roteiro: Partial<Roteiro>): Roteiro;
  toggleGravacaoRoteiro(id: number): Roteiro;
  deleteRoteiro(id: number): boolean;

  getLogos(cliente?: string): LogoCliente[];
  createLogo(logo: Partial<LogoCliente>): LogoCliente;
  deleteLogo(id: number): boolean;

  getEventos(): Evento[];
  getEventoById(id: number): Evento | undefined;
  createEvento(evento: Partial<Evento>): Evento;
  addFotoEvento(eventoId: number, foto: Partial<FotoEvento>): Evento;
  deleteFotoEvento(fotoId: number): boolean;
  deleteEvento(id: number): boolean;

  getVendas(status?: string): VendaFoto[];
  createVenda(venda: Partial<VendaFoto>): VendaFoto;

  getDespesas(): Despesa[];
  createDespesa(despesa: Partial<Despesa>): Despesa;
  deleteDespesa(id: number): boolean;

  getRelatorioMensal(loja?: string, mes?: any, ano?: any): RelatorioMensalItem[];
  getFluxoCaixa(): FluxoCaixa;
}
