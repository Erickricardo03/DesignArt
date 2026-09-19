import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AccountRecoveryService } from '../../core/services/account-recovery.service';
import { AUTH_PAGE_STYLES } from './auth-shell.css';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="auth-wrapper">
      <div class="auth-card">
        <h1>Esqueci minha senha</h1>
        <p class="sub">Informe o e-mail da sua conta. Se ele estiver cadastrado e ativo, enviaremos um link para você criar uma nova senha.</p>

        <div *ngIf="enviado()" class="auth-alert ok">
          Se existir uma conta elegível para este e-mail, enviaremos as instruções de recuperação. Verifique também a caixa de spam.
        </div>
        <div *ngIf="erro()" class="auth-alert error">{{ erro() }}</div>

        <form *ngIf="!enviado()" (ngSubmit)="enviar()">
          <div class="form-group">
            <label class="form-label" for="email">E-MAIL</label>
            <input id="email" name="email" type="email" class="form-control" [(ngModel)]="email"
                   autocomplete="username" autocapitalize="none" spellcheck="false" required />
          </div>
          <button type="submit" class="btn btn-primary btn-submit" [disabled]="carregando()">
            {{ carregando() ? 'Enviando...' : 'Enviar link de recuperação' }}
          </button>
        </form>
        <div class="auth-links"><a routerLink="/login">Voltar ao login</a></div>
      </div>
    </div>
  `,
  styles: [AUTH_PAGE_STYLES],
})
export class ForgotPasswordComponent {
  private service = inject(AccountRecoveryService);
  email = '';
  carregando = signal(false);
  enviado = signal(false);
  erro = signal('');

  enviar(): void {
    if (!this.email.trim()) {
      this.erro.set('Informe o seu e-mail.');
      return;
    }
    this.carregando.set(true);
    this.erro.set('');
    this.service.forgotPassword(this.email).subscribe({
      next: () => {
        this.carregando.set(false);
        this.enviado.set(true); // mesma mensagem, exista a conta ou não
      },
      error: (e) => {
        this.carregando.set(false);
        this.erro.set(e.message);
      },
    });
  }
}
