import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { getApiBaseUrl, setApiBaseUrl, resetApiBaseUrl, isCustomApiUrlSet, getDefaultApiUrl } from '../../core/services/api-config';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="login-wrapper">
      <!-- Top Actions: Status, API Config & Theme Switcher -->
      <div class="top-bar-controls">
        <!-- Badge de Status do Backend -->
        <button 
          class="status-pill-btn" 
          (click)="abrirModalConfig()"
          [title]="'Clique para ver detalhes do backend ou configurar URL'"
        >
          <span class="status-indicator-dot" [ngClass]="statusDotClass()"></span>
          <span class="status-label-text">{{ statusLabel() }}</span>
          <i class="bi bi-gear-fill config-mini-icon"></i>
        </button>

        <!-- Theme Switcher -->
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
              <span>ACESSO SEGURO &middot; PRODUÇÃO & METAS</span>
            </div>

            <h3 class="welcome-heading">Bem-vindo ao sistema</h3>
            <p class="welcome-text">
              Acesse com segurança para gerenciar roteiros no set, demandas de lojas parceiras, relatórios mensais e vendas de fotos.
            </p>

          </div>

          <div class="banner-footer-decor">
            <a routerLink="/" class="back-home-link">
              <i class="bi bi-arrow-left"></i>
              <span>Voltar à Página Inicial</span>
            </a>
          </div>
        </div>

        <!-- Lado Direito: Formulário de Autenticação -->
        <div class="login-right-form">
          <div class="form-header">
            <div class="d-flex align-items-center justify-content-between mb-1">
              <span class="auth-tag">IDENTIFICAÇÃO DE EQUIPE</span>
              <button type="button" class="btn-api-settings" (click)="abrirModalConfig()" title="Configurar URL do Backend">
                <i class="bi bi-sliders2"></i> Servidor API
              </button>
            </div>
            <h2>Entrar no sistema</h2>
            <p>Informe seu e-mail e senha cadastrados para continuar.</p>
          </div>

          <!-- Mensagem de Alerta ou Dica de Conexão -->
          <div *ngIf="backendStatus() === 'cold_start'" class="alert-info-box">
            <i class="bi bi-info-circle-fill"></i>
            <div>
              <strong>Servidor gratuito no Render iniciando:</strong> O backend em nuvem pode levar até 50 segundos para despertar na primeira requisição. Aguarde alguns instantes e tente novamente.
            </div>
          </div>

          <form (ngSubmit)="onLogin()" class="login-form">
            <div *ngIf="errorMessage()" class="alert-error">
              <i class="bi bi-exclamation-circle-fill"></i>
              <span>{{ errorMessage() }}</span>
            </div>

            <div class="form-group">
              <label class="form-label" for="email">E-MAIL</label>
              <div class="input-with-icon">
                <i class="bi bi-envelope-fill"></i>
                <input
                  type="email"
                  id="email"
                  name="email"
                  class="form-control"
                  [(ngModel)]="email"
                  placeholder="voce@empresa.com"
                  autocomplete="username"
                  autocapitalize="none"
                  spellcheck="false"
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
                  autocomplete="current-password"
                  required
                />
                <button type="button" class="btn-toggle-pwd" (click)="toggleShowPassword()">
                  <i class="bi" [ngClass]="showPassword() ? 'bi-eye-slash-fill' : 'bi-eye-fill'"></i>
                </button>
              </div>
            </div>

            <div class="forgot-row"><a routerLink="/forgot-password">Esqueci minha senha</a></div>

            <button type="submit" class="btn btn-primary btn-submit mb-2" [disabled]="loading()">
              <span *ngIf="!loading()">Entrar no Sistema &rarr;</span>
              <span *ngIf="loading()" class="d-flex align-items-center justify-content-center gap-2">
                <span class="spinner-border-sm"></span> Conectando...
              </span>
            </button>
          </form>

          <div class="login-footer-info">
            <span>Versão 3.1.0 &middot; Design Arte Produções</span>
          </div>
        </div>
      </div>

      <!-- Modal de Configuração do Backend / API URL -->
      <div *ngIf="showConfigModal()" class="modal-overlay" (click)="fecharModalConfig()">
        <div class="modal-card" (click)="$event.stopPropagation()">
          <div class="modal-header">
            <div class="d-flex align-items-center gap-2">
              <i class="bi bi-hdd-network-fill text-primary" style="font-size: 1.3rem;"></i>
              <h3 class="modal-title m-0">Configuração de Servidor API</h3>
            </div>
            <button class="btn-close-modal" (click)="fecharModalConfig()">&times;</button>
          </div>

          <div class="modal-body">
            <p class="modal-description">
              Configure o endereço do backend (API Spring Boot). Se estiver rodando no Render ou localmente, você pode definir a URL personalizada aqui.
            </p>

            <div class="form-group mb-3">
              <label class="form-label">URL DA API BACKEND</label>
              <div class="input-with-icon">
                <i class="bi bi-link-45deg"></i>
                <input 
                  type="text" 
                  class="form-control" 
                  [(ngModel)]="tempApiUrl" 
                  placeholder="https://seu-servico-api.onrender.com/api"
                />
              </div>
              <small class="text-muted d-block mt-1">
                Padrão atual: <code>{{ defaultUrl }}</code>
              </small>
            </div>

            <!-- Resultado do Teste de Conexão -->
            <div *ngIf="testResult()" class="test-result-box" [ngClass]="testResult()!.online ? 'result-success' : 'result-error'">
              <i class="bi" [ngClass]="testResult()!.online ? 'bi-check-circle-fill' : 'bi-exclamation-triangle-fill'"></i>
              <div>
                <strong>{{ testResult()!.online ? 'Conexão Estabelecida com Sucesso!' : 'Falha ao Conectar' }}</strong>
                <p class="m-0 text-small">
                  {{ testResult()!.online ? ('Latência: ' + testResult()!.latencyMs + 'ms') : testResult()!.error }}
                </p>
              </div>
            </div>

            <div class="quick-url-presets mt-3">
              <span class="presets-label">Atalhos rápidos:</span>
              <div class="presets-buttons">
                <button type="button" class="btn btn-sm btn-outline" (click)="tempApiUrl = defaultUrl">
                  Padrão em Nuvem
                </button>
                <button type="button" class="btn btn-sm btn-outline" (click)="tempApiUrl = 'http://localhost:8080/api'">
                  Localhost:8080
                </button>
              </div>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="testarConexao()" [disabled]="testingConnection()">
              <span *ngIf="!testingConnection()"><i class="bi bi-activity"></i> Testar Conexão</span>
              <span *ngIf="testingConnection()">Testando...</span>
            </button>
            <div class="d-flex gap-2">
              <button type="button" class="btn btn-ghost" (click)="restaurarPadrao()">Restaurar</button>
              <button type="button" class="btn btn-primary" (click)="salvarConfig()">Salvar e Conectar</button>
            </div>
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
      padding: 2.5rem 1rem;
      position: relative;
    }

    .top-bar-controls {
      position: absolute;
      top: 1.25rem;
      right: 1.5rem;
      z-index: 10;
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .status-pill-btn {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      padding: 0.5rem 0.85rem;
      border-radius: var(--radius-full);
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      font-size: 0.8rem;
      font-weight: 600;
      cursor: pointer;
      box-shadow: var(--shadow-sm);
      transition: all 0.2s;
    }

    .status-pill-btn:hover {
      background: var(--bg-surface-elevated);
      border-color: var(--color-primary);
    }

    .status-indicator-dot {
      width: 9px;
      height: 9px;
      border-radius: 50%;
      flex-shrink: 0;
    }

    .dot-green {
      background: #10B981;
      box-shadow: 0 0 8px rgba(16, 185, 129, 0.6);
    }

    .dot-yellow {
      background: #F59E0B;
      box-shadow: 0 0 8px rgba(245, 158, 11, 0.6);
      animation: pulse 1.5s infinite;
    }

    .dot-gray {
      background: #94A3B8;
    }

    @keyframes pulse {
      0% { opacity: 0.4; }
      50% { opacity: 1; }
      100% { opacity: 0.4; }
    }

    .config-mini-icon {
      font-size: 0.75rem;
      opacity: 0.6;
      margin-left: 0.2rem;
    }

    .btn-theme {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 0.85rem;
      border-radius: var(--radius-full);
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      font-size: 0.8rem;
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
      max-width: 980px;
      min-height: 600px;
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
      background: linear-gradient(145deg, #3730A3 0%, #1E1B4B 100%);
      color: #FFFFFF;
      padding: 3rem 2.5rem;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      position: relative;
    }

    .login-left-banner::before {
      content: '';
      position: absolute;
      inset: 0;
      background: radial-gradient(circle at top right, rgba(99, 102, 241, 0.35), transparent 70%);
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
      margin-bottom: 1.5rem;
      color: #FFFFFF;
    }

    .security-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.35rem 0.85rem;
      background: rgba(255, 255, 255, 0.12);
      border-radius: var(--radius-full);
      font-size: 0.7rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      margin-bottom: 1.25rem;
    }

    .welcome-heading {
      font-size: 1.45rem;
      margin-bottom: 0.75rem;
      color: #FFFFFF;
    }

    .welcome-text {
      color: rgba(255, 255, 255, 0.85);
      font-size: 0.9rem;
      line-height: 1.6;
      margin-bottom: 1.5rem;
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
      flex: 1.15;
      padding: 3rem 2.5rem;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }

    .form-header {
      margin-bottom: 1.5rem;
    }

    .auth-tag {
      font-size: 0.75rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      color: var(--color-primary);
      display: block;
    }

    .btn-api-settings {
      background: transparent;
      border: 1px solid var(--border-color);
      padding: 0.3rem 0.65rem;
      border-radius: var(--radius-md);
      font-size: 0.75rem;
      font-weight: 600;
      color: var(--text-muted);
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      transition: all 0.2s;
    }

    .btn-api-settings:hover {
      color: var(--color-primary);
      border-color: var(--color-primary);
      background: var(--bg-surface-elevated);
    }

    .form-header h2 {
      font-size: 1.75rem;
      margin-bottom: 0.25rem;
    }

    .form-header p {
      color: var(--text-muted);
      font-size: 0.875rem;
      margin: 0;
    }

    .alert-info-box {
      display: flex;
      align-items: flex-start;
      gap: 0.65rem;
      padding: 0.75rem 1rem;
      background: rgba(99, 102, 241, 0.1);
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: var(--radius-md);
      color: var(--text-primary);
      font-size: 0.8rem;
      line-height: 1.4;
      margin-bottom: 1.25rem;
    }

    .alert-info-box i {
      color: var(--color-primary);
      font-size: 1.1rem;
      margin-top: 0.1rem;
      flex-shrink: 0;
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

    .forgot-row { text-align: right; margin: -0.25rem 0 1rem; font-size: 0.8rem; }
    .forgot-row a { color: var(--color-primary); font-weight: 600; }

    .btn-submit {
      width: 100%;
      padding: 0.85rem;
      font-size: 0.95rem;
      font-weight: 700;
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
      font-size: 0.825rem;
      margin-bottom: 1.25rem;
    }

    .login-footer-info {
      text-align: center;
      color: var(--text-muted);
      font-size: 0.75rem;
      margin-top: 1.25rem;
    }

    .spinner-border-sm {
      display: inline-block;
      width: 1rem;
      height: 1rem;
      vertical-align: -0.125em;
      border: 0.15em solid currentColor;
      border-right-color: transparent;
      border-radius: 50%;
      animation: spinner-border 0.75s linear infinite;
    }

    @keyframes spinner-border {
      to { transform: rotate(360deg); }
    }

    /* Modal */
    .modal-overlay {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.6);
      backdrop-filter: blur(4px);
      z-index: 1000;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1rem;
    }

    .modal-card {
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      width: 100%;
      max-width: 520px;
      box-shadow: var(--shadow-xl);
      overflow: hidden;
      animation: modalFadeIn 0.2s ease-out;
    }

    @keyframes modalFadeIn {
      from { opacity: 0; transform: scale(0.96); }
      to { opacity: 1; transform: scale(1); }
    }

    .modal-header {
      padding: 1.25rem 1.5rem;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .modal-title {
      font-size: 1.15rem;
    }

    .btn-close-modal {
      background: transparent;
      border: none;
      font-size: 1.5rem;
      color: var(--text-muted);
      cursor: pointer;
    }

    .modal-body {
      padding: 1.5rem;
    }

    .modal-description {
      font-size: 0.85rem;
      color: var(--text-secondary);
      margin-bottom: 1.25rem;
      line-height: 1.5;
    }

    .test-result-box {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.75rem 1rem;
      border-radius: var(--radius-md);
      margin-top: 1rem;
      font-size: 0.85rem;
    }

    .result-success {
      background: var(--color-success-light);
      border: 1px solid rgba(16, 185, 129, 0.3);
      color: var(--color-success);
    }

    .result-error {
      background: var(--color-danger-light);
      border: 1px solid rgba(239, 68, 68, 0.3);
      color: var(--color-danger);
    }

    .text-small {
      font-size: 0.775rem;
      opacity: 0.9;
    }

    .quick-url-presets {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .presets-label {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--text-muted);
    }

    .presets-buttons {
      display: flex;
      gap: 0.4rem;
    }

    .btn-outline {
      background: transparent;
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      padding: 0.25rem 0.6rem;
      font-size: 0.75rem;
      border-radius: var(--radius-sm);
      cursor: pointer;
    }

    .btn-outline:hover {
      border-color: var(--color-primary);
      color: var(--color-primary);
    }

    .modal-footer {
      padding: 1rem 1.5rem;
      background: var(--bg-surface-elevated);
      border-top: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.5rem;
    }

    @media (max-width: 850px) {
      .login-wrapper {
        padding: 1rem 0.65rem;
      }
      .top-bar-controls {
        top: 0.75rem;
        right: 0.75rem;
      }
      .login-card-container {
        flex-direction: column;
        border-radius: var(--radius-lg);
        min-height: auto;
        margin-top: 3.5rem;
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
export class LoginComponent implements OnInit {
  authService = inject(AuthService);
  themeService = inject(ThemeService);
  private router = inject(Router);

  // Sem valores pré-preenchidos: nunca há credencial padrão na tela de login.
  email: string = '';
  password: string = '';
  showPassword = signal<boolean>(false);
  loading = signal<boolean>(false);
  errorMessage = signal<string>('');

  // Status de conexão com o backend
  backendStatus = signal<'online' | 'offline' | 'cold_start' | 'checking'>('checking');
  backendLatency = signal<number | null>(null);

  // Modal de Configuração de API
  showConfigModal = signal<boolean>(false);
  tempApiUrl: string = '';
  defaultUrl: string = getDefaultApiUrl();
  testingConnection = signal<boolean>(false);
  testResult = signal<{ online: boolean; latencyMs?: number; error?: string } | null>(null);

  ngOnInit(): void {
    this.tempApiUrl = getApiBaseUrl();
    this.verificarBackendStatus();
  }

  statusDotClass(): string {
    switch (this.backendStatus()) {
      case 'online': return 'dot-green';
      case 'cold_start': return 'dot-yellow';
      case 'offline': return 'dot-gray';
      default: return 'dot-yellow';
    }
  }

  statusLabel(): string {
    switch (this.backendStatus()) {
      case 'online': 
        return this.backendLatency() ? `Online (${this.backendLatency()}ms)` : 'Online';
      case 'cold_start':
        return 'Servidor Iniciando...';
      case 'offline':
        return 'Servidor Indisponível';
      default:
        return 'Verificando...';
    }
  }

  verificarBackendStatus(): void {
    this.backendStatus.set('checking');
    this.authService.checkBackendHealth().subscribe((res) => {
      if (res.online) {
        this.backendStatus.set('online');
        this.backendLatency.set(res.latencyMs || null);
      } else {
        // Se a URL for do Render (.onrender.com), consideramos que pode estar em cold-start
        if (res.url.includes('onrender.com')) {
          this.backendStatus.set('cold_start');
        } else {
          this.backendStatus.set('offline');
        }
      }
    });
  }

  toggleShowPassword(): void {
    this.showPassword.update((val) => !val);
  }

  onLogin(): void {
    if (!this.email.trim() || !this.password) {
      this.errorMessage.set('Por favor, preencha o e-mail e a senha.');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');

    this.authService.login(this.email, this.password).subscribe({
      next: (res) => {
        this.loading.set(false);
        // O painel administrativo da Nexus (SUPER_ADMIN) ainda não existe nesta etapa:
        // não entra no app das empresas (o backend, de todo modo, negaria os dados).
        if (res.user.role === 'SUPER_ADMIN') {
          this.authService.logout();
          this.errorMessage.set('O painel administrativo da Nexus ainda não está disponível nesta versão.');
          return;
        }
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.loading.set(false);
        this.errorMessage.set(err.message || err.error?.message || 'Usuário ou senha inválidos.');
      }
    });
  }

  abrirModalConfig(): void {
    this.tempApiUrl = getApiBaseUrl();
    this.testResult.set(null);
    this.showConfigModal.set(true);
  }

  fecharModalConfig(): void {
    this.showConfigModal.set(false);
  }

  testarConexao(): void {
    this.testingConnection.set(true);
    this.testResult.set(null);

    // Salva temporariamente para testar
    setApiBaseUrl(this.tempApiUrl);

    this.authService.checkBackendHealth().subscribe({
      next: (res) => {
        this.testingConnection.set(false);
        this.testResult.set(res);
        if (res.online) {
          this.backendStatus.set('online');
          this.backendLatency.set(res.latencyMs || null);
        }
      },
      error: () => {
        this.testingConnection.set(false);
        this.testResult.set({ online: false, error: 'Falha de conexão com a URL informada' });
      }
    });
  }

  salvarConfig(): void {
    setApiBaseUrl(this.tempApiUrl);
    this.fecharModalConfig();
    this.verificarBackendStatus();
  }

  restaurarPadrao(): void {
    resetApiBaseUrl();
    this.tempApiUrl = getDefaultApiUrl();
    this.testResult.set(null);
  }
}
