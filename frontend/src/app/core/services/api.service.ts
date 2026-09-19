import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, of, timeout, catchError, tap, throwError } from 'rxjs';
import { getApiBaseUrl } from './api-config';
import { MockStorageService } from './mock-storage.service';
import { IMockStorage } from './mock-storage.contract';
import { environment } from '../../../environments/environment';
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
  User,
  UsuarioRequest,
} from '../models';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private http = inject(HttpClient);
  // Tipado pela interface (não pela classe concreta) de propósito: assim o
  // ApiService só enxerga o contrato comum entre a implementação de DEV
  // (dados fictícios completos) e o stub de PROD (sem dados nenhum), que são
  // trocadas via fileReplacements no build — ver mock-storage.contract.ts.
  private mockDb: IMockStorage = inject(MockStorageService);

  private isBackendOnline = false;
  private hasCheckedOnlineStatus = false;
  private isChecking = false;

  private readonly FAST_TIMEOUT_MS = 800;
  private readonly PROD_TIMEOUT_MS = 15000;

  /**
   * true quando a última chamada à API falhou em produção. A UI (ver
   * SystemStatusBannerComponent) usa isso para avisar o usuário e oferecer
   * "tentar novamente" — nunca para exibir dados inventados no lugar.
   */
  connectionError = signal<boolean>(false);

  constructor() {
    this.checkHealthQuietly();
  }

  private get baseUrl(): string {
    return getApiBaseUrl();
  }

  private getHeaders(): HttpHeaders {
    const token = typeof window !== 'undefined' ? localStorage.getItem('designart_token') : null;
    return new HttpHeaders({
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    });
  }

  /**
   * Verifica em segundo plano se o backend está vivo sem travar a interface
   */
  public checkHealthQuietly(): void {
    if (this.isChecking) return;
    this.isChecking = true;

    this.http
      .get(`${this.baseUrl}/health`, { headers: this.getHeaders(), responseType: 'text' })
      .pipe(
        timeout(1000),
        catchError(() => of(null))
      )
      .subscribe({
        next: (res) => {
          this.isBackendOnline = !!res;
          this.hasCheckedOnlineStatus = true;
          this.isChecking = false;
        },
        error: () => {
          this.isBackendOnline = false;
          this.hasCheckedOnlineStatus = true;
          this.isChecking = false;
        },
      });
  }

  /**
   * Wrapper universal para todas as chamadas à API.
   *
   * Em DESENVOLVIMENTO: se o backend estiver offline ou não responder rápido,
   * cai instantaneamente para os dados locais de `MockStorageService` — só
   * para não travar o trabalho de quem está codando sem o backend rodando.
   *
   * Em PRODUÇÃO: `fallbackFn` nunca é chamada. Um erro de rede vira um erro
   * real propagado ao chamador (para a tela mostrar um estado de erro de
   * verdade, com opção de tentar de novo) — jamais dados fictícios.
   */
  private execute<T>(httpCall: Observable<T>, fallbackFn: () => T): Observable<T> {
    if (!environment.production) {
      if (this.hasCheckedOnlineStatus && !this.isBackendOnline) {
        // 🚀 RESPOSTA INSTANTÂNEA EM 0ms! (somente em desenvolvimento)
        return of(fallbackFn());
      }

      return httpCall.pipe(
        timeout(this.FAST_TIMEOUT_MS),
        tap(() => {
          this.isBackendOnline = true;
          this.hasCheckedOnlineStatus = true;
        }),
        catchError(() => {
          this.isBackendOnline = false;
          this.hasCheckedOnlineStatus = true;
          return of(fallbackFn());
        })
      );
    }

    return httpCall.pipe(
      timeout(this.PROD_TIMEOUT_MS),
      tap(() => {
        this.isBackendOnline = true;
        this.hasCheckedOnlineStatus = true;
        this.connectionError.set(false);
      }),
      catchError((err) => {
        this.isBackendOnline = false;
        this.hasCheckedOnlineStatus = true;
        this.connectionError.set(true);
        return throwError(() => err);
      })
    );
  }

  // === DASHBOARD ===
  getDashboardStats(): Observable<DashboardStats> {
    return this.execute(
      this.http.get<DashboardStats>(`${this.baseUrl}/dashboard/stats`, { headers: this.getHeaders() }),
      () => this.mockDb.getDashboardStats()
    );
  }

  getSaudeFinanceira(): Observable<SaudeFinanceira> {
    return this.execute(
      this.http.get<SaudeFinanceira>(`${this.baseUrl}/financeiro/saude`, { headers: this.getHeaders() }),
      () => this.mockDb.getSaudeFinanceira()
    );
  }

  // === CLIENTES & MARCAS ===
  getClientes(): Observable<Cliente[]> {
    return this.execute(
      this.http.get<Cliente[]>(`${this.baseUrl}/clientes`, { headers: this.getHeaders() }),
      () => this.mockDb.getClientes()
    );
  }

  getClienteById(id: number): Observable<Cliente> {
    return this.execute(
      this.http.get<Cliente>(`${this.baseUrl}/clientes/${id}`, { headers: this.getHeaders() }),
      () => {
        const c = this.mockDb.getClienteById(id);
        if (c) return c;
        throw new Error('Cliente não encontrado');
      }
    );
  }

  createCliente(cliente: Partial<Cliente>): Observable<Cliente> {
    const local = this.mockDb.createCliente(cliente);
    return this.execute(
      this.http.post<Cliente>(`${this.baseUrl}/clientes`, cliente, { headers: this.getHeaders() }),
      () => local
    );
  }

  updateCliente(id: number, cliente: Partial<Cliente>): Observable<Cliente> {
    const local = this.mockDb.updateCliente(id, cliente);
    return this.execute(
      this.http.put<Cliente>(`${this.baseUrl}/clientes/${id}`, cliente, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteCliente(id: number): Observable<boolean> {
    const local = this.mockDb.deleteCliente(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/clientes/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === FATURAS MENSALIDADE ===
  getFaturas(mesReferencia?: string, status?: string, clienteId?: number): Observable<Fatura[]> {
    let params = new HttpParams();
    if (mesReferencia) params = params.set('mesReferencia', mesReferencia);
    if (status) params = params.set('status', status);
    if (clienteId) params = params.set('clienteId', clienteId);

    return this.execute(
      this.http.get<Fatura[]>(`${this.baseUrl}/faturas`, { headers: this.getHeaders(), params }),
      () => this.mockDb.getFaturas(mesReferencia, status, clienteId)
    );
  }

  createFatura(fatura: Partial<Fatura>): Observable<Fatura> {
    const local = this.mockDb.createFatura(fatura);
    return this.execute(
      this.http.post<Fatura>(`${this.baseUrl}/faturas`, fatura, { headers: this.getHeaders() }),
      () => local
    );
  }

  updateFatura(id: number, fatura: Partial<Fatura>): Observable<Fatura> {
    const local = this.mockDb.updateFatura(id, fatura);
    return this.execute(
      this.http.put<Fatura>(`${this.baseUrl}/faturas/${id}`, fatura, { headers: this.getHeaders() }),
      () => local
    );
  }

  toggleStatusFatura(id: number): Observable<Fatura> {
    const local = this.mockDb.toggleStatusFatura(id);
    return this.execute(
      this.http.patch<Fatura>(`${this.baseUrl}/faturas/${id}/toggle`, {}, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteFatura(id: number): Observable<boolean> {
    const local = this.mockDb.deleteFatura(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/faturas/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === SERVIÇOS DO CATÁLOGO ===
  getServicos(): Observable<ServicoCatalogo[]> {
    return this.execute(
      this.http.get<ServicoCatalogo[]>(`${this.baseUrl}/servicos`, { headers: this.getHeaders() }),
      () => this.mockDb.getServicos()
    );
  }

  createServico(servico: Partial<ServicoCatalogo>): Observable<ServicoCatalogo> {
    const local = this.mockDb.createServico(servico);
    return this.execute(
      this.http.post<ServicoCatalogo>(`${this.baseUrl}/servicos`, servico, { headers: this.getHeaders() }),
      () => local
    );
  }

  updateServico(id: number, servico: Partial<ServicoCatalogo>): Observable<ServicoCatalogo> {
    const local = this.mockDb.updateServico(id, servico);
    return this.execute(
      this.http.put<ServicoCatalogo>(`${this.baseUrl}/servicos/${id}`, servico, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteServico(id: number): Observable<boolean> {
    const local = this.mockDb.deleteServico(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/servicos/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === EQUIPE & COLABORADORES ===
  getColaboradores(): Observable<Colaborador[]> {
    return this.execute(
      this.http.get<Colaborador[]>(`${this.baseUrl}/equipe`, { headers: this.getHeaders() }),
      () => this.mockDb.getColaboradores()
    );
  }

  createColaborador(colaborador: Partial<Colaborador>): Observable<Colaborador> {
    const local = this.mockDb.createColaborador(colaborador);
    return this.execute(
      this.http.post<Colaborador>(`${this.baseUrl}/equipe`, colaborador, { headers: this.getHeaders() }),
      () => local
    );
  }

  updateColaborador(id: number, colaborador: Partial<Colaborador>): Observable<Colaborador> {
    const local = this.mockDb.updateColaborador(id, colaborador);
    return this.execute(
      this.http.put<Colaborador>(`${this.baseUrl}/equipe/${id}`, colaborador, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteColaborador(id: number): Observable<boolean> {
    const local = this.mockDb.deleteColaborador(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/equipe/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === AVALIAÇÕES DE CLIENTES (Depoimentos) ===
  getAvaliacoes(apenasAtivas = false): Observable<Avaliacao[]> {
    let params = new HttpParams();
    if (apenasAtivas) params = params.set('apenasAtivas', 'true');

    return this.execute(
      this.http.get<Avaliacao[]>(`${this.baseUrl}/avaliacoes`, { headers: this.getHeaders(), params }),
      () => this.mockDb.getAvaliacoes(apenasAtivas)
    );
  }

  createAvaliacao(avaliacao: Partial<Avaliacao>): Observable<Avaliacao> {
    const local = this.mockDb.createAvaliacao(avaliacao);
    return this.execute(
      this.http.post<Avaliacao>(`${this.baseUrl}/avaliacoes`, avaliacao, { headers: this.getHeaders() }),
      () => local
    );
  }

  updateAvaliacao(id: number, avaliacao: Partial<Avaliacao>): Observable<Avaliacao> {
    const local = this.mockDb.updateAvaliacao(id, avaliacao);
    return this.execute(
      this.http.put<Avaliacao>(`${this.baseUrl}/avaliacoes/${id}`, avaliacao, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteAvaliacao(id: number): Observable<boolean> {
    const local = this.mockDb.deleteAvaliacao(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/avaliacoes/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === USUÁRIOS & PERMISSÕES (gestão de acesso da equipe, apenas ADMIN) ===
  // Sem fallback local: um usuário "criado" só offline não conseguiria logar de verdade,
  // então aqui é melhor expor o erro de conexão do que fingir sucesso.
  getUsuarios(): Observable<User[]> {
    return this.http.get<User[]>(`${this.baseUrl}/usuarios`, { headers: this.getHeaders() });
  }

  createUsuario(usuario: UsuarioRequest): Observable<User> {
    return this.http.post<User>(`${this.baseUrl}/usuarios`, usuario, { headers: this.getHeaders() });
  }

  reenviarConvite(id: number): Observable<User> {
    return this.http.post<User>(`${this.baseUrl}/usuarios/${id}/reenviar-convite`, {}, { headers: this.getHeaders() });
  }

  updateUsuario(id: number, usuario: Partial<UsuarioRequest>): Observable<User> {
    return this.http.put<User>(`${this.baseUrl}/usuarios/${id}`, usuario, { headers: this.getHeaders() });
  }

  deleteUsuario(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/usuarios/${id}`, { headers: this.getHeaders() });
  }

  // === MUNICÍPIOS ===
  getMunicipios(): Observable<Municipio[]> {
    return this.execute(
      this.http.get<Municipio[]>(`${this.baseUrl}/municipios`, { headers: this.getHeaders() }),
      () => this.mockDb.getMunicipios()
    );
  }

  createMunicipio(m: Partial<Municipio>): Observable<Municipio> {
    const local = this.mockDb.createMunicipio(m);
    return this.execute(
      this.http.post<Municipio>(`${this.baseUrl}/municipios`, m, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteMunicipio(id: number): Observable<boolean> {
    const local = this.mockDb.deleteMunicipio(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/municipios/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === CONFIGURAÇÃO DA LOJA ===
  getConfigLoja(): Observable<ConfiguracaoLoja> {
    return this.execute(
      this.http.get<ConfiguracaoLoja>(`${this.baseUrl}/configuracoes/loja`, { headers: this.getHeaders() }),
      () => this.mockDb.getConfigLoja()
    );
  }

  updateConfigLoja(config: Partial<ConfiguracaoLoja>): Observable<ConfiguracaoLoja> {
    const local = this.mockDb.updateConfigLoja(config);
    return this.execute(
      this.http.put<ConfiguracaoLoja>(`${this.baseUrl}/configuracoes/loja`, config, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === HISTÓRICO DE ATIVIDADES ===
  getHistorico(): Observable<AtividadeHistorico[]> {
    return this.execute(
      this.http.get<AtividadeHistorico[]>(`${this.baseUrl}/historico`, { headers: this.getHeaders() }),
      () => this.mockDb.getHistorico()
    );
  }

  // === TAREFAS ===
  getTarefas(loja?: string, status?: string, prioridade?: string): Observable<Tarefa[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);
    if (status) params = params.set('status', status);
    if (prioridade) params = params.set('prioridade', prioridade);

    return this.execute(
      this.http.get<Tarefa[]>(`${this.baseUrl}/tarefas`, { headers: this.getHeaders(), params }),
      () => this.mockDb.getTarefas(loja, status, prioridade)
    );
  }

  getTarefaById(id: number): Observable<Tarefa> {
    return this.execute(
      this.http.get<Tarefa>(`${this.baseUrl}/tarefas/${id}`, { headers: this.getHeaders() }),
      () => {
        const t = this.mockDb.getTarefaById(id);
        if (t) return t;
        throw new Error('Tarefa não encontrada');
      }
    );
  }

  getTarefaByToken(token: string): Observable<Tarefa> {
    return this.execute(
      this.http.get<Tarefa>(`${this.baseUrl}/tarefas/token/${token}`, { headers: this.getHeaders() }),
      () => {
        const t = this.mockDb.getTarefaByToken(token);
        if (t) return t;
        throw new Error('Tarefa não encontrada');
      }
    );
  }

  createTarefa(tarefa: Partial<Tarefa>): Observable<Tarefa> {
    const local = this.mockDb.createTarefa(tarefa);
    return this.execute(
      this.http.post<Tarefa>(`${this.baseUrl}/tarefas`, tarefa, { headers: this.getHeaders() }),
      () => local
    );
  }

  updateTarefa(id: number, tarefa: Partial<Tarefa>): Observable<Tarefa> {
    const local = this.mockDb.updateTarefa(id, tarefa);
    return this.execute(
      this.http.put<Tarefa>(`${this.baseUrl}/tarefas/${id}`, tarefa, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteTarefa(id: number): Observable<boolean> {
    const local = this.mockDb.deleteTarefa(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/tarefas/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  addObservacaoTarefa(tarefaId: number, texto: string, autorNome?: string): Observable<Tarefa> {
    const local = this.mockDb.addObservacaoTarefa(tarefaId, texto, autorNome);
    return this.execute(
      this.http.post<Tarefa>(`${this.baseUrl}/tarefas/${tarefaId}/observacoes`, { texto, autorNome }, { headers: this.getHeaders() }),
      () => local
    );
  }

  addArquivoFinal(tarefaId: number, arquivo: { nome: string; urlOuBase64: string; tipo?: 'IMAGEM' | 'VIDEO' | 'DOCUMENTO' }): Observable<Tarefa> {
    const local = this.mockDb.addArquivoFinal(tarefaId, arquivo);
    return this.execute(
      this.http.post<Tarefa>(`${this.baseUrl}/tarefas/${tarefaId}/arquivos`, arquivo, { headers: this.getHeaders() }),
      () => local
    );
  }

  removeArquivoFinal(tarefaId: number, arquivoId: number): Observable<Tarefa> {
    const local = this.mockDb.removeArquivoFinal(tarefaId, arquivoId);
    return this.execute(
      this.http.delete<Tarefa>(`${this.baseUrl}/tarefas/${tarefaId}/arquivos/${arquivoId}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  responderAprovacao(tarefaId: number, statusAprovacao: 'APROVADO' | 'SOLICITOU_AJUSTE', feedback?: string): Observable<Tarefa> {
    const local = this.mockDb.responderAprovacao(tarefaId, statusAprovacao, feedback);
    return this.execute(
      this.http.post<Tarefa>(`${this.baseUrl}/tarefas/${tarefaId}/aprovacao`, { statusAprovacao, feedback }, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === ROTEIROS ===
  getRoteiros(loja?: string, status?: string): Observable<Roteiro[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);
    if (status) params = params.set('status', status);

    return this.execute(
      this.http.get<Roteiro[]>(`${this.baseUrl}/roteiros`, { headers: this.getHeaders(), params }),
      () => this.mockDb.getRoteiros(loja, status)
    );
  }

  getRoteiroById(id: number): Observable<Roteiro> {
    return this.execute(
      this.http.get<Roteiro>(`${this.baseUrl}/roteiros/${id}`, { headers: this.getHeaders() }),
      () => {
        const r = this.mockDb.getRoteiroById(id);
        if (r) return r;
        throw new Error('Roteiro não encontrado');
      }
    );
  }

  createRoteiro(roteiro: Partial<Roteiro>): Observable<Roteiro> {
    const local = this.mockDb.createRoteiro(roteiro);
    return this.execute(
      this.http.post<Roteiro>(`${this.baseUrl}/roteiros`, roteiro, { headers: this.getHeaders() }),
      () => local
    );
  }

  updateRoteiro(id: number, roteiro: Partial<Roteiro>): Observable<Roteiro> {
    const local = this.mockDb.updateRoteiro(id, roteiro);
    return this.execute(
      this.http.put<Roteiro>(`${this.baseUrl}/roteiros/${id}`, roteiro, { headers: this.getHeaders() }),
      () => local
    );
  }

  toggleGravacaoRoteiro(id: number): Observable<Roteiro> {
    const local = this.mockDb.toggleGravacaoRoteiro(id);
    return this.execute(
      this.http.patch<Roteiro>(`${this.baseUrl}/roteiros/${id}/toggle`, {}, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteRoteiro(id: number): Observable<boolean> {
    const local = this.mockDb.deleteRoteiro(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/roteiros/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === LOGOS ===
  getLogos(cliente?: string): Observable<LogoCliente[]> {
    let params = new HttpParams();
    if (cliente) params = params.set('cliente', cliente);

    return this.execute(
      this.http.get<LogoCliente[]>(`${this.baseUrl}/logos`, { headers: this.getHeaders(), params }),
      () => this.mockDb.getLogos(cliente)
    );
  }

  createLogo(logo: Partial<LogoCliente>): Observable<LogoCliente> {
    const local = this.mockDb.createLogo(logo);
    return this.execute(
      this.http.post<LogoCliente>(`${this.baseUrl}/logos`, logo, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteLogo(id: number): Observable<boolean> {
    const local = this.mockDb.deleteLogo(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/logos/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === EVENTOS / LOJA DE FOTOS ===
  getEventos(): Observable<Evento[]> {
    return this.execute(
      this.http.get<Evento[]>(`${this.baseUrl}/eventos`, { headers: this.getHeaders() }),
      () => this.mockDb.getEventos()
    );
  }

  getEventoById(id: number): Observable<Evento> {
    return this.execute(
      this.http.get<Evento>(`${this.baseUrl}/eventos/${id}`, { headers: this.getHeaders() }),
      () => {
        const e = this.mockDb.getEventoById(id);
        if (e) return e;
        throw new Error('Evento não encontrado');
      }
    );
  }

  createEvento(evento: Partial<Evento>): Observable<Evento> {
    const local = this.mockDb.createEvento(evento);
    return this.execute(
      this.http.post<Evento>(`${this.baseUrl}/eventos`, evento, { headers: this.getHeaders() }),
      () => local
    );
  }

  addFotoEvento(eventoId: number, foto: Partial<FotoEvento>): Observable<Evento> {
    const local = this.mockDb.addFotoEvento(eventoId, foto);
    return this.execute(
      this.http.post<Evento>(`${this.baseUrl}/eventos/${eventoId}/fotos`, foto, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteFotoEvento(fotoId: number): Observable<boolean> {
    const local = this.mockDb.deleteFotoEvento(fotoId);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/eventos/fotos/${fotoId}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteEvento(id: number): Observable<boolean> {
    const local = this.mockDb.deleteEvento(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/eventos/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === VENDAS ===
  getVendas(status?: string): Observable<VendaFoto[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);

    return this.execute(
      this.http.get<VendaFoto[]>(`${this.baseUrl}/vendas`, { headers: this.getHeaders(), params }),
      () => this.mockDb.getVendas(status)
    );
  }

  createVenda(venda: Partial<VendaFoto>): Observable<VendaFoto> {
    const local = this.mockDb.createVenda(venda);
    return this.execute(
      this.http.post<VendaFoto>(`${this.baseUrl}/vendas`, venda, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === DESPESAS ===
  getDespesas(): Observable<Despesa[]> {
    return this.execute(
      this.http.get<Despesa[]>(`${this.baseUrl}/despesas`, { headers: this.getHeaders() }),
      () => this.mockDb.getDespesas()
    );
  }

  createDespesa(despesa: Partial<Despesa>): Observable<Despesa> {
    const local = this.mockDb.createDespesa(despesa);
    return this.execute(
      this.http.post<Despesa>(`${this.baseUrl}/despesas`, despesa, { headers: this.getHeaders() }),
      () => local
    );
  }

  deleteDespesa(id: number): Observable<boolean> {
    const local = this.mockDb.deleteDespesa(id);
    return this.execute(
      this.http.delete<boolean>(`${this.baseUrl}/despesas/${id}`, { headers: this.getHeaders() }),
      () => local
    );
  }

  // === RELATÓRIOS & FLUXO DE CAIXA ===
  getRelatorioMensal(loja?: string, mes?: any, ano?: any): Observable<RelatorioMensalItem[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);
    if (mes !== undefined && mes !== null) params = params.set('mes', String(mes));
    if (ano !== undefined && ano !== null) params = params.set('ano', String(ano));

    return this.execute(
      this.http.get<RelatorioMensalItem[]>(`${this.baseUrl}/relatorios/mensal`, { headers: this.getHeaders(), params }),
      () => this.mockDb.getRelatorioMensal(loja, mes, ano)
    );
  }

  getFluxoCaixa(): Observable<FluxoCaixa> {
    return this.execute(
      this.http.get<FluxoCaixa>(`${this.baseUrl}/financeiro/fluxo-caixa`, { headers: this.getHeaders() }),
      () => this.mockDb.getFluxoCaixa()
    );
  }
}
