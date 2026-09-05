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

  updateTarefaStatus(id: number, status: string): Observable<Tarefa> {
    return this.http
      .patch<Tarefa>(
        `${this.baseUrl}/tarefas/${id}/status`,
        { status },
        { headers: this.getHeaders() }
      )
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateTarefa(id, { status: status as any })))
      );
  }

  toggleChecklistItem(tarefaId: number, itemId: number): Observable<Tarefa> {
    return this.http
      .patch<Tarefa>(
        `${this.baseUrl}/tarefas/${tarefaId}/checklist/${itemId}/toggle`,
        {},
        { headers: this.getHeaders() }
      )
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.toggleChecklistItem(tarefaId, itemId)))
      );
  }

  deleteTarefa(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/tarefas/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          this.mockDb.deleteTarefa(id);
          return of(undefined);
        })
      );
  }

  // === ROTEIROS ===
  getRoteiros(loja?: string): Observable<Roteiro[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);

    return this.http
      .get<Roteiro[]>(`${this.baseUrl}/roteiros`, {
        headers: this.getHeaders(),
        params,
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getRoteiros(loja)))
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

  toggleRoteiroConcluido(id: number): Observable<Roteiro> {
    return this.http
      .patch<Roteiro>(
        `${this.baseUrl}/roteiros/${id}/toggle-concluido`,
        {},
        { headers: this.getHeaders() }
      )
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.toggleRoteiroConcluido(id)))
      );
  }

  deleteRoteiro(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/roteiros/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          this.mockDb.deleteRoteiro(id);
          return of(undefined);
        })
      );
  }

  // === LOGOS CLIENTES ===
  getLogos(clienteNome?: string): Observable<LogoCliente[]> {
    let params = new HttpParams();
    if (clienteNome) params = params.set('clienteNome', clienteNome);

    return this.http
      .get<LogoCliente[]>(`${this.baseUrl}/logos`, {
        headers: this.getHeaders(),
        params,
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getLogos(clienteNome)))
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

  deleteLogo(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/logos/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          this.mockDb.deleteLogo(id);
          return of(undefined);
        })
      );
  }

  // === EVENTOS & FOTOS ===
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

  addFotoEvento(eventoId: number, foto: Partial<FotoEvento>): Observable<FotoEvento> {
    return this.http
      .post<FotoEvento>(`${this.baseUrl}/eventos/${eventoId}/fotos`, foto, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.addFotoEvento(eventoId, foto)))
      );
  }

  deleteFotoEvento(fotoId: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/eventos/fotos/${fotoId}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          this.mockDb.deleteFotoEvento(fotoId);
          return of(undefined);
        })
      );
  }

  deleteEvento(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/eventos/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          this.mockDb.deleteEvento(id);
          return of(undefined);
        })
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

  updateVendaStatus(id: number, status: string): Observable<VendaFoto> {
    return this.http
      .patch<VendaFoto>(
        `${this.baseUrl}/vendas/${id}/status`,
        { status },
        { headers: this.getHeaders() }
      )
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateVendaStatus(id, status as any)))
      );
  }

  deleteVenda(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/vendas/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          this.mockDb.deleteVenda(id);
          return of(undefined);
        })
      );
  }

  // === DESPESAS & FLUXO DE CAIXA ===
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

  getFluxoCaixa(): Observable<FluxoCaixa> {
    return this.http
      .get<FluxoCaixa>(`${this.baseUrl}/despesas/fluxo-caixa`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.getFluxoCaixa()))
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

  updateDespesa(id: number, despesa: Partial<Despesa>): Observable<Despesa> {
    return this.http
      .put<Despesa>(`${this.baseUrl}/despesas/${id}`, despesa, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => of(this.mockDb.updateDespesa(id, despesa)))
      );
  }

  deleteDespesa(id: number): Observable<void> {
    return this.http
      .delete<void>(`${this.baseUrl}/despesas/${id}`, {
        headers: this.getHeaders(),
      })
      .pipe(
        timeout(this.HTTP_TIMEOUT_MS),
        catchError(() => {
          this.mockDb.deleteDespesa(id);
          return of(undefined);
        })
      );
  }

  // === RELATÓRIOS MENSAL POR LOJA ===
  getRelatorioMensal(loja?: string, mes?: number, ano?: number): Observable<RelatorioMensalItem[]> {
    let params = new HttpParams();
    if (loja) params = params.set('loja', loja);
    if (mes) params = params.set('mes', mes.toString());
    if (ano) params = params.set('ano', ano.toString());

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
}
