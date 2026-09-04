import { Component, Input, Output, EventEmitter, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/services/auth.service';
import { NavigationService } from '../../core/services/navigation.service';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [CommonModule],
  template: `
    <header class="app-header">
      <div class="header-left">
        <!-- Botão Menu Hamburguer (Mobile / Tablet) -->
        <button 
          class="mobile-menu-btn" 
          (click)="navService.toggleMobileSidebar()" 
          title="Abrir Menu Lateral"
        >
          <i class="bi bi-list"></i>
        </button>

        <div class="titles-box">
          <h1 class="page-title">{{ title }}</h1>
          <p class="page-subtitle" *ngIf="subtitle">{{ subtitle }}</p>
        </div>
      </div>

      <div class="header-right">
        <button class="btn btn-secondary btn-icon-mobile" (click)="refreshAction.emit()" title="Atualizar dados">
          <i class="bi bi-arrow-clockwise"></i>
          <span class="btn-text">Atualizar</span>
        </button>

        <button *ngIf="showNewTaskButton" class="btn btn-primary" (click)="newTaskAction.emit()">
          <i class="bi bi-plus-lg"></i>
          <span>Nova Tarefa</span>
        </button>
      </div>
    </header>
  `,
  styles: [`
    .app-header {
      padding: 1.25rem 2rem;
      background: var(--bg-surface);
      border-bottom: 1px solid var(--border-color);
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      position: sticky;
      top: 0;
      z-index: 90;
      backdrop-filter: blur(8px);
      -webkit-backdrop-filter: blur(8px);
      width: 100%;
      max-width: 100vw;
      box-sizing: border-box;
      overflow: hidden;
    }

    .header-left {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      min-width: 0;
      flex: 1;
    }

    .mobile-menu-btn {
      display: none;
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      width: 40px;
      height: 40px;
      border-radius: var(--radius-md);
      font-size: 1.4rem;
      cursor: pointer;
      align-items: center;
      justify-content: center;
      transition: background 0.15s;
      flex-shrink: 0;
    }

    .mobile-menu-btn:hover, .mobile-menu-btn:active {
      background: var(--bg-surface-hover);
    }

    .titles-box {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
      min-width: 0;
      flex: 1;
      overflow: hidden;
    }

    .page-title {
      font-size: 1.35rem;
      margin: 0;
      color: var(--text-primary);
      line-height: 1.2;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 100%;
    }

    .page-subtitle {
      font-size: 0.8rem;
      color: var(--text-muted);
      margin: 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      flex-shrink: 0;
    }

    @media (max-width: 992px) {
      .app-header {
        padding: 0.85rem 1rem;
      }
      .mobile-menu-btn {
        display: inline-flex;
      }
      .page-title {
        font-size: 1.1rem;
      }
      .page-subtitle {
        display: none;
      }
    }

    @media (max-width: 576px) {
      .app-header {
        padding: 0.75rem 0.65rem;
        gap: 0.5rem;
      }
      .btn-text {
        display: none;
      }
      .header-right {
        gap: 0.35rem;
      }
      .header-right .btn {
        padding: 0.5rem 0.65rem;
        font-size: 0.8rem;
      }
    }
  `]
})
export class HeaderComponent {
  @Input() title: string = '';
  @Input() subtitle: string = '';
  @Input() showNewTaskButton: boolean = false;

  @Output() newTaskAction = new EventEmitter<void>();
  @Output() refreshAction = new EventEmitter<void>();

  authService = inject(AuthService);
  navService = inject(NavigationService);
}
