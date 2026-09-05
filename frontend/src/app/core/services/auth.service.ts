import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap, of, catchError, timeout, map } from 'rxjs';
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
  isOfflineMode = signal<boolean>(false);

  constructor(private http: HttpClient) {
    const savedToken = localStorage.getItem('designart_token');
    const savedUser = localStorage.getItem('designart_user');

    if (savedToken && savedUser) {
      try {
        this.token.set(savedToken);
        this.currentUser.set(JSON.parse(savedUser));
        if (savedToken.startsWith('mock-')) {
          this.isOfflineMode.set(true);
        }
      } catch (e) {
        this.logout();
      }
    }
  }

  login(username: string, password: string): Observable<LoginResponse> {
    const u = (username || '').trim();
    const p = (password || '').trim();

    return this.http.post<LoginResponse>(`${this.apiUrl}/login`, { username: u, password: p }).pipe(
      timeout(5000), // Evita travamento infinito no Render se o servidor estiver em cold start ou a URL estiver inacessível
      tap((res) => {
        if (res && res.token) {
          this.token.set(res.token);
          this.currentUser.set(res.user);
          this.isOfflineMode.set(false);
          localStorage.setItem('designart_token', res.token);
          localStorage.setItem('designart_user', JSON.stringify(res.user));
        }
      }),
      catchError((err) => {
        console.warn('Backend indisponível ou resposta lenta. Ativando fallback de contingência...', err);

        // Fallback para usuários cadastrados no sistema
        const mockUser = this.getMockUser(u, p);
        if (mockUser) {
          const mockRes: LoginResponse = {
            token: `mock-jwt-token-designart-${mockUser.username}`,
            type: 'Bearer',
            user: mockUser,
          };
          this.token.set(mockRes.token);
          this.currentUser.set(mockUser);
          this.isOfflineMode.set(true);
          localStorage.setItem('designart_token', mockRes.token);
          localStorage.setItem('designart_user', JSON.stringify(mockUser));
          return of(mockRes);
        }

        throw new Error(err.error?.message || 'Não foi possível conectar ao servidor e as credenciais não conferem.');
      })
    );
  }

  loginDirectMock(username: string = 'admin'): Observable<LoginResponse> {
    const mockUser = this.getMockUser(username, 'admin') || {
      id: 1,
      username: 'admin',
      nomeCompleto: 'Administrador Design Arte',
      role: 'ADMIN',
      cargo: 'Diretor Geral & Estrategista',
      email: 'admin@designarte.com.br',
    };

    const mockRes: LoginResponse = {
      token: `mock-jwt-token-designart-${mockUser.username}`,
      type: 'Bearer',
      user: mockUser,
    };
    this.token.set(mockRes.token);
    this.currentUser.set(mockUser);
    this.isOfflineMode.set(true);
    localStorage.setItem('designart_token', mockRes.token);
    localStorage.setItem('designart_user', JSON.stringify(mockUser));
    return of(mockRes);
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

  private getMockUser(username: string, password?: string): User | null {
    const norm = username.toLowerCase().trim();
    if (norm === 'admin' && (!password || password === 'admin')) {
      return {
        id: 1,
        username: 'admin',
        nomeCompleto: 'Administrador Design Arte',
        role: 'ADMIN',
        cargo: 'Diretor Geral & Estrategista',
        email: 'admin@designarte.com.br',
      };
    }
    if (norm === 'lucas.matheus' && (!password || password === 'admin')) {
      return {
        id: 2,
        username: 'lucas.matheus',
        nomeCompleto: 'Lucas Matheus',
        role: 'COLABORADOR',
        cargo: 'Roteirista & Estrategista',
        email: 'lucas@designarte.com.br',
      };
    }
    if (norm === 'edyllaine.silva' && (!password || password === 'admin')) {
      return {
        id: 3,
        username: 'edyllaine.silva',
        nomeCompleto: 'Edyllaine Silva',
        role: 'COLABORADOR',
        cargo: 'Social Media & Community Manager',
        email: 'edyllaine@designarte.com.br',
      };
    }
    return null;
  }

  logout(): void {
    this.token.set(null);
    this.currentUser.set(null);
    this.isOfflineMode.set(false);
    localStorage.removeItem('designart_token');
    localStorage.removeItem('designart_user');
  }

  isLoggedIn(): boolean {
    return !!this.token();
  }
}
