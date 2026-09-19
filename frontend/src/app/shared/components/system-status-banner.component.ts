import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService } from '../../core/services/api.service';

/**
 * Aviso global exibido quando uma chamada à API falha em produção.
 * Nunca substitui o dado por algo fictício — apenas informa o problema e
 * oferece uma forma simples de tentar de novo.
 */
@Component({
  selector: 'app-system-status-banner',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="status-banner" *ngIf="api.connectionError()" role="alert">
      <i class="bi bi-wifi-off"></i>
      <span>Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.</span>
      <button type="button" (click)="tentarNovamente()">
        <i class="bi bi-arrow-clockwise"></i> Tentar novamente
      </button>
    </div>
  `,
  styles: [`
    .status-banner {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      z-index: 9999;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.75rem;
      flex-wrap: wrap;
      padding: 0.65rem 1rem;
      background: #DC2626;
      color: #fff;
      font-size: 0.85rem;
      font-weight: 600;
      text-align: center;
    }
    .status-banner button {
      background: rgba(255, 255, 255, 0.18);
      border: 1px solid rgba(255, 255, 255, 0.4);
      color: #fff;
      padding: 0.3rem 0.75rem;
      border-radius: 6px;
      font-weight: 700;
      font-size: 0.8rem;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }
    .status-banner button:hover {
      background: rgba(255, 255, 255, 0.28);
    }
  `],
})
export class SystemStatusBannerComponent {
  api = inject(ApiService);

  tentarNovamente(): void {
    // Recarregar a página é a forma mais simples e confiável de "tentar de
    // novo" hoje, já que várias telas puxam dados independentes via execute().
    window.location.reload();
  }
}
