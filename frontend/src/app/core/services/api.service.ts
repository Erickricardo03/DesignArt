import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, of, timeout, catchError } from 'rxjs';
import { getApiBaseUrl } from './api-config';
import { MockStorageService } from './mock-storage.service';
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
} from '../models';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private http = inject(HttpClient);
  private mockDb = inject(MockStorageService);

  private readonly HTTP_TIMEOUT_MS = 6000;

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

  // === DASHBOARD ===
  getDashboardStats(): Observable<DashboardStats> {
    return this.http
      .get<DashboardStats>(`${this.baseUrl}/dashboard/stats`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getDashboardStats()))
      );
  }

  getSaudeFinanceira(): Observable<SaudeFinanceira> {
    return this.http
      .get<SaudeFinanceira>(`${this.baseUrl}/financeiro/saude`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getSaudeFinanceira()))
      );
  }

  // === CLIENTES & MARCAS ===
  getClientes(): Observable<Cliente[]> {
    return this.http
      .get<Cliente[]>(`${this.baseUrl}/clientes`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getClientes()))
      );
  }

  getClienteById(id: number): Observable<Cliente> {
    return this.http
      .get<Cliente>(`${this.baseUrl}/clientes/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          const c = this.mockDb.getClienteById(id);
          if (c) return of(c);
          throw new Error('Cliente não encontrado');
        })
      );
  }

  createCliente(cliente: Partial<Cliente>): Observable<Cliente> {
    return this.http
      .post<Cliente>(`${this.baseUrl}/clientes`, cliente, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createCliente(cliente)))
      );
  }

  updateCliente(id: number, cliente: Partial<Cliente>): Observable<Cliente> {
    return this.http
      .put<Cliente>(`${this.baseUrl}/clientes/${id}`, cliente, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateCliente(id, cliente)))
      );
  }

  deleteCliente(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/clientes/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteCliente(id)))
      );
  }

  // === FATURAS MENSALIDADE ===
  getFaturas(mesReferencia?: string, status?: string, clienteId?: number): Observable<Fatura[]> {
    let params = new HttpParams();
    if (mesReferencia) params = params.set('mesReferencia', mesReferencia);
    if (status) params = params.set('status', status);
    if (clienteId) params = params.set('clienteId', clienteId);

    return this.http
      .get<Fatura[]>(`${this.baseUrl}/faturas`, {
        headers: this.getHeaders(),
        params,
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getFaturas(mesReferencia, status, clienteId)))
      );
  }

  createFatura(fatura: Partial<Fatura>): Observable<Fatura> {
    return this.http
      .post<Fatura>(`${this.baseUrl}/faturas`, fatura, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createFatura(fatura)))
      );
  }

  updateFatura(id: number, fatura: Partial<Fatura>): Observable<Fatura> {
    return this.http
      .put<Fatura>(`${this.baseUrl}/faturas/${id}`, fatura, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateFatura(id, fatura)))
      );
  }

  toggleStatusFatura(id: number): Observable<Fatura> {
    return this.http
      .patch<Fatura>(`${this.baseUrl}/faturas/${id}/toggle`, {}, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.toggleStatusFatura(id)))
      );
  }

  deleteFatura(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/faturas/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteFatura(id)))
      );
  }

  // === SERVIÇOS DO CATÁLOGO ===
  getServicos(): Observable<ServicoCatalogo[]> {
    return this.http
      .get<ServicoCatalogo[]>(`${this.baseUrl}/servicos`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getServicos()))
      );
  }

  createServico(servico: Partial<ServicoCatalogo>): Observable<ServicoCatalogo> {
    return this.http
      .post<ServicoCatalogo>(`${this.baseUrl}/servicos`, servico, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createServico(servico)))
      );
  }

  updateServico(id: number, servico: Partial<ServicoCatalogo>): Observable<ServicoCatalogo> {
    return this.http
      .put<ServicoCatalogo>(`${this.baseUrl}/servicos/${id}`, servico, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateServico(id, servico)))
      );
  }

  deleteServico(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/servicos/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteServico(id)))
      );
  }

  // === EQUIPE & COLABORADORES ===
  getColaboradores(): Observable<Colaborador[]> {
    return this.http
      .get<Colaborador[]>(`${this.baseUrl}/equipe`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getColaboradores()))
      );
  }

  createColaborador(colaborador: Partial<Colaborador>): Observable<Colaborador> {
    return this.http
      .post<Colaborador>(`${this.baseUrl}/equipe`, colaborador, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createColaborador(colaborador)))
      );
  }

  updateColaborador(id: number, colaborador: Partial<Colaborador>): Observable<Colaborador> {
    return this.http
      .put<Colaborador>(`${this.baseUrl}/equipe/${id}`, colaborador, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateColaborador(id, colaborador)))
      );
  }

  deleteColaborador(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/equipe/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteColaborador(id)))
      );
  }

  // === MUNICÍPIOS ===
  getMunicipios(): Observable<Municipio[]> {
    return this.http
      .get<Municipio[]>(`${this.baseUrl}/municipios`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getMunicipios()))
      );
  }

  createMunicipio(m: Partial<Municipio>): Observable<Municipio> {
    return this.http
      .post<Municipio>(`${this.baseUrl}/municipios`, m, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createMunicipio(m)))
      );
  }

  deleteMunicipio(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/municipios/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteMunicipio(id)))
      );
  }

  // === CONFIGURAÇÃO DA LOJA ===
  getConfigLoja(): Observable<ConfiguracaoLoja> {
    return this.http
      .get<ConfiguracaoLoja>(`${this.baseUrl}/configuracoes/loja`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getConfigLoja()))
      );
  }

  updateConfigLoja(config: Partial<ConfiguracaoLoja>): Observable<ConfiguracaoLoja> {
    return this.http
      .put<ConfiguracaoLoja>(`${this.baseUrl}/configuracoes/loja`, config, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateConfigLoja(config)))
      );
  }

  // === HISTÓRICO DE ATIVIDADES ===
  getHistorico(): Observable<AtividadeHistorico[]> {
    return this.http
      .get<AtividadeHistorico[]>(`${this.baseUrl}/historico`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getHistorico()))
      );
  }

  // === TAREFAS ===
  getTarefas(loja?: string, status?: string, prioridade?: string): Observable<Tarefa[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);
    if (status) params = params.set('status', status);
    if (prioridade) params = params.set('prioridade', prioridade);

    return this.http
      .get<Tarefa[]>(`${this.baseUrl}/tarefas`, {
        headers: this.getHeaders(),
        params,
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getTarefas(loja, status, prioridade)))
      );
  }

  getTarefaById(id: number): Observable<Tarefa> {
    return this.http
      .get<Tarefa>(`${this.baseUrl}/tarefas/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          const t = this.mockDb.getTarefaById(id);
          if (t) return of(t);
          throw new Error('Tarefa não encontrada');
        })
      );
  }

  getTarefaByToken(token: string): Observable<Tarefa> {
    return this.http
      .get<Tarefa>(`${this.baseUrl}/tarefas/token/${token}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          const t = this.mockDb.getTarefaByToken(token);
          if (t) return of(t);
          throw new Error('Tarefa não encontrada');
        })
      );
  }

  createTarefa(tarefa: Partial<Tarefa>): Observable<Tarefa> {
    return this.http
      .post<Tarefa>(`${this.baseUrl}/tarefas`, tarefa, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createTarefa(tarefa)))
      );
  }

  updateTarefa(id: number, tarefa: Partial<Tarefa>): Observable<Tarefa> {
    return this.http
      .put<Tarefa>(`${this.baseUrl}/tarefas/${id}`, tarefa, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateTarefa(id, tarefa)))
      );
  }

  deleteTarefa(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/tarefas/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteTarefa(id)))
      );
  }

  addObservacaoTarefa(tarefaId: number, texto: string, autorNome?: string): Observable<Tarefa> {
    return this.http
      .post<Tarefa>(`${this.baseUrl}/tarefas/${tarefaId}/observacoes`, { texto, autorNome }, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.addObservacaoTarefa(tarefaId, texto, autorNome)))
      );
  }

  addArquivoFinal(tarefaId: number, arquivo: { nome: string; urlOuBase64: string; tipo?: 'IMAGEM' | 'VIDEO' | 'DOCUMENTO' }): Observable<Tarefa> {
    return this.http
      .post<Tarefa>(`${this.baseUrl}/tarefas/${tarefaId}/arquivos`, arquivo, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.addArquivoFinal(tarefaId, arquivo)))
      );
  }

  removeArquivoFinal(tarefaId: number, arquivoId: number): Observable<Tarefa> {
    return this.http
      .delete<Tarefa>(`${this.baseUrl}/tarefas/${tarefaId}/arquivos/${arquivoId}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.removeArquivoFinal(tarefaId, arquivoId)))
      );
  }

  responderAprovacao(tarefaId: number, statusAprovacao: 'APROVADO' | 'SOLICITOU_AJUSTE', feedback?: string): Observable<Tarefa> {
    return this.http
      .post<Tarefa>(`${this.baseUrl}/tarefas/${tarefaId}/aprovacao`, { statusAprovacao, feedback }, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.responderAprovacao(tarefaId, statusAprovacao, feedback)))
      );
  }

  // === ROTEIROS ===
  getRoteiros(loja?: string, status?: string): Observable<Roteiro[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);
    if (status) params = params.set('status', status);

    return this.http
      .get<Roteiro[]>(`${this.baseUrl}/roteiros`, {
        headers: this.getHeaders(),
        params,
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getRoteiros(loja, status)))
      );
  }

  getRoteiroById(id: number): Observable<Roteiro> {
    return this.http
      .get<Roteiro>(`${this.baseUrl}/roteiros/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          const r = this.mockDb.getRoteiroById(id);
          if (r) return of(r);
          throw new Error('Roteiro não encontrado');
        })
      );
  }

  createRoteiro(roteiro: Partial<Roteiro>): Observable<Roteiro> {
    return this.http
      .post<Roteiro>(`${this.baseUrl}/roteiros`, roteiro, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createRoteiro(roteiro)))
      );
  }

  updateRoteiro(id: number, roteiro: Partial<Roteiro>): Observable<Roteiro> {
    return this.http
      .put<Roteiro>(`${this.baseUrl}/roteiros/${id}`, roteiro, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateRoteiro(id, roteiro)))
      );
  }

  toggleGravacaoRoteiro(id: number): Observable<Roteiro> {
    return this.http
      .patch<Roteiro>(`${this.baseUrl}/roteiros/${id}/toggle`, {}, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.toggleGravacaoRoteiro(id)))
      );
  }

  deleteRoteiro(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/roteiros/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteRoteiro(id)))
      );
  }

  // === LOGOS ===
  getLogos(cliente?: string): Observable<LogoCliente[]> {
    let params = new HttpParams();
    if (cliente) params = params.set('cliente', cliente);

    return this.http
      .get<LogoCliente[]>(`${this.baseUrl}/logos`, {
        headers: this.getHeaders(),
        params,
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getLogos(cliente)))
      );
  }

  createLogo(logo: Partial<LogoCliente>): Observable<LogoCliente> {
    return this.http
      .post<LogoCliente>(`${this.baseUrl}/logos`, logo, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createLogo(logo)))
      );
  }

  deleteLogo(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/logos/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteLogo(id)))
      );
  }

  // === EVENTOS / LOJA DE FOTOS ===
  getEventos(): Observable<Evento[]> {
    return this.http
      .get<Evento[]>(`${this.baseUrl}/eventos`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getEventos()))
      );
  }

  getEventoById(id: number): Observable<Evento> {
    return this.http
      .get<Evento>(`${this.baseUrl}/eventos/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          const e = this.mockDb.getEventoById(id);
          if (e) return of(e);
          throw new Error('Evento não encontrado');
        })
      );
  }

  createEvento(evento: Partial<Evento>): Observable<Evento> {
    return this.http
      .post<Evento>(`${this.baseUrl}/eventos`, evento, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createEvento(evento)))
      );
  }

  addFotoEvento(eventoId: number, foto: Partial<FotoEvento>): Observable<Evento> {
    return this.http
      .post<Evento>(`${this.baseUrl}/eventos/${eventoId}/fotos`, foto, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.addFotoEvento(eventoId, foto)))
      );
  }

  deleteFotoEvento(fotoId: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/eventos/fotos/${fotoId}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteFotoEvento(fotoId)))
      );
  }

  deleteEvento(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/eventos/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteEvento(id)))
      );
  }

  // === VENDAS ===
  getVendas(status?: string): Observable<VendaFoto[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);

    return this.http
      .get<VendaFoto[]>(`${this.baseUrl}/vendas`, {
        headers: this.getHeaders(),
        params,
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getVendas(status)))
      );
  }

  createVenda(venda: Partial<VendaFoto>): Observable<VendaFoto> {
    return this.http
      .post<VendaFoto>(`${this.baseUrl}/vendas`, venda, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createVenda(venda)))
      );
  }

  // === DESPESAS ===
  getDespesas(): Observable<Despesa[]> {
    return this.http
      .get<Despesa[]>(`${this.baseUrl}/despesas`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getDespesas()))
      );
  }

  createDespesa(despesa: Partial<Despesa>): Observable<Despesa> {
    return this.http
      .post<Despesa>(`${this.baseUrl}/despesas`, despesa, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.createDespesa(despesa)))
      );
  }

  deleteDespesa(id: number): Observable<boolean> {
    return this.http
      .delete<boolean>(`${this.baseUrl}/despesas/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.deleteDespesa(id)))
      );
  }

  // === RELATÓRIOS & FLUXO DE CAIXA ===
  getRelatorioMensal(loja?: string, mes?: any, ano?: any): Observable<RelatorioMensalItem[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);
    if (mes !== undefined && mes !== null) params = params.set('mes', String(mes));
    if (ano !== undefined && ano !== null) params = params.set('ano', String(ano));

    return this.http
      .get<RelatorioMensalItem[]>(`${this.baseUrl}/relatorios/mensal`, {
        headers: this.getHeaders(),
        params,
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getRelatorioMensal(loja, mes, ano)))
      );
  }

  getFluxoCaixa(): Observable<FluxoCaixa> {
    return this.http
      .get<FluxoCaixa>(`${this.baseUrl}/financeiro/fluxo-caixa`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getFluxoCaixa()))
      );
  }
}
