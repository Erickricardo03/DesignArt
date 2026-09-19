import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, throwError, timeout } from 'rxjs';
import { getApiBaseUrl } from './api-config';

/**
 * Fluxos públicos de conta (recuperação de senha e aceite de convite).
 * Os tokens vivem SOMENTE em memória, nunca em localStorage/sessionStorage/cookies nem em logs.
 */
@Injectable({ providedIn: 'root' })
export class AccountRecoveryService {
  private get apiUrl(): string {
    return `${getApiBaseUrl()}/auth`;
  }

  constructor(private http: HttpClient) {}

  forgotPassword(email: string): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(`${this.apiUrl}/forgot-password`, { email: (email || '').trim() }).pipe(
      timeout(60000),
      catchError((e) => this.erro(e))
    );
  }

  resetPassword(token: string, newPassword: string, confirmPassword: string): Observable<{ message: string }> {
    return this.http
      .post<{ message: string }>(`${this.apiUrl}/reset-password`, { token, newPassword, confirmPassword })
      .pipe(timeout(60000), catchError((e) => this.erro(e)));
  }

  acceptInvite(token: string, newPassword: string, confirmPassword: string): Observable<{ message: string }> {
    return this.http
      .post<{ message: string }>(`${this.apiUrl}/accept-invite`, { token, newPassword, confirmPassword })
      .pipe(timeout(60000), catchError((e) => this.erro(e)));
  }

  /** Mensagens seguras: nunca ecoam token nem dados sensíveis. */
  private erro(err: any): Observable<never> {
    let msg: string;
    if (err?.name === 'TimeoutError') msg = 'O servidor demorou muito para responder. Tente novamente em instantes.';
    else if (err?.status === 429) msg = 'Muitas tentativas. Aguarde alguns minutos e tente novamente.';
    else if (err?.status === 0) msg = 'Servidor inacessível. Verifique sua conexão.';
    else msg = err?.error?.message || 'Não foi possível concluir a operação.';
    return throwError(() => new Error(msg));
  }
}

/** Espelha a política do backend (autoridade): mínimo 10 caracteres, máximo 72 bytes UTF-8. */
export function validarSenha(senha: string, email?: string): string | null {
  if ([...senha].length < 10) return 'A senha deve ter no mínimo 10 caracteres.';
  if (new TextEncoder().encode(senha).length > 72) return 'A senha deve ter no máximo 72 bytes (caracteres acentuados ocupam mais de um byte).';
  if (email && senha.trim().toLowerCase() === email.trim().toLowerCase()) return 'A senha não pode ser igual ao e-mail.';
  return null;
}

/**
 * Lê o token da query string e o REMOVE da URL imediatamente (history.replaceState),
 * para que não permaneça no histórico, na barra de endereço, nem seja enviado como Referer.
 * Retorna o token para guardar apenas em memória.
 */
export function extrairTokenERemoverDaUrl(): string | null {
  try {
    const url = new URL(window.location.href);
    const token = url.searchParams.get('token');
    if (url.searchParams.has('token')) {
      url.searchParams.delete('token');
      const limpa = url.pathname + (url.searchParams.toString() ? '?' + url.searchParams.toString() : '') + url.hash;
      window.history.replaceState(window.history.state, '', limpa);
    }
    return token && /^[A-Za-z0-9_-]{20,128}$/.test(token) ? token : null;
  } catch {
    return null;
  }
}

/** Instrui o navegador a não enviar Referer enquanto a página com token estiver aberta. */
export function aplicarReferrerNoReferrer(): () => void {
  const meta = document.createElement('meta');
  meta.name = 'referrer';
  meta.content = 'no-referrer';
  document.head.appendChild(meta);
  return () => meta.remove();
}
