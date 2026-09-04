import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
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
} from '../models';

@Injectable({
  providedIn: 'root',
})
export class ApiService {
  private baseUrl = 'http://localhost:8080/api';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('designart_token');
    return new HttpHeaders({
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    });
  }

  // === DASHBOARD ===
  getDashboardStats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${this.baseUrl}/dashboard/stats`, {
      headers: this.getHeaders(),
    });
  }

  // === TAREFAS ===
  getTarefas(loja?: string, status?: string, prioridade?: string): Observable<Tarefa[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);
    if (status) params = params.set('status', status);
    if (prioridade) params = params.set('prioridade', prioridade);

    return this.http.get<Tarefa[]>(`${this.baseUrl}/tarefas`, {
      headers: this.getHeaders(),
      params,
    });
  }

  getTarefaById(id: number): Observable<Tarefa> {
    return this.http.get<Tarefa>(`${this.baseUrl}/tarefas/${id}`, {
      headers: this.getHeaders(),
    });
  }

  createTarefa(tarefa: Partial<Tarefa>): Observable<Tarefa> {
    return this.http.post<Tarefa>(`${this.baseUrl}/tarefas`, tarefa, {
      headers: this.getHeaders(),
    });
  }

  updateTarefa(id: number, tarefa: Partial<Tarefa>): Observable<Tarefa> {
    return this.http.put<Tarefa>(`${this.baseUrl}/tarefas/${id}`, tarefa, {
      headers: this.getHeaders(),
    });
  }

  updateTarefaStatus(id: number, status: string): Observable<Tarefa> {
    return this.http.patch<Tarefa>(
      `${this.baseUrl}/tarefas/${id}/status`,
      { status },
      { headers: this.getHeaders() }
    );
  }

  toggleChecklistItem(tarefaId: number, itemId: number): Observable<Tarefa> {
    return this.http.patch<Tarefa>(
      `${this.baseUrl}/tarefas/${tarefaId}/checklist/${itemId}/toggle`,
      {},
      { headers: this.getHeaders() }
    );
  }

  deleteTarefa(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/tarefas/${id}`, {
      headers: this.getHeaders(),
    });
  }

  // === ROTEIROS ===
  getRoteiros(loja?: string): Observable<Roteiro[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);

    return this.http.get<Roteiro[]>(`${this.baseUrl}/roteiros`, {
      headers: this.getHeaders(),
      params,
    });
  }

  createRoteiro(roteiro: Partial<Roteiro>): Observable<Roteiro> {
    return this.http.post<Roteiro>(`${this.baseUrl}/roteiros`, roteiro, {
      headers: this.getHeaders(),
    });
  }

  updateRoteiro(id: number, roteiro: Partial<Roteiro>): Observable<Roteiro> {
    return this.http.put<Roteiro>(`${this.baseUrl}/roteiros/${id}`, roteiro, {
      headers: this.getHeaders(),
    });
  }

  toggleRoteiroConcluido(id: number): Observable<Roteiro> {
    return this.http.patch<Roteiro>(
      `${this.baseUrl}/roteiros/${id}/toggle-concluido`,
      {},
      { headers: this.getHeaders() }
    );
  }

  deleteRoteiro(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/roteiros/${id}`, {
      headers: this.getHeaders(),
    });
  }

  // === LOGOS CLIENTES ===
  getLogos(clienteNome?: string): Observable<LogoCliente[]> {
    let params = new HttpParams();
    if (clienteNome) params = params.set('clienteNome', clienteNome);

    return this.http.get<LogoCliente[]>(`${this.baseUrl}/logos`, {
      headers: this.getHeaders(),
      params,
    });
  }

  createLogo(logo: Partial<LogoCliente>): Observable<LogoCliente> {
    return this.http.post<LogoCliente>(`${this.baseUrl}/logos`, logo, {
      headers: this.getHeaders(),
    });
  }

  deleteLogo(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/logos/${id}`, {
      headers: this.getHeaders(),
    });
  }

  // === EVENTOS & FOTOS ===
  getEventos(): Observable<Evento[]> {
    return this.http.get<Evento[]>(`${this.baseUrl}/eventos`, {
      headers: this.getHeaders(),
    });
  }

  getEventoById(id: number): Observable<Evento> {
    return this.http.get<Evento>(`${this.baseUrl}/eventos/${id}`, {
      headers: this.getHeaders(),
    });
  }

  createEvento(evento: Partial<Evento>): Observable<Evento> {
    return this.http.post<Evento>(`${this.baseUrl}/eventos`, evento, {
      headers: this.getHeaders(),
    });
  }

  addFotoEvento(eventoId: number, foto: Partial<FotoEvento>): Observable<FotoEvento> {
    return this.http.post<FotoEvento>(`${this.baseUrl}/eventos/${eventoId}/fotos`, foto, {
      headers: this.getHeaders(),
    });
  }

  deleteFotoEvento(fotoId: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/eventos/fotos/${fotoId}`, {
      headers: this.getHeaders(),
    });
  }

  deleteEvento(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/eventos/${id}`, {
      headers: this.getHeaders(),
    });
  }

  // === VENDAS ===
  getVendas(status?: string): Observable<VendaFoto[]> {
    let params = new HttpParams();
    if (status) params = params.set('status', status);

    return this.http.get<VendaFoto[]>(`${this.baseUrl}/vendas`, {
      headers: this.getHeaders(),
      params,
    });
  }

  createVenda(venda: Partial<VendaFoto>): Observable<VendaFoto> {
    return this.http.post<VendaFoto>(`${this.baseUrl}/vendas`, venda, {
      headers: this.getHeaders(),
    });
  }

  updateVendaStatus(id: number, status: string): Observable<VendaFoto> {
    return this.http.patch<VendaFoto>(
      `${this.baseUrl}/vendas/${id}/status`,
      { status },
      { headers: this.getHeaders() }
    );
  }

  deleteVenda(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/vendas/${id}`, {
      headers: this.getHeaders(),
    });
  }

  // === DESPESAS & FLUXO DE CAIXA ===
  getDespesas(): Observable<Despesa[]> {
    return this.http.get<Despesa[]>(`${this.baseUrl}/despesas`, {
      headers: this.getHeaders(),
    });
  }

  getFluxoCaixa(): Observable<FluxoCaixa> {
    return this.http.get<FluxoCaixa>(`${this.baseUrl}/despesas/fluxo-caixa`, {
      headers: this.getHeaders(),
    });
  }

  createDespesa(despesa: Partial<Despesa>): Observable<Despesa> {
    return this.http.post<Despesa>(`${this.baseUrl}/despesas`, despesa, {
      headers: this.getHeaders(),
    });
  }

  updateDespesa(id: number, despesa: Partial<Despesa>): Observable<Despesa> {
    return this.http.put<Despesa>(`${this.baseUrl}/despesas/${id}`, despesa, {
      headers: this.getHeaders(),
    });
  }

  deleteDespesa(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/despesas/${id}`, {
      headers: this.getHeaders(),
    });
  }

  // === RELATÓRIOS MENSAL POR LOJA ===
  getRelatorioMensal(loja?: string, mes?: number, ano?: number): Observable<RelatorioMensalItem[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);
    if (mes) params = params.set('mes', mes.toString());
    if (ano) params = params.set('ano', ano.toString());

    return this.http.get<RelatorioMensalItem[]>(`${this.baseUrl}/relatorios/mensal`, {
      headers: this.getHeaders(),
      params,
    });
  }
}
