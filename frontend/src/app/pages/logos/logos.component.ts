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
              <label class="form-label">ARQUIVO DA LOGO (ALTA RESOLUÇÃO)</label>
              <div class="custom-file-upload-box" (click)="logoFileInput.click()">
                <input 
                  type="file" 
                  #logoFileInput 
                  (change)="onLogoFileSelected($event)" 
                  accept="image/*,.svg,.png,.jpg,.jpeg,.webp,.gif,.bmp,.ai,.eps,.pdf,.ico,.tiff,.heic" 
                  style="display: none" 
                />
                <div *ngIf="!formLogo.arquivoUrlOuBase64" class="upload-placeholder">
                  <i class="bi bi-cloud-arrow-up text-primary" style="font-size: 1.8rem;"></i>
                  <span class="upload-title">Selecionar arquivo de logo no computador</span>
                  <small class="upload-sub">Formatos aceitos: SVG, PNG, JPG, WEBP, GIF, AI, etc.</small>
                </div>
                <div *ngIf="formLogo.arquivoUrlOuBase64" class="upload-preview-container" (click)="$event.stopPropagation()">
                  <img [src]="formLogo.arquivoUrlOuBase64" alt="Logo preview" class="preview-logo-thumb" />
                  <div class="preview-meta">
                    <span class="preview-title"><i class="bi bi-check-circle-fill text-success"></i> Arquivo carregado</span>
                    <div class="preview-actions">
                      <button type="button" class="btn-action-small btn-trocar" (click)="logoFileInput.click()">
                        <i class="bi bi-arrow-repeat"></i> Trocar
                      </button>
                      <button type="button" class="btn-action-small btn-remover" (click)="formLogo.arquivoUrlOuBase64 = ''">
                        <i class="bi bi-trash"></i> Remover
                      </button>
                    </div>
                  </div>
                </div>
              </div>
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

    /* Custom File Upload Box */
    .custom-file-upload-box {
      border: 2px dashed var(--border-color);
      border-radius: 12px;
      padding: 1.25rem;
      background: rgba(124, 58, 237, 0.02);
      cursor: pointer;
      transition: all 0.2s ease;
      text-align: center;
    }
    .custom-file-upload-box:hover {
      border-color: var(--color-primary);
      background: rgba(124, 58, 237, 0.05);
    }
    .upload-placeholder {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.35rem;
    }
    .upload-title {
      font-size: 0.88rem;
      font-weight: 700;
      color: var(--text-primary);
    }
    .upload-sub {
      font-size: 0.75rem;
      color: var(--text-secondary);
    }
    .upload-preview-container {
      display: flex;
      align-items: center;
      gap: 1.25rem;
      text-align: left;
    }
    .preview-logo-thumb {
      width: 72px;
      height: 72px;
      object-fit: contain;
      background: #1a1a2e;
      border-radius: 8px;
      border: 2px solid var(--color-primary);
      padding: 4px;
      box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
    }
    .preview-meta {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .preview-title {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text-primary);
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .preview-actions {
      display: flex;
      gap: 0.5rem;
    }
    .btn-action-small {
      border: none;
      padding: 0.35rem 0.75rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
    }
    .btn-trocar {
      background: #ede9fe;
      color: #6d28d9;
    }
    .btn-remover {
      background: #fee2e2;
      color: #b91c1c;
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
      tamanho: '2.5 MB - Alta Resolução',
      arquivoUrlOuBase64: ''
    };
    this.modalUploadAberto.set(true);
  }

  fecharModalUpload(): void {
    this.modalUploadAberto.set(false);
  }

  onLogoFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const reader = new FileReader();
      const format = file.name.split('.').pop()?.toUpperCase() || 'PNG';
      const sizeMB = (file.size / (1024 * 1024)).toFixed(1) + ' MB';
      
      this.formLogo.formato = format;
      this.formLogo.tamanho = sizeMB;

      reader.onload = (e: ProgressEvent<FileReader>) => {
        this.formLogo.arquivoUrlOuBase64 = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    }
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
