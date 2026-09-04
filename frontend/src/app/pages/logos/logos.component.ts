import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { ApiService } from '../../core/services/api.service';
import { LogoCliente } from '../../core/models';

@Component({
  selector: 'app-logos',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, HeaderComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>

      <main class="main-content">
        <app-header 
          title="Repositório de Logos dos Clientes" 
          subtitle="Área centralizada de assets para editores de vídeo e designers baixarem as logos em alta resolução"
          (refreshAction)="carregarLogos()"
        ></app-header>

        <div class="page-body">
          <!-- Banner Superior & Filtro de Busca -->
          <div class="card mb-4">
            <div class="header-action-row">
              <div class="input-with-icon flex-1">
                <input 
                  type="text" 
                  class="form-control" 
                  placeholder="Pesquisar logos por marca ou cliente (Ex: JM Moda Fitness, Ateliê da Ysa)..." 
                  [(ngModel)]="filtroCliente" 
                  (input)="carregarLogos()"
                />
              </div>
              <button class="btn btn-primary" (click)="abrirModalUpload()">
                <i class="bi bi-cloud-arrow-up-fill"></i>
                <span>Adicionar Nova Logo</span>
              </button>
            </div>
          </div>

          <!-- Grid de Logos -->
          <div class="logos-grid">
            <div class="card logo-card" *ngFor="let logo of logos()">
              <!-- Área de Pré-visualização com Checkerboard (fundo transparente para logos PNG/SVG) -->
              <div class="logo-preview-box">
                <img [src]="logo.arquivoUrlOuBase64" [alt]="logo.clienteNome" class="logo-img" />
              </div>

              <div class="logo-card-info">
                <div class="logo-meta-row">
                  <span class="badge badge-em-revisao">{{ logo.formato || 'PNG' }}</span>
                  <span class="logo-size" *ngIf="logo.tamanho">{{ logo.tamanho }}</span>
                </div>

                <h3 class="logo-client-name">{{ logo.clienteNome }}</h3>
                <span class="logo-variant-tag">{{ logo.variante || 'Logo Principal' }}</span>

                <div class="color-palette-sample" *ngIf="logo.corPrimaria">
                  <span class="color-dot" [style.background-color]="logo.corPrimaria"></span>
                  <span class="color-hex">{{ logo.corPrimaria }}</span>
                </div>
              </div>

              <div class="logo-card-actions">
                <a [href]="logo.arquivoUrlOuBase64" target="_blank" download class="btn btn-primary btn-sm flex-1">
                  <i class="bi bi-download"></i>
                  <span>Baixar Logo</span>
                </a>
                <button class="btn btn-secondary btn-sm" (click)="copiarLink(logo.arquivoUrlOuBase64)" title="Copiar Link Direto">
                  <i class="bi bi-link-45deg"></i>
                </button>
                <button class="btn btn-outline-danger btn-sm" (click)="excluirLogo(logo)" title="Excluir">
                  <i class="bi bi-trash"></i>
                </button>
              </div>
            </div>

            <div *ngIf="logos().length === 0" class="empty-state-card card">
              <i class="bi bi-images text-muted" style="font-size: 3rem;"></i>
              <p class="text-muted mt-2">Nenhuma logo encontrada. Adicione uma nova logo para os editores.</p>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- MODAL DE UPLOAD / CADASTRO DE LOGO -->
    <div class="modal-backdrop" *ngIf="modalUploadAberto()" (click)="fecharModalUpload()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3>Adicionar Logo do Cliente</h3>
          <button class="btn-ghost btn-icon" (click)="fecharModalUpload()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <form (ngSubmit)="salvarLogo()">
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">NOME DO CLIENTE / MARCA</label>
              <input type="text" class="form-control" [(ngModel)]="formLogo.clienteNome" name="clienteNome" required placeholder="Ex: JM MODA FITNESS" />
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">VARIANTE DA LOGO</label>
                <input type="text" class="form-control" [(ngModel)]="formLogo.variante" name="variante" placeholder="Ex: Principal, Negativa Branca, Símbolo" />
              </div>

              <div class="form-group">
                <label class="form-label">FORMATO</label>
                <select class="form-select" [(ngModel)]="formLogo.formato" name="formato">
                  <option value="PNG">PNG (Transparente)</option>
                  <option value="SVG">SVG (Vetor)</option>
                  <option value="AI">AI (Illustrator)</option>
                  <option value="JPG">JPG / JPEG</option>
                </select>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">COR PRIMÁRIA DA MARCA (HEX)</label>
              <input type="text" class="form-control" [(ngModel)]="formLogo.corPrimaria" name="corPrimaria" placeholder="Ex: #E11D48" />
            </div>

            <div class="form-group">
              <label class="form-label">URL DA IMAGEM / ARQUIVO EM ALTA</label>
              <input type="text" class="form-control" [(ngModel)]="formLogo.arquivoUrlOuBase64" name="arquivoUrlOuBase64" required placeholder="https://... ou cole o link do arquivo" />
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="fecharModalUpload()">Cancelar</button>
            <button type="submit" class="btn btn-primary">Salvar no Repositório</button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .header-action-row {
      display: flex;
      gap: 1rem;
      align-items: center;
      width: 100%;
      max-width: 100%;
      min-width: 0;
      flex-wrap: wrap;
    }

    .flex-1 { flex: 1; min-width: 0; }

    .logos-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
      gap: 1.25rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .logo-card {
      display: flex;
      flex-direction: column;
      padding: 1.25rem;
      border: 1px solid var(--border-color);
      transition: all 0.2s ease;
      min-width: 0;
      max-width: 100%;
      box-sizing: border-box;
      overflow: hidden;
    }

    .logo-card:hover {
      border-color: var(--color-primary);
      box-shadow: var(--shadow-md);
      transform: translateY(-2px);
    }

    .logo-preview-box {
      width: 100%;
      height: 170px;
      border-radius: var(--radius-md);
      background-color: #1a1a2e;
      background-image: linear-gradient(45deg, #131320 25%, transparent 25%), 
                        linear-gradient(-45deg, #131320 25%, transparent 25%), 
                        linear-gradient(45deg, transparent 75%, #131320 75%), 
                        linear-gradient(-45deg, transparent 75%, #131320 75%);
      background-size: 16px 16px;
      background-position: 0 0, 0 8px, 8px -8px, -8px 0px;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
      margin-bottom: 1.25rem;
      border: 1px solid var(--border-subtle);
      box-sizing: border-box;
    }

    .logo-img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
      filter: drop-shadow(0 4px 6px rgba(0,0,0,0.3));
    }

    .logo-card-info {
      flex: 1;
      margin-bottom: 1.25rem;
      min-width: 0;
    }

    .logo-meta-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 0.5rem;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .logo-size {
      font-size: 0.75rem;
      color: var(--text-muted);
    }

    .logo-client-name {
      font-size: 1.1rem;
      margin: 0 0 0.2rem 0;
      word-break: break-word;
    }

    .logo-variant-tag {
      font-size: 0.8rem;
      color: var(--text-secondary);
      display: block;
      margin-bottom: 0.5rem;
      word-break: break-word;
    }

    .color-palette-sample {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-size: 0.75rem;
      color: var(--text-muted);
    }

    .color-dot {
      width: 14px;
      height: 14px;
      border-radius: var(--radius-full);
      border: 1px solid rgba(255,255,255,0.2);
    }

    .logo-card-actions {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      padding-top: 1rem;
      border-top: 1px solid var(--border-color);
      flex-wrap: wrap;
    }

    .empty-state-card {
      grid-column: 1 / -1;
      text-align: center;
      padding: 3rem 1rem;
    }

    .form-row-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    @media (max-width: 768px) {
      .header-action-row { flex-direction: column; align-items: stretch; gap: 0.75rem; }
      .header-action-row .btn { width: 100%; }
      .logos-grid { grid-template-columns: 1fr; }
      .form-row-2 { grid-template-columns: 1fr; }
    }
  `]
})
export class LogosComponent implements OnInit {
  private apiService = inject(ApiService);

  logos = signal<LogoCliente[]>([]);
  filtroCliente: string = '';
  modalUploadAberto = signal<boolean>(false);

  formLogo: Partial<LogoCliente> = {};

  ngOnInit(): void {
    this.carregarLogos();
  }

  carregarLogos(): void {
    this.apiService.getLogos(this.filtroCliente).subscribe({
      next: (res) => this.logos.set(res),
      error: (err) => console.error('Erro ao carregar logos:', err)
    });
  }

  abrirModalUpload(): void {
    this.formLogo = {
      formato: 'PNG',
      variante: 'Logo Principal Colorida',
      tamanho: '2.5 MB - Alta Resolução'
    };
    this.modalUploadAberto.set(true);
  }

  fecharModalUpload(): void {
    this.modalUploadAberto.set(false);
  }

  salvarLogo(): void {
    this.apiService.createLogo(this.formLogo).subscribe({
      next: () => {
        this.fecharModalUpload();
        this.carregarLogos();
      }
    });
  }

  copiarLink(url: string): void {
    navigator.clipboard.writeText(url);
    alert('Link da logo copiado para a área de transferência!');
  }

  excluirLogo(logo: LogoCliente): void {
    if (!logo.id || !confirm(`Deseja excluir a logo de ${logo.clienteNome}?`)) return;
    this.apiService.deleteLogo(logo.id).subscribe({
      next: () => this.carregarLogos()
    });
  }
}
