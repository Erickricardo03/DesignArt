import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, of, catchError, timeout, map, throwError } from 'rxjs';
import { getApiBaseUrl } from './api-config';
import { LoginResponse, User } from '../models';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private get apiUrl(): string {
    return `${getApiBaseUrl()}/auth`;
  }
  currentUser = signal<User | null>(null);
  token = signal<string | null>(null);

  constructor(private http: HttpClient) {
    const savedToken = localStorage.getItem('designart_token');
    const savedUser = localStorage.getItem('designart_user');

    if (savedToken && savedUser) {
      try {
        const user = JSON.parse(savedUser) as User;
        // Sessão salva antes da Fase 4.1 (papéis ADMIN/COLABORADOR, sem e-mail): descarta,
        // pois o token antigo não é mais aceito pelo backend.
        if (!['SUPER_ADMIN', 'TENANT_ADMIN', 'USER'].includes(user?.role) || !user?.email) {
          this.logout();
          return;
        }
        this.token.set(savedToken);
        this.currentUser.set(user);
      } catch (e) {
        this.logout();
      }
    }
  }

  /**
   * Autentica exclusivamente contra o backend. Não existe mais nenhum
   * fallback local com usuários/senhas fixos: se a API não responder ou as
   * credenciais forem inválidas, o erro é propagado para a tela de login
   * mostrar a mensagem real (nunca um acesso fictício).
   */
  login(email: string, password: string): Observable<LoginResponse> {
    // O backend normaliza o e-mail (trim + minúsculas). A senha é enviada EXATAMENTE
    // como digitada: nunca é aparada nem alterada (espaços podem fazer parte dela).
    const e = (email || '').trim();

    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, { email: e, password }).pipe(
      // Timeout generoso: hospedagens gratuitas (ex: Render) podem levar
      // dezenas de segundos para "acordar" no primeiro acesso após ficarem
      // inativas, e não há mais fallback para cobrir essa espera.
      timeout(60000),
      tap((res) => {
        if (res && res.token) {
          this.token.set(res.token);
          this.currentUser.set(res.user);
          localStorage.setItem('designart_token', res.token);
          localStorage.setItem('designart_user', JSON.stringify(res.user));
        }
      }),
      catchError((err) => {
        const mensagem =
          err.name === 'TimeoutError'
            ? 'O servidor demorou muito para responder. Tente novamente em instantes.'
            : err.error?.message || 'Usuário ou senha inválidos.';
        return throwError(() => new Error(mensagem));
      })
    );
  }

  checkBackendHealth(): Observable<{ online: boolean; latencyMs?: number; url: string; error?: string }> {
    const start = Date.now();
    const url = getApiBaseUrl();
    return this.http.get(`${url}/auth/ping`, { responseType: 'text' }).pipe(
      timeout(4000),
      map(() => {
        const latency = Date.now() - start;
        return { online: true, latencyMs: latency, url };
      }),
      catchError((err) => {
        return of({
          online: false,
          url,
          error: err.name === 'TimeoutError' ? 'Tempo limite esgotado (Servidor hibernando ou URL incorreta)' : 'Servidor inacessível',
        });
      })
    );
  }

  logout(): void {
    this.token.set(null);
    this.currentUser.set(null);
    localStorage.removeItem('designart_token');
    localStorage.removeItem('designart_user');
  }

  isLoggedIn(): boolean {
    return !!this.token();
  }
}
