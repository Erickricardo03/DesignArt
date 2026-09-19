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
        <div class="brand-logo" routerLink="/dashboard" style="cursor: pointer;">
          <div class="logo-badge">DA</div>
          <div class="brand-info">
            <h2>DesignArte</h2>
          </div>
        </div>

        <!-- Botão Fechar no Mobile -->
        <button class="mobile-close-btn" (click)="navService.closeMobileSidebar()">
          <i class="bi bi-x-lg"></i>
        </button>
      </div>

      <!-- Widget de Perfil do Topo (Exatamente como em 16718.jpg) -->
      <div class="user-top-widget">
        <div class="user-avatar-circle" style="display:flex;align-items:center;justify-content:center;font-weight:800;">
          {{ iniciais() }}
        </div>
        <div class="user-top-info">
          <h3 class="user-name-title">{{ nomeUsuario() }}</h3>
          <span class="user-role-badge">{{ rotuloPapel() }}</span>
        </div>
      </div>

      <!-- Navegação Lateral Completa (Exatamente na ordem de 16718.jpg) -->
      <nav class="sidebar-nav">
        <a routerLink="/tarefas" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-card-checklist"></i>
          <span>TAREFAS</span>
        </a>

        <a routerLink="/clientes" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-briefcase"></i>
          <span>CLIENTES</span>
        </a>

        <a routerLink="/servicos" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-currency-dollar"></i>
          <span>SERVIÇOS</span>
        </a>

        <a *ngIf="temAcesso('EQUIPE')" routerLink="/equipe" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-people"></i>
          <span>EQUIPE</span>
        </a>

        <a routerLink="/roteiros" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-book"></i>
          <span>ROTEIROS</span>
        </a>

        <a routerLink="/projetos-concluidos" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-check2-circle"></i>
          <span>PROJETOS CONCLUÍDOS</span>
        </a>

        <a routerLink="/loja-fotos" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-images"></i>
          <span>LOJA DE FOTOS</span>
        </a>

        <a routerLink="/calendario" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-calendar3"></i>
          <span>CALENDÁRIO</span>
        </a>

        <a *ngIf="temAcesso('FINANCEIRO')" routerLink="/financeiro" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-shield-check"></i>
          <span>FINANCEIRO</span>
        </a>

        <a routerLink="/relatorios" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-file-earmark-bar-graph"></i>
          <span>RELATÓRIOS</span>
        </a>

        <a routerLink="/historico" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-clock-history"></i>
          <span>HISTÓRICO</span>
        </a>

        <a *ngIf="temAcesso('CONFIGURACOES')" routerLink="/configuracoes" routerLinkActive="active" class="nav-item" (click)="onNavClick()">
          <i class="bi bi-gear"></i>
          <span>CONFIGURAÇÕES</span>
        </a>

        <!-- Botão Sair do Sistema (16718.jpg) -->
        <button class="nav-item logout-nav-btn" (click)="logout()">
          <i class="bi bi-box-arrow-right text-danger"></i>
          <span class="text-danger">SAIR DO SISTEMA</span>
        </button>
      </nav>
    </aside>

    <!-- Barra Inferior Mobile -->
    <nav class="mobile-bottom-bar" aria-label="Navegação rápida móvel">
      <a routerLink="/tarefas" routerLinkActive="active" class="mobile-nav-btn" (click)="onNavClick()">
        <i class="bi bi-card-checklist"></i>
        <span>Tarefas</span>
      </a>
      <a routerLink="/clientes" routerLinkActive="active" class="mobile-nav-btn" (click)="onNavClick()">
        <i class="bi bi-briefcase"></i>
        <span>Clientes</span>
      </a>
      <a *ngIf="temAcesso('FINANCEIRO')" routerLink="/financeiro" routerLinkActive="active" class="mobile-nav-btn" (click)="onNavClick()">
        <i class="bi bi-currency-dollar"></i>
        <span>Financeiro</span>
      </a>
      <a routerLink="/roteiros" routerLinkActive="active" class="mobile-nav-btn" (click)="onNavClick()">
        <i class="bi bi-book"></i>
        <span>Roteiros</span>
      </a>
      <a routerLink="/projetos-concluidos" routerLinkActive="active" class="mobile-nav-btn" (click)="onNavClick()">
        <i class="bi bi-check2-circle"></i>
        <span>Concluídos</span>
      </a>
    </nav>
  `,
  styles: [`
    :host {
      display: block;
      width: 260px;
      min-width: 260px;
      max-width: 260px;
      flex: 0 0 260px;
      position: sticky;
      top: 0;
      height: 100vh;
      z-index: 100;
    }

    .sidebar {
      width: 260px;
      min-width: 260px;
      height: 100vh;
      background: var(--sidebar-bg);
      border-right: 1px solid var(--border-color);
      display: flex;
      flex-direction: column;
      overflow-y: auto;
    }

    .sidebar-header {
      padding: 1.25rem 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      border-bottom: 1px solid var(--border-color);
    }

    .brand-logo {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      text-decoration: none;
    }

    .logo-badge {
      font-size: 1.3rem;
      font-weight: 900;
      color: #7c3aed;
      letter-spacing: -0.05em;
    }

    .brand-info h2 {
      font-size: 1.15rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
    }

    /* Widget do Usuário (16718.jpg) */
    .user-top-widget {
      padding: 1rem 1.25rem;
      display: flex;
      align-items: center;
      gap: 0.85rem;
      border-bottom: 1px solid var(--border-color);
      background: rgba(124, 58, 237, 0.02);
    }

    .user-avatar-circle {
      width: 44px;
      height: 44px;
      border-radius: 50%;
      overflow: hidden;
      background: #7c3aed;
      flex-shrink: 0;
    }

    .user-avatar-circle img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .user-top-info {
      overflow: hidden;
    }

    .user-name-title {
      font-size: 0.92rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 0.15rem 0;
      white-space: nowrap;
      text-overflow: ellipsis;
      overflow: hidden;
    }

    .user-role-badge {
      font-size: 0.7rem;
      font-weight: 800;
      color: #7c3aed;
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }

    .sidebar-nav {
      padding: 1rem 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.3rem;
      flex: 1;
    }

    .nav-item {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      padding: 0.75rem 1rem;
      border-radius: 8px;
      color: var(--text-secondary);
      text-decoration: none;
      font-weight: 700;
      font-size: 0.82rem;
      letter-spacing: 0.04em;
      transition: all 0.2s ease;
      background: transparent;
      border: none;
      width: 100%;
      text-align: left;
      cursor: pointer;
    }

    .nav-item i {
      font-size: 1.1rem;
      transition: transform 0.2s ease;
    }

    .nav-item:hover {
      background: rgba(124, 58, 237, 0.06);
      color: var(--text-primary);
      transform: translateX(3px);
    }

    .nav-item:hover i {
      transform: scale(1.15);
    }

    .nav-item.active {
      background: #7c3aed;
      color: #ffffff !important;
      font-weight: 800;
      box-shadow: 0 4px 14px rgba(124, 58, 237, 0.35);
      animation: navActivate 0.35s ease;
    }

    @keyframes navActivate {
      from { transform: scale(0.97); }
      to { transform: scale(1); }
    }

    .logout-nav-btn {
      margin-top: 1rem;
      border-top: 1px solid var(--border-color);
      padding-top: 1rem;
    }

    .text-danger {
      color: #ef4444 !important;
    }

    .sidebar-footer {
      padding: 1rem 1.25rem;
      border-top: 1px solid var(--border-color);
    }

    .mobile-close-btn {
      display: none;
      background: none;
      border: none;
      color: var(--text-secondary);
      font-size: 1.2rem;
      cursor: pointer;
    }

    .mobile-bottom-bar {
      display: none;
      position: fixed;
      bottom: 0;
      left: 0;
      right: 0;
      background: var(--card-bg);
      border-top: 1px solid var(--border-color);
      height: 60px;
      z-index: 999;
      justify-content: space-around;
      align-items: center;
      padding: 0 0.5rem;
    }

    .mobile-nav-btn {
      display: flex;
      flex-direction: column;
      align-items: center;
      color: var(--text-secondary);
      text-decoration: none;
      font-size: 0.65rem;
      font-weight: 700;
      gap: 2px;
    }

    .mobile-nav-btn i {
      font-size: 1.15rem;
      transition: transform 0.2s ease;
    }

    .mobile-nav-btn.active {
      color: var(--primary);
    }

    .mobile-nav-btn.active i {
      transform: translateY(-3px) scale(1.1);
    }

    @media (max-width: 900px) {
      :host {
        width: 0;
        min-width: 0;
        max-width: 0;
        flex: 0 0 0;
        position: static;
        height: auto;
      }
      .sidebar {
        position: fixed;
        left: -280px;
        top: 0;
        z-index: 1000;
        transition: left 0.3s ease;
      }
      .sidebar.mobile-open {
        left: 0;
      }
      .mobile-close-btn {
        display: block;
      }
      .sidebar-backdrop {
        position: fixed;
        inset: 0;
        background: rgba(0,0,0,0.6);
        z-index: 999;
      }
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
    this.navService.closeMobileSidebar();
  }

  isAdmin(): boolean {
    return this.authService.currentUser()?.role === 'TENANT_ADMIN';
  }

  nomeUsuario(): string {
    const u = this.authService.currentUser();
    return u?.nomeCompleto || u?.email || '';
  }

  iniciais(): string {
    const partes = this.nomeUsuario().trim().split(/\s+/).filter(Boolean);
    if (!partes.length) return '?';
    const letras = partes.length === 1 ? partes[0].substring(0, 2) : partes[0][0] + partes[partes.length - 1][0];
    return letras.toUpperCase();
  }

  // Rótulo do papel real do usuário autenticado (nada de cargo fixo).
  rotuloPapel(): string {
    switch (this.authService.currentUser()?.role) {
      case 'TENANT_ADMIN': return 'ADMINISTRADOR';
      case 'USER': return 'USUÁRIO';
      case 'SUPER_ADMIN': return 'NEXUS';
      default: return '';
    }
  }

  temAcesso(modulo: 'FINANCEIRO' | 'EQUIPE' | 'CONFIGURACOES'): boolean {
    // TENANT_ADMIN: implícito. USER: só as atribuídas. (Conveniência de menu; o backend decide.)
    const u = this.authService.currentUser();
    return this.isAdmin() || (u?.role === 'USER' && !!u.permissoes?.includes(modulo));
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
