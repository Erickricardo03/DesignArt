import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import {
  AccountRecoveryService,
  aplicarReferrerNoReferrer,
  extrairTokenERemoverDaUrl,
  validarSenha,
} from '../../core/services/account-recovery.service';
import { AUTH_PAGE_STYLES } from './auth-shell.css';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="auth-wrapper">
      <div class="auth-card">
        <h1>Criar nova senha</h1>
        <p class="sub">Escolha uma nova senha para a sua conta. Este link só pode ser usado uma vez.</p>

        <div *ngIf="!token() && !concluido()" class="auth-alert error">
          Link inválido ou incompleto. Solicite uma nova recuperação de senha.
        </div>
        <div *ngIf="concluido()" class="auth-alert ok">
          Senha redefinida com sucesso. Por segurança, todas as sessões anteriores foram encerradas.
        </div>
        <div *ngIf="erro()" class="auth-alert error">{{ erro() }}</div>

        <form *ngIf="token() && !concluido()" (ngSubmit)="enviar()">
          <div class="form-group">
            <label class="form-label" for="novaSenha">NOVA SENHA</label>
            <input id="novaSenha" name="novaSenha" type="password" class="form-control" [(ngModel)]="senha"
                   autocomplete="new-password" required />
            <div class="pwd-hint">Mínimo de 10 caracteres.</div>
          </div>
          <div class="form-group">
            <label class="form-label" for="confirmacao">CONFIRME A NOVA SENHA</label>
            <input id="confirmacao" name="confirmacao" type="password" class="form-control" [(ngModel)]="confirmacao"
                   autocomplete="new-password" required />
          </div>
          <button type="submit" class="btn btn-primary btn-submit" [disabled]="carregando()">
            {{ carregando() ? 'Salvando...' : 'Redefinir senha' }}
          </button>
        </form>

        <div class="auth-links">
          <a *ngIf="!token() || erro()" routerLink="/forgot-password">Solicitar novo link</a>
          <a routerLink="/login">{{ concluido() ? 'Ir para o login' : 'Voltar ao login' }}</a>
        </div>
      </div>
    </div>
  `,
  styles: [AUTH_PAGE_STYLES],
})
export class ResetPasswordComponent implements OnInit, OnDestroy {
  private service = inject(AccountRecoveryService);

  // O token existe SOMENTE nesta variável em memória: sai da URL logo ao abrir a página.
  token = signal<string | null>(null);
  senha = '';
  confirmacao = '';
  carregando = signal(false);
  concluido = signal(false);
  erro = signal('');
  private removerReferrer: (() => void) | null = null;

  ngOnInit(): void {
    this.removerReferrer = aplicarReferrerNoReferrer();
    this.token.set(extrairTokenERemoverDaUrl());
  }

  ngOnDestroy(): void {
    this.token.set(null);
    this.senha = '';
    this.confirmacao = '';
    this.removerReferrer?.();
  }

  enviar(): void {
    const token = this.token();
    if (!token) return;
    if (this.senha !== this.confirmacao) {
      this.erro.set('A confirmação da senha não confere.');
      return;
    }
    const problema = validarSenha(this.senha);
    if (problema) {
      this.erro.set(problema);
      return;
    }
    this.carregando.set(true);
    this.erro.set('');
    this.service.resetPassword(token, this.senha, this.confirmacao).subscribe({
      next: () => {
        this.carregando.set(false);
        this.concluido.set(true);
        this.token.set(null);
        this.senha = '';
        this.confirmacao = '';
      },
      error: (e) => {
        this.carregando.set(false);
        this.erro.set(e.message);
      },
    });
  }
}
