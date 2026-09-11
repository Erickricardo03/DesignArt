import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { WatermarkViewerComponent } from '../../shared/components/watermark-viewer.component';
import { ApiService } from '../../core/services/api.service';
import { Evento, FotoEvento } from '../../core/models';

@Component({
  selector: 'app-eventos',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, HeaderComponent, WatermarkViewerComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>

      <main class="main-content">
        <app-header 
          title="Cobertura de Eventos & Galeria Protegida" 
          subtitle="Registro de eventos fotográficos com tecnologia anti-print e marca d'água de proteção autoral"
          (refreshAction)="carregarEventos()"
        ></app-header>

        <div class="page-body">
          <!-- Banner Superior & Ações -->
          <div class="card mb-4">
            <div class="events-top-row">
              <div class="security-info-box">
                <i class="bi bi-shield-shaded text-teal"></i>
                <div>
                  <strong>PROTEÇÃO DE FOTOS ATIVA:</strong>
                  <span>Marca d'água reforçada e proteção contra printscreens e downloads desautorizados no catálogo.</span>
                </div>
              </div>
              <button class="btn btn-primary" (click)="abrirModalNovoEvento()">
                <i class="bi bi-calendar-plus-fill"></i>
                <span>Registrar Novo Evento</span>
              </button>
            </div>
          </div>

          <!-- Listagem dos Eventos -->
          <div class="events-list-container">
            <div class="card event-master-card" *ngFor="let evento of eventos()">
              <!-- Header do Evento (Página 6 do PDF) -->
              <div class="event-hero-grid">
                <div class="event-banner-box" *ngIf="evento.bannerUrl">
                  <img [src]="evento.bannerUrl" [alt]="evento.nome" class="event-banner-img" />
                </div>

                <div class="event-details-content">
                  <div class="event-badge-row">
                    <span class="badge badge-em-dev">{{ evento.status || 'PUBLICADO' }}</span>
                    <span class="badge badge-prioridade-media">COBERTURA OFICIAL</span>
                  </div>

                  <h2 class="event-title">{{ evento.nome }}</h2>

                  <div class="event-meta-info-grid">
                    <div class="meta-item">
                      <i class="bi bi-geo-alt-fill text-danger"></i>
                      <span>{{ evento.localizacao }}</span>
                    </div>

                    <div class="meta-item">
                      <i class="bi bi-calendar-check text-teal"></i>
                      <span>{{ evento.dataEvento | date:'dd/MM/yyyy' }} {{ evento.horario ? ' - ' + evento.horario : '' }}</span>
                    </div>

                    <div class="meta-item">
                      <i class="bi bi-tag-fill text-success"></i>
                      <strong>R$ {{ evento.precoFotoVendida | number:'1.2-2' }} por foto vendida</strong>
                    </div>

                    <div class="meta-item" *ngIf="evento.publicoEstimado">
                      <i class="bi bi-people-fill text-info"></i>
                      <span>{{ evento.publicoEstimado }} pessoas estimadas</span>
                    </div>
                  </div>

                  <p class="event-description" *ngIf="evento.descricao">{{ evento.descricao }}</p>

                  <div class="event-actions-row">
                    <button class="btn btn-teal btn-sm" (click)="abrirModalAddFoto(evento)">
                      <i class="bi bi-camera-plus-fill"></i>
                      <span>Adicionar Fotos</span>
                    </button>
                    <button class="btn btn-outline-danger btn-sm" (click)="excluirEvento(evento)">
                      <i class="bi bi-trash"></i>
                      <span>Excluir Evento</span>
                    </button>
                  </div>
                </div>
              </div>

              <!-- Galeria de Fotos do Evento com Anti-Print (Página 6 do PDF) -->
              <div class="event-photos-section">
                <div class="gallery-header">
                  <h4><i class="bi bi-images text-primary"></i> GALERIA DE FOTOS PROTEGIDAS ({{ evento.fotos?.length || 0 }})</h4>
                  <span class="watermark-legend-tag">
                    <i class="bi bi-lock-fill"></i> Marca D'água Forte: PROIBIDA A CIRCULAÇÃO • DESIGN ARTE
                  </span>
                </div>

                <div class="protected-photos-grid">
                  <div class="photo-card" *ngFor="let foto of evento.fotos">
                    <div class="photo-viewer-wrapper">
                      <!-- Componente com marca d'água diagonal repetida e proteção anti-print -->
                      <app-watermark-viewer 
                        [imageUrl]="foto.urlOuBase64" 
                        [altText]="foto.titulo || evento.nome"
                        [watermarkText]="foto.marcaDaguaTexto || 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE'"
                      ></app-watermark-viewer>
                    </div>

                    <div class="photo-footer-bar">
                      <div class="photo-meta">
                        <span class="photo-code">{{ foto.codigoFoto }}</span>
                        <span class="photo-price text-success">R$ {{ foto.preco | number:'1.2-2' }}</span>
                      </div>
                      <button class="btn btn-danger btn-xs" (click)="excluirFoto(foto)" title="Excluir Foto">
                        <i class="bi bi-trash"></i>
                      </button>
                    </div>
                  </div>

                  <div *ngIf="!evento.fotos?.length" class="empty-gallery-box">
                    <i class="bi bi-camera text-muted" style="font-size: 2rem;"></i>
                    <p class="text-muted text-sm mt-1">Nenhuma foto adicionada neste evento ainda.</p>
                  </div>
                </div>
              </div>
            </div>

            <div *ngIf="eventos().length === 0" class="empty-state-card card">
              <i class="bi bi-calendar-event text-muted" style="font-size: 3rem;"></i>
              <p class="text-muted mt-2">Nenhum evento registrado. Cadastre o primeiro evento esportivo ou corporativo.</p>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- MODAL 1: REGISTRAR NOVO EVENTO -->
    <div class="modal-backdrop" *ngIf="modalNovoEventoAberto()" (click)="fecharModalNovoEvento()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3>Registrar Novo Evento</h3>
          <button class="btn-ghost btn-icon" (click)="fecharModalNovoEvento()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <form (ngSubmit)="salvarEvento()">
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">NOME DO EVENTO</label>
              <input type="text" class="form-control" [(ngModel)]="formEvento.nome" name="nome" required placeholder="Ex: BARRA RUN 2026" />
            </div>

            <div class="form-group">
              <label class="form-label">LOCALIZAÇÃO</label>
              <input type="text" class="form-control" [(ngModel)]="formEvento.localizacao" name="localizacao" required placeholder="Ex: Barra de São Miguel - AL, Brasil" />
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">DATA DO EVENTO</label>
                <input type="date" class="form-control" [(ngModel)]="formEvento.dataEvento" name="dataEvento" required />
              </div>

              <div class="form-group">
                <label class="form-label">HORÁRIO</label>
                <input type="text" class="form-control" [(ngModel)]="formEvento.horario" name="horario" placeholder="Ex: 05:00 às 09:00" />
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">PREÇO POR FOTO (R$)</label>
                <input type="number" step="0.50" class="form-control" [(ngModel)]="formEvento.precoFotoVendida" name="precoFotoVendida" required />
              </div>

              <div class="form-group">
                <label class="form-label">PÚBLICO ESTIMADO</label>
                <input type="number" class="form-control" [(ngModel)]="formEvento.publicoEstimado" name="publicoEstimado" placeholder="Ex: 500" />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">BANNER / FLYER DO EVENTO</label>
              <div class="custom-file-upload-box" (click)="eventoBannerInput.click()">
                <input 
                  type="file" 
                  #eventoBannerInput 
                  (change)="onBannerFileSelected($event)" 
                  accept="image/*,.png,.jpg,.jpeg,.webp,.gif,.bmp,.svg,.ico,.tiff,.heic" 
                  style="display: none" 
                />
                <div *ngIf="!formEvento.bannerUrl" class="upload-placeholder">
                  <i class="bi bi-image text-primary" style="font-size: 1.8rem;"></i>
                  <span class="upload-title">Selecionar banner no computador</span>
                  <small class="upload-sub">Formatos aceitos: PNG, JPG, JPEG, WEBP, etc.</small>
                </div>
                <div *ngIf="formEvento.bannerUrl" class="upload-preview-container" (click)="$event.stopPropagation()">
                  <img [src]="formEvento.bannerUrl" alt="Banner preview" class="preview-banner-rect" />
                  <div class="preview-meta">
                    <span class="preview-title"><i class="bi bi-check-circle-fill text-success"></i> Imagem carregada</span>
                    <div class="preview-actions">
                      <button type="button" class="btn-action-small btn-trocar" (click)="eventoBannerInput.click()">
                        <i class="bi bi-arrow-repeat"></i> Trocar
                      </button>
                      <button type="button" class="btn-action-small btn-remover" (click)="formEvento.bannerUrl = ''">
                        <i class="bi bi-trash"></i> Remover
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">DESCRIÇÃO DO EVENTO</label>
              <textarea class="form-control" rows="3" [(ngModel)]="formEvento.descricao" name="descricao" placeholder="Detalhes da corrida ou festival..."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="fecharModalNovoEvento()">Cancelar</button>
            <button type="submit" class="btn btn-primary">Registrar Evento</button>
          </div>
        </form>
      </div>
    </div>

    <!-- MODAL 2: ADICIONAR FOTO AO EVENTO -->
    <div class="modal-backdrop" *ngIf="modalAddFotoAberto()" (click)="fecharModalAddFoto()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3>Adicionar Foto a: {{ eventoSelecionado()?.nome }}</h3>
          <button class="btn-ghost btn-icon" (click)="fecharModalAddFoto()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <form (ngSubmit)="salvarFoto()">
          <div class="modal-body">
            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">CÓDIGO DA FOTO</label>
                <input type="text" class="form-control" [(ngModel)]="formFoto.codigoFoto" name="codigoFoto" placeholder="Ex: BR26-005" />
              </div>

              <div class="form-group">
                <label class="form-label">PREÇO (R$)</label>
                <input type="number" step="0.50" class="form-control" [(ngModel)]="formFoto.preco" name="preco" required />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">TÍTULO / LEGENDA DA FOTO</label>
              <input type="text" class="form-control" [(ngModel)]="formFoto.titulo" name="titulo" placeholder="Ex: Corrida 10k - Ponto de Chegada" />
            </div>

            <div class="form-group">
              <label class="form-label">FOTO EM ALTA RESOLUÇÃO</label>
              <div class="custom-file-upload-box" (click)="fotoItemInput.click()">
                <input 
                  type="file" 
                  #fotoItemInput 
                  (change)="onFotoFileSelected($event)" 
                  accept="image/*,.png,.jpg,.jpeg,.webp,.gif,.bmp,.svg,.ico,.tiff,.heic" 
                  style="display: none" 
                />
                <div *ngIf="!formFoto.urlOuBase64" class="upload-placeholder">
                  <i class="bi bi-cloud-arrow-up text-primary" style="font-size: 1.8rem;"></i>
                  <span class="upload-title">Selecionar foto no computador</span>
                  <small class="upload-sub">PNG, JPG, JPEG, WEBP, etc.</small>
                </div>
                <div *ngIf="formFoto.urlOuBase64" class="upload-preview-container" (click)="$event.stopPropagation()">
                  <img [src]="formFoto.urlOuBase64" alt="Foto preview" class="preview-banner-rect" />
                  <div class="preview-meta">
                    <span class="preview-title"><i class="bi bi-check-circle-fill text-success"></i> Foto carregada</span>
                    <div class="preview-actions">
                      <button type="button" class="btn-action-small btn-trocar" (click)="fotoItemInput.click()">
                        <i class="bi bi-arrow-repeat"></i> Trocar
                      </button>
                      <button type="button" class="btn-action-small btn-remover" (click)="formFoto.urlOuBase64 = ''">
                        <i class="bi bi-trash"></i> Remover
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">TEXTO DA MARCA D'ÁGUA</label>
              <input type="text" class="form-control" [(ngModel)]="formFoto.marcaDaguaTexto" name="marcaDaguaTexto" />
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="fecharModalAddFoto()">Cancelar</button>
            <button type="submit" class="btn btn-teal">Adicionar Foto com Proteção</button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .events-top-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
    }

    .security-info-box {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      font-size: 0.85rem;
      color: var(--text-secondary);
    }

    .security-info-box i {
      font-size: 1.6rem;
    }

    .events-list-container {
      display: flex;
      flex-direction: column;
      gap: 2rem;
    }

    .event-master-card {
      padding: 1.5rem;
      border: 1px solid var(--border-color);
      width: 100%;
      max-width: 100%;
      min-width: 0;
      overflow: hidden;
      box-sizing: border-box;
      margin-bottom: 1.5rem;
    }

    .events-top-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
      flex-wrap: wrap;
    }

    .security-info-box {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      font-size: 0.85rem;
      word-break: break-word;
      min-width: 0;
      flex: 1;
    }

    .event-hero-grid {
      display: grid;
      grid-template-columns: 320px 1fr;
      gap: 1.5rem;
      padding-bottom: 1.5rem;
      border-bottom: 1px solid var(--border-color);
      margin-bottom: 1.5rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .event-banner-box {
      width: 100%;
      height: 200px;
      border-radius: var(--radius-md);
      overflow: hidden;
    }

    .event-banner-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .event-badge-row {
      display: flex;
      gap: 0.5rem;
      margin-bottom: 0.5rem;
      flex-wrap: wrap;
    }

    .event-title {
      font-size: clamp(1.25rem, 4vw, 1.85rem);
      margin: 0 0 0.85rem 0;
      word-break: break-word;
    }

    .event-meta-info-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 0.65rem 1.25rem;
      margin-bottom: 1rem;
      width: 100%;
      min-width: 0;
    }

    .meta-item {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.85rem;
      color: var(--text-secondary);
      word-break: break-word;
    }

    .event-description {
      font-size: 0.85rem;
      color: var(--text-muted);
      line-height: 1.5;
      margin-bottom: 1.25rem;
      word-break: break-word;
    }

    .event-actions-row {
      display: flex;
      gap: 0.75rem;
      flex-wrap: wrap;
    }

    /* Protected Photos Section */
    .gallery-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1rem;
      gap: 0.5rem;
      flex-wrap: wrap;
    }

    .gallery-header h4 {
      font-size: 1rem;
      margin: 0;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .watermark-legend-tag {
      font-size: 0.7rem;
      font-weight: 700;
      color: var(--color-warning);
      background: var(--color-warning-light);
      padding: 0.2rem 0.6rem;
      border-radius: var(--radius-full);
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
      word-break: break-word;
    }

    .protected-photos-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
      gap: 1rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .photo-card {
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      overflow: hidden;
      display: flex;
      flex-direction: column;
      min-width: 0;
      max-width: 100%;
      box-sizing: border-box;
    }

    .photo-viewer-wrapper {
      width: 100%;
      height: 200px;
    }

    .photo-footer-bar {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0.55rem 0.75rem;
      background: var(--bg-surface);
      border-top: 1px solid var(--border-color);
    }

    .photo-meta {
      display: flex;
      gap: 0.5rem;
      align-items: center;
      font-size: 0.8rem;
    }

    .photo-code {
      font-weight: 800;
      color: var(--text-primary);
    }

    .btn-xs {
      padding: 0.25rem 0.5rem;
      font-size: 0.75rem;
    }

    .empty-gallery-box {
      grid-column: 1 / -1;
      text-align: center;
      padding: 2rem;
      background: var(--bg-surface-elevated);
      border-radius: var(--radius-md);
    }

    .form-row-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    @media (max-width: 900px) {
      .event-hero-grid { grid-template-columns: 1fr; }
      .event-meta-info-grid { grid-template-columns: 1fr; }
      .events-top-row { flex-direction: column; align-items: flex-start; }
      .events-top-row .btn { width: 100%; }
    }

    @media (max-width: 576px) {
      .event-master-card { padding: 1rem 0.75rem; }
      .protected-photos-grid { grid-template-columns: 1fr; }
      .gallery-header { flex-direction: column; align-items: flex-start; gap: 0.5rem; }
      .event-actions-row { width: 100%; flex-direction: column; }
      .event-actions-row .btn { width: 100%; }
      .event-banner-box { height: 160px; }
      .photo-viewer-wrapper { height: 220px; }
      .form-row-2 { grid-template-columns: 1fr; }
    }
  `]
})
export class EventosComponent implements OnInit {
  private apiService = inject(ApiService);

  eventos = signal<Evento[]>([]);
  eventoSelecionado = signal<Evento | null>(null);

  modalNovoEventoAberto = signal<boolean>(false);
  modalAddFotoAberto = signal<boolean>(false);

  formEvento: Partial<Evento> = {};
  formFoto: Partial<FotoEvento> = {};

  ngOnInit(): void {
    this.carregarEventos();
  }

  carregarEventos(): void {
    this.apiService.getEventos().subscribe({
      next: (res) => this.eventos.set(res),
      error: (err) => console.error('Erro ao carregar eventos:', err)
    });
  }

  abrirModalNovoEvento(): void {
    this.formEvento = {
      precoFotoVendida: 10.00,
      publicoEstimado: 500,
      status: 'PUBLICADO',
      dataEvento: new Date().toISOString().split('T')[0]
    };
    this.modalNovoEventoAberto.set(true);
  }

  fecharModalNovoEvento(): void {
    this.modalNovoEventoAberto.set(false);
  }

  salvarEvento(): void {
    this.apiService.createEvento(this.formEvento).subscribe({
      next: () => {
        this.fecharModalNovoEvento();
        this.carregarEventos();
      }
    });
  }

  abrirModalAddFoto(evento: Evento): void {
    this.eventoSelecionado.set(evento);
    this.formFoto = {
      preco: evento.precoFotoVendida || 10.00,
      marcaDaguaTexto: 'PROIBIDA A CIRCULAÇÃO • DESIGN ARTE',
      codigoFoto: 'BR26-' + Math.floor(100 + Math.random() * 900)
    };
    this.modalAddFotoAberto.set(true);
  }

  fecharModalAddFoto(): void {
    this.modalAddFotoAberto.set(false);
  }

  onBannerFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const reader = new FileReader();
      reader.onload = (e: ProgressEvent<FileReader>) => {
        this.formEvento.bannerUrl = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  onFotoFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const reader = new FileReader();
      reader.onload = (e: ProgressEvent<FileReader>) => {
        this.formFoto.urlOuBase64 = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  salvarFoto(): void {
    const ev = this.eventoSelecionado();
    if (!ev?.id) return;

    this.apiService.addFotoEvento(ev.id, this.formFoto).subscribe({
      next: () => {
        this.fecharModalAddFoto();
        this.carregarEventos();
      }
    });
  }

  excluirFoto(foto: FotoEvento): void {
    if (!foto.id || !confirm('Deseja excluir esta foto?')) return;
    this.apiService.deleteFotoEvento(foto.id).subscribe({
      next: () => this.carregarEventos()
    });
  }

  excluirEvento(evento: Evento): void {
    if (!evento.id || !confirm(`Deseja excluir o evento ${evento.nome}?`)) return;
    this.apiService.deleteEvento(evento.id).subscribe({
      next: () => this.carregarEventos()
    });
  }
}
