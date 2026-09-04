import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="login-wrapper">
      <!-- Theme Switcher Floating -->
      <div class="theme-switcher-top">
        <button class="btn-theme" (click)="themeService.toggleTheme()" [title]="themeService.isDarkMode() ? 'Mudar para Modo Claro' : 'Mudar para Modo Escuro'">
          <i class="bi" [ngClass]="themeService.isDarkMode() ? 'bi-moon-stars-fill' : 'bi-sun-fill'"></i>
          <span>{{ themeService.isDarkMode() ? 'Modo Escuro' : 'Modo Claro' }}</span>
        </button>
      </div>

      <div class="login-card-container">
        <!-- Lado Esquerdo: Banner Institucional (Padrão PDF Página 8) -->
        <div class="login-left-banner">
          <div class="banner-content">
            <div class="logo-symbol">
              <i class="bi bi-gem"></i>
            </div>
            <h2 class="brand-title">Design Arte</h2>
            
            <div class="security-badge">
              <i class="bi bi-shield-check"></i>
              <span>ACESSO SEGURO</span>
            </div>

            <h3 class="welcome-heading">Bem-vindo ao sistema</h3>
            <p class="welcome-text">
              Acesse sua unidade com segurança para acompanhar atendimentos, relatórios, roteiros e rotinas administrativas.
            </p>
          </div>
          
          <div class="banner-footer-decor">
            <a routerLink="/" class="back-home-link">
              <i class="bi bi-arrow-left"></i>
              <span>Voltar ao Início</span>
            </a>
          </div>
        </div>

        <!-- Lado Direito: Formulário de Autenticação -->
        <div class="login-right-form">
          <div class="form-header">
            <span class="auth-tag">IDENTIFICAÇÃO</span>
            <h2>Entrar no sistema</h2>
            <p>Informe login e senha para continuar.</p>
          </div>

          <form (ngSubmit)="onLogin()" class="login-form">
            <div *ngIf="errorMessage()" class="alert-error">
              <i class="bi bi-exclamation-circle-fill"></i>
              <span>{{ errorMessage() }}</span>
            </div>

            <div class="form-group">
              <label class="form-label" for="username">LOGIN / USUÁRIO</label>
              <div class="input-with-icon">
                <i class="bi bi-person-fill"></i>
                <input 
                  type="text" 
                  id="username" 
                  name="username" 
                  class="form-control" 
                  [(ngModel)]="username" 
                  placeholder="Ex: admin"
                  required
                />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label" for="password">SENHA</label>
              <div class="input-with-icon">
                <i class="bi bi-lock-fill"></i>
                <input 
                  [type]="showPassword() ? 'text' : 'password'" 
                  id="password" 
                  name="password" 
                  class="form-control" 
                  [(ngModel)]="password" 
                  placeholder="Informe sua senha"
                  required
                />
                <button type="button" class="btn-toggle-pwd" (click)="toggleShowPassword()">
                  <i class="bi" [ngClass]="showPassword() ? 'bi-eye-slash-fill' : 'bi-eye-fill'"></i>
                </button>
              </div>
            </div>

            <div class="quick-credentials-hint">
              <i class="bi bi-info-circle-fill"></i>
              <span>Usuário padrão: <strong>admin</strong> | Senha: <strong>admin</strong></span>
            </div>

            <button type="submit" class="btn btn-primary btn-submit" [disabled]="loading()">
              <span *ngIf="!loading()">Entrar &rarr;</span>
              <span *ngIf="loading()">Autenticando...</span>
            </button>
          </form>

          <div class="login-footer-info">
            <span>Versão 3.0.2 atualizada</span>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .login-wrapper {
      min-height: 100vh;
      display: flex;
      align-items: center;
      justify-content: center;
      background-color: var(--bg-app);
      padding: 2rem 1rem;
      position: relative;
    }

    .theme-switcher-top {
      position: absolute;
      top: 1.5rem;
      right: 1.5rem;
      z-index: 10;
    }

    .btn-theme {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.6rem 1rem;
      border-radius: var(--radius-full);
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      font-size: 0.85rem;
      font-weight: 600;
      cursor: pointer;
      box-shadow: var(--shadow-sm);
      transition: all 0.2s;
    }

    .btn-theme:hover {
      background: var(--bg-surface-elevated);
      transform: translateY(-2px);
    }

    .login-card-container {
      width: 100%;
      max-width: 950px;
      min-height: 560px;
      display: flex;
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-xl);
      overflow: hidden;
      box-shadow: var(--shadow-xl);
    }

    /* Left Banner */
    .login-left-banner {
      flex: 1;
      background: linear-gradient(145deg, #4338CA 0%, #312E81 100%);
      color: #FFFFFF;
      padding: 3.5rem 2.5rem;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      position: relative;
    }

    .login-left-banner::before {
      content: '';
      position: absolute;
      inset: 0;
      background: radial-gradient(circle at top right, rgba(99, 102, 241, 0.4), transparent 60%);
      pointer-events: none;
    }

    .banner-content {
      position: relative;
      z-index: 1;
    }

    .logo-symbol {
      width: 50px;
      height: 50px;
      border-radius: var(--radius-md);
      background: rgba(255, 255, 255, 0.15);
      border: 1px solid rgba(255, 255, 255, 0.3);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.6rem;
      margin-bottom: 1rem;
    }

    .brand-title {
      font-size: 2rem;
      font-weight: 800;
      margin-bottom: 2rem;
      color: #FFFFFF;
    }

    .security-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.35rem 0.85rem;
      background: rgba(255, 255, 255, 0.15);
      border-radius: var(--radius-full);
      font-size: 0.725rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      margin-bottom: 1.25rem;
    }

    .welcome-heading {
      font-size: 1.5rem;
      margin-bottom: 0.75rem;
      color: #FFFFFF;
    }

    .welcome-text {
      color: rgba(255, 255, 255, 0.85);
      font-size: 0.95rem;
      line-height: 1.6;
    }

    .back-home-link {
      color: rgba(255, 255, 255, 0.8);
      font-size: 0.85rem;
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      font-weight: 600;
      transition: color 0.2s;
    }

    .back-home-link:hover {
      color: #FFFFFF;
    }

    /* Right Form */
    .login-right-form {
      flex: 1.1;
      padding: 3.5rem 3rem;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }

    .form-header {
      margin-bottom: 2rem;
    }

    .auth-tag {
      font-size: 0.75rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      color: var(--color-primary);
      display: block;
      margin-bottom: 0.35rem;
    }

    .form-header h2 {
      font-size: 1.85rem;
      margin-bottom: 0.35rem;
    }

    .form-header p {
      color: var(--text-muted);
      font-size: 0.9rem;
      margin: 0;
    }

    .input-with-icon {
      position: relative;
      display: flex;
      align-items: center;
    }

    .input-with-icon > i {
      position: absolute;
      left: 1rem;
      color: var(--text-muted);
      font-size: 1rem;
      pointer-events: none;
    }

    .input-with-icon .form-control {
      padding-left: 2.75rem;
      padding-right: 2.75rem;
    }

    .btn-toggle-pwd {
      position: absolute;
      right: 0.75rem;
      background: transparent;
      border: none;
      color: var(--text-muted);
      cursor: pointer;
      padding: 0.25rem;
    }

    .quick-credentials-hint {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.65rem 0.85rem;
      background: var(--color-primary-light);
      border-radius: var(--radius-md);
      font-size: 0.8rem;
      color: var(--color-primary);
      margin-bottom: 1.5rem;
    }

    .btn-submit {
      width: 100%;
      padding: 0.85rem;
      font-size: 1rem;
      border-radius: var(--radius-md);
    }

    .alert-error {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
      background: var(--color-danger-light);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: var(--color-danger);
      border-radius: var(--radius-md);
      font-size: 0.85rem;
      margin-bottom: 1.25rem;
    }

    .login-footer-info {
      text-align: center;
      color: var(--text-muted);
      font-size: 0.75rem;
      margin-top: 1.5rem;
    }

    @media (max-width: 800px) {
      .login-wrapper {
        padding: 1rem 0.65rem;
      }
      .theme-switcher-top {
        top: 0.75rem;
        right: 0.75rem;
      }
      .login-card-container {
        flex-direction: column;
        border-radius: var(--radius-lg);
        min-height: auto;
        width: 100%;
        max-width: 100%;
        box-sizing: border-box;
      }
      .login-left-banner {
        padding: 2rem 1.25rem;
      }
      .brand-title {
        font-size: 1.6rem;
        margin-bottom: 1rem;
      }
      .welcome-heading {
        font-size: 1.25rem;
      }
      .login-right-form {
        padding: 2rem 1.25rem;
      }
    }
  `]
})
export class LoginComponent {
  authService = inject(AuthService);
  themeService = inject(ThemeService);
  private router = inject(Router);

  username: string = 'admin';
  password: string = 'admin';
  showPassword = signal<boolean>(false);
  loading = signal<boolean>(false);
  errorMessage = signal<string>('');

  toggleShowPassword(): void {
    this.showPassword.update((val) => !val);
  }

  onLogin(): void {
    if (!this.username.trim() || !this.password.trim()) {
      this.errorMessage.set('Por favor, preencha o usuário e a senha.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');

    this.authService.login(this.username, this.password).subscribe({
      next: () => {
        this.loading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err.error?.message || 'Usuário ou senha incorretos.');
      }
    });
  }
}
