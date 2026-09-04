import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../core/services/theme.service';
import { NavigationService } from '../../core/services/navigation.service';

@Component({
  selector: 'app-sidebar',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <!-- Backdrop overlay para Mobile & Tablet -->
    <div 
      class="sidebar-backdrop" 
      *ngIf="navService.mobileSidebarOpen()" 
      (click)="navService.closeMobileSidebar()"
    ></div>

    <aside class="sidebar" [class.mobile-open]="navService.mobileSidebarOpen()">
      <div class="sidebar-header">
        <div class="brand-logo">
          <div class="logo-badge">DA</div>
          <div class="brand-info">
            <h2>Design Arte</h2>
            <span>Agência Criativa & Operacional</span>
          </div>
        </div>

        <!-- Botão Fechar no Mobile -->
        <button class="mobile-close-btn" (click)="navService.closeMobileSidebar()">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>

      <nav class="sidebar-nav">
        <div class="nav-section-title">PRINCIPAL</div>
        <a routerLink="/dashboard" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-grid-1x2-fill"></i>
          <span>Dashboard</span>
        </a>
        <a routerLink="/tarefas" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-check2-square"></i>
          <span>Tarefas & Demandas</span>
        </a>
        <a routerLink="/roteiros" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-film"></i>
          <span>Roteiros & Set</span>
        </a>

        <div class="nav-section-title">PRODUÇÃO & ASSETS</div>
        <a routerLink="/logos" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-images"></i>
          <span>Repositório de Logos</span>
        </a>
        <a routerLink="/eventos" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-camera-reels-fill"></i>
          <span>Cobertura de Eventos</span>
        </a>

        <div class="nav-section-title">GESTÃO & CONTROLE</div>
        <a routerLink="/financeiro" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-cash-stack"></i>
          <span>Financeiro & Vendas</span>
        </a>
        <a routerLink="/relatorios" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-file-earmark-bar-graph-fill"></i>
          <span>Relatórios Mensais</span>
        </a>
        <a routerLink="/" target="_blank" class="nav-item">
          <i class="bi bi-globe2"></i>
          <span>Página Institucional</span>
          <i class="bi bi-box-arrow-up-right external-icon"></i>
        </a>
      </nav>

      <div class="sidebar-footer">
        <div class="theme-switch-container">
          <button class="theme-toggle-btn" (click)="themeService.toggleTheme()" title="Alternar Modo Claro / Escuro">
            <i class="bi" [ngClass]="themeService.isDarkMode() ? 'bi-moon-stars-fill' : 'bi-sun-fill'"></i>
            <span>{{ themeService.isDarkMode() ? 'Modo Escuro' : 'Modo Claro' }}</span>
          </button>
        </div>

        <div class="user-profile-widget" *ngIf="authService.currentUser() as user">
          <div class="user-avatar">{{ user.username.charAt(0).toUpperCase() }}</div>
          <div class="user-details">
            <p class="user-name">{{ user.nomeCompleto || user.username }}</p>
            <span class="user-role">{{ user.cargo || user.role }}</span>
          </div>
          <button class="logout-btn" (click)="logout()" title="Sair do sistema">
            <i class="bi bi-box-arrow-right"></i>
          </button>
        </div>
      </div>
    </aside>

    <!-- Barra Inferior de Navegação Rápida para Celular (Mobile Navigation Bar) -->
    <nav class="mobile-bottom-bar" aria-label="Navegação rápida móvel">
      <a routerLink="/dashboard" routerLinkActive="active" class="mobile-nav-btn" (click)="onNavClick()">
        <i class="bi bi-grid-1x2-fill"></i>
        <span>Dashboard</span>
      </a>
      <a routerLink="/tarefas" routerLinkActive="active" class="mobile-nav-btn" (click)="onNavClick()">
        <i class="bi bi-check2-square"></i>
        <span>Tarefas</span>
      </a>
      <a routerLink="/roteiros" routerLinkActive="active" class="mobile-nav-btn" (click)="onNavClick()">
        <i class="bi bi-film"></i>
        <span>Roteiros</span>
      </a>
      <a routerLink="/financeiro" routerLinkActive="active" class="mobile-nav-btn" (click)="onNavClick()">
        <i class="bi bi-cash-stack"></i>
        <span>Financeiro</span>
      </a>
      <button type="button" class="mobile-nav-btn btn-more" (click)="navService.toggleMobileSidebar()">
        <i class="bi bi-grid-fill"></i>
        <span>Mais</span>
      </button>
    </nav>
  `,
  styles: [`
    .sidebar-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.65);
      backdrop-filter: blur(4px);
      -webkit-backdrop-filter: blur(4px);
      z-index: 998;
      animation: fadeIn 0.2s ease-out;
    }

    .sidebar {
      width: 280px;
      height: 100vh;
      background: var(--bg-surface);
      border-right: 1px solid var(--border-color);
      display: flex;
      flex-direction: column;
      position: sticky;
      top: 0;
      z-index: 999;
      transition: transform 0.28s cubic-bezier(0.16, 1, 0.3, 1);
      will-change: transform;
      contain: layout style paint;
    }

    .sidebar-header {
      padding: 1.25rem 1.25rem;
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .brand-logo {
      display: flex;
      align-items: center;
      gap: 0.85rem;
    }

    .logo-badge {
      width: 42px;
      height: 42px;
      border-radius: var(--radius-md);
      background: var(--color-primary-gradient);
      color: #FFFFFF;
      font-family: var(--font-display);
      font-weight: 900;
      font-size: 1.2rem;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.35);
    }

    .brand-info h2 {
      font-size: 1.15rem;
      margin: 0;
      line-height: 1.2;
    }

    .brand-info span {
      font-size: 0.725rem;
      color: var(--text-muted);
      font-weight: 500;
    }

    .mobile-close-btn {
      display: none;
      background: transparent;
      border: none;
      color: var(--text-secondary);
      font-size: 1.25rem;
      cursor: pointer;
      padding: 0.4rem;
      border-radius: var(--radius-sm);
    }

    .sidebar-nav {
      flex: 1;
      padding: 1rem 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
      overflow-y: auto;
      -webkit-overflow-scrolling: touch;
    }

    .nav-section-title {
      font-size: 0.685rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      color: var(--text-muted);
      padding: 0.75rem 0.75rem 0.25rem;
      text-transform: uppercase;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      padding: 0.7rem 0.85rem;
      border-radius: var(--radius-md);
      color: var(--text-secondary);
      font-size: 0.875rem;
      font-weight: 600;
      transition: background 0.15s, color 0.15s;
      min-height: 44px; /* Touch target otimizado */
    }

    .nav-item i {
      font-size: 1.15rem;
      color: var(--text-muted);
      transition: color 0.15s;
    }

    .nav-item .external-icon {
      margin-left: auto;
      font-size: 0.8rem;
    }

    .nav-item:hover, .nav-item:active {
      background: var(--bg-surface-elevated);
      color: var(--text-primary);
    }

    .nav-item.active {
      background: var(--color-primary-light);
      color: var(--color-primary);
      font-weight: 700;
    }

    .nav-item.active i {
      color: var(--color-primary);
    }

    .sidebar-footer {
      padding: 1rem 0.85rem;
      border-top: 1px solid var(--border-color);
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }

    .theme-switch-container {
      width: 100%;
    }

    .theme-toggle-btn {
      width: 100%;
      min-height: 42px;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.6rem;
      padding: 0.55rem;
      border-radius: var(--radius-md);
      border: 1px solid var(--border-color);
      background: var(--bg-surface-elevated);
      color: var(--text-secondary);
      font-size: 0.825rem;
      font-weight: 600;
      cursor: pointer;
      transition: background 0.15s, color 0.15s;
    }

    .theme-toggle-btn:hover {
      background: var(--bg-surface-hover);
      color: var(--text-primary);
    }

    .user-profile-widget {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.6rem 0.75rem;
      background: var(--bg-surface-elevated);
      border-radius: var(--radius-md);
      border: 1px solid var(--border-color);
    }

    .user-avatar {
      width: 36px;
      height: 36px;
      border-radius: var(--radius-full);
      background: var(--color-primary-gradient);
      color: white;
      font-weight: 700;
      font-size: 0.9rem;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .user-details {
      flex: 1;
      min-width: 0;
    }

    .user-name {
      font-size: 0.825rem;
      font-weight: 700;
      margin: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .user-role {
      font-size: 0.7rem;
      color: var(--text-muted);
      display: block;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .logout-btn {
      background: transparent;
      border: none;
      color: var(--text-muted);
      cursor: pointer;
      font-size: 1.25rem;
      padding: 0.4rem;
      min-width: 40px;
      min-height: 40px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: var(--radius-sm);
      transition: color 0.15s, background 0.15s;
    }

    .logout-btn:hover {
      color: var(--color-danger);
      background: var(--color-danger-light);
    }

    /* Regras de Responsividade Mobile & Tablet */
    @media (max-width: 992px) {
      .sidebar {
        position: fixed;
        top: 0;
        left: 0;
        width: 290px;
        max-width: 85vw;
        height: 100vh;
        height: 100dvh;
        transform: translateX(-105%);
        visibility: hidden;
        pointer-events: none;
        box-shadow: none;
        z-index: 1000;
        transition: transform 0.28s cubic-bezier(0.16, 1, 0.3, 1), visibility 0.28s ease;
      }

      .sidebar.mobile-open {
        transform: translateX(0);
        visibility: visible;
        pointer-events: auto;
        box-shadow: 0 0 40px rgba(0, 0, 0, 0.5);
      }

      .mobile-close-btn {
        display: block;
      }
    }

    /* Barra Inferior para Celulares (<= 768px) */
    .mobile-bottom-bar {
      display: none;
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      width: 100%;
      max-width: 100vw;
      height: 60px;
      padding-bottom: env(safe-area-inset-bottom, 0);
      background: var(--bg-surface);
      border-top: 1px solid var(--border-color);
      z-index: 950;
      box-shadow: 0 -4px 16px rgba(0, 0, 0, 0.1);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      justify-content: space-around;
      align-items: center;
      touch-action: manipulation;
      box-sizing: border-box;
      overflow: hidden;
    }

    .mobile-nav-btn {
      flex: 1;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 2px;
      height: 100%;
      background: transparent;
      border: none;
      color: var(--text-muted);
      font-size: 0.675rem;
      font-weight: 700;
      cursor: pointer;
      text-decoration: none;
      transition: color 0.15s ease, transform 0.15s ease;
      user-select: none;
      -webkit-tap-highlight-color: transparent;
      min-width: 0;
    }

    .mobile-nav-btn span {
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 100%;
      font-size: 0.65rem;
    }

    .mobile-nav-btn i {
      font-size: 1.2rem;
      line-height: 1;
      transition: transform 0.15s ease, color 0.15s ease;
    }

    .mobile-nav-btn.active {
      color: var(--color-primary);
    }

    .mobile-nav-btn.active i {
      transform: scale(1.12);
      color: var(--color-primary);
    }

    .mobile-nav-btn:active {
      transform: scale(0.92);
    }

    .btn-more {
      color: var(--text-secondary);
    }

    @media (max-width: 768px) {
      .mobile-bottom-bar {
        display: flex;
      }
    }
  `]
})
export class SidebarComponent {
  authService = inject(AuthService);
  themeService = inject(ThemeService);
  navService = inject(NavigationService);
  private router = inject(Router);

  onNavClick(): void {
    if (window.innerWidth <= 992) {
      this.navService.closeMobileSidebar();
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
