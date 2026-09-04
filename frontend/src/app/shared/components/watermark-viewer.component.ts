import { Component, Input, HostListener, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-watermark-viewer',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="watermark-container" 
         (contextmenu)="preventAction($event)"
         (dragstart)="preventAction($event)"
         [class.blurred]="isBlurred()">

      <!-- Imagem Base Protegida -->
      <img [src]="imageUrl" [alt]="altText" class="protected-img" draggable="false" />

      <!-- Marca D'água Forte Diagonal Repetida -->
      <div class="watermark-grid-overlay">
        <div class="watermark-row" *ngFor="let r of [1,2,3,4,5]">
          <span class="watermark-item" *ngFor="let c of [1,2,3,4]">
            {{ watermarkText }}
          </span>
        </div>
      </div>

      <!-- Overlay de Proteção contra Captura / Perda de Foco -->
      <div class="anti-capture-overlay" *ngIf="isBlurred()">
        <i class="bi bi-shield-lock-fill"></i>
        <span>CONTEÚDO PROTEGIDO • DESIGN ARTE</span>
        <small>Capturas e downloads desabilitados para proteção de direitos autorais</small>
      </div>
    </div>
  `,
  styles: [`
    .watermark-container {
      position: relative;
      overflow: hidden;
      border-radius: var(--radius-md);
      background: #000000;
      user-select: none;
      -webkit-user-select: none;
      -moz-user-select: none;
      -ms-user-select: none;
      display: inline-block;
      width: 100%;
    }

    .protected-img {
      display: block;
      width: 100%;
      height: 100%;
      object-fit: cover;
      pointer-events: none;
      transition: filter 0.2s ease;
    }

    .watermark-container.blurred .protected-img {
      filter: blur(25px) brightness(0.2);
    }

    .watermark-grid-overlay {
      position: absolute;
      inset: -50%;
      width: 200%;
      height: 200%;
      pointer-events: none;
      display: flex;
      flex-direction: column;
      justify-content: space-around;
      transform: rotate(-30deg);
      z-index: 5;
    }

    .watermark-row {
      display: flex;
      justify-content: space-around;
      gap: 2rem;
      white-space: nowrap;
    }

    .watermark-item {
      font-family: var(--font-display);
      font-size: 1.15rem;
      font-weight: 900;
      text-transform: uppercase;
      letter-spacing: 0.15em;
      color: rgba(255, 255, 255, 0.65);
      text-shadow: 
        0 0 8px rgba(0, 0, 0, 0.9),
        0 0 2px rgba(0, 0, 0, 0.9),
        1px 1px 2px rgba(0, 0, 0, 0.8);
      border: 1px dashed rgba(255, 255, 255, 0.3);
      padding: 0.25rem 0.75rem;
      border-radius: 4px;
    }

    .anti-capture-overlay {
      position: absolute;
      inset: 0;
      background: rgba(11, 15, 25, 0.95);
      z-index: 20;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      color: white;
      text-align: center;
      padding: 1rem;
    }

    .anti-capture-overlay i {
      font-size: 2.5rem;
      color: var(--color-danger);
    }

    .anti-capture-overlay span {
      font-weight: 800;
      letter-spacing: 0.05em;
      font-size: 1rem;
    }

    .anti-capture-overlay small {
      color: var(--text-muted);
      font-size: 0.75rem;
    }
  `]
})
export class WatermarkViewerComponent {
  @Input() imageUrl: string = '';
  @Input() altText: string = 'Foto do Evento';
  @Input() watermarkText: string = 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE';

  isBlurred = signal<boolean>(false);

  @HostListener('contextmenu', ['$event'])
  preventAction(event: Event): void {
    event.preventDefault();
  }

  @HostListener('window:keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    // Bloqueia PrintScreen, F12, Ctrl+P, Ctrl+S
    if (
      event.key === 'PrintScreen' ||
      event.key === 'F12' ||
      (event.ctrlKey && (event.key === 'p' || event.key === 's' || event.key === 'u'))
    ) {
      this.isBlurred.set(true);
      setTimeout(() => this.isBlurred.set(false), 3000);
    }
  }

  @HostListener('window:blur')
  onWindowBlur(): void {
    this.isBlurred.set(true);
  }

  @HostListener('window:focus')
  onWindowFocus(): void {
    this.isBlurred.set(false);
  }
}
