import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Evento, ConfiguracaoLoja, FaixaDesconto } from '../../core/models';
import { HeaderComponent } from '../../shared/components/header.component';
import { SidebarComponent } from '../../shared/components/sidebar.component';

@Component({
  selector: 'app-loja-fotos',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-header 
          title="Loja de Fotos & Álbuns Protegidos" 
          subtitle="Galeria com marca d'água anti-print, pacotes e e-commerce de fotos"
          (refreshAction)="carregar()"
        ></app-header>

        <main class="page-body">
          <div class="page-top-bar">
            <div>
              <span class="badge-subtitle">GALERIA & E-COMMERCE</span>
              <h1 class="page-title">Loja de Fotos & Álbuns Protegidos</h1>
            </div>
            <div class="top-actions">
              <button class="btn-primary" (click)="openNovoAlbumModal()">
                <i class="bi bi-plus-lg"></i> NOVO ÁLBUM
              </button>
            </div>
          </div>

          <!-- Seção de Retenção e Descontos (Exatamente como em 16716.jpg) -->
          <div class="retencao-card">
            <h2 class="retencao-title">LOJA DE FOTOS — RETENÇÃO E DESCONTOS</h2>
            
            <div class="form-group-retencao">
              <label class="retencao-label">Dias de retenção após exclusão (fotos já compradas)</label>
              <input 
                type="number" 
                [(ngModel)]="configLoja.diasRetencao" 
                class="form-control-retencao" 
                min="1" 
                max="365"
              />
            </div>

            <div class="descontos-box">
              <span class="descontos-title">Faixas de desconto progressivo</span>
              
              <div class="faixas-list" *ngIf="configLoja.faixasDesconto.length > 0">
                <div class="faixa-item" *ngFor="let f of configLoja.faixasDesconto; let i = index">
                  <span class="faixa-info">
                    A partir de <strong>{{ f.qtdMinima }} fotos</strong>: 
                    <span class="badge-discount">{{ f.percentualDesconto }}% OFF</span>
                  </span>
                  <button class="btn-remove-faixa" (click)="removerFaixa(i)"><i class="bi bi-x"></i></button>
                </div>
              </div>

              <!-- Formulário inline para adicionar faixa -->
              <div class="add-faixa-inline" *ngIf="adicionandoFaixa">
                <input type="number" [(ngModel)]="novaFaixa.qtdMinima" placeholder="Qtd mínima fotos (ex: 5)" class="form-control-sm" />
                <input type="number" [(ngModel)]="novaFaixa.percentualDesconto" placeholder="% desconto (ex: 15)" class="form-control-sm" />
                <button class="btn-confirm-faixa" (click)="salvarFaixa()">Salvar</button>
                <button class="btn-cancel-faixa" (click)="adicionandoFaixa = false">Cancelar</button>
              </div>

              <button class="btn-add-desconto" *ngIf="!adicionandoFaixa" (click)="adicionandoFaixa = true">
                <i class="bi bi-plus"></i> Adicionar
              </button>
            </div>

            <div class="retencao-save-bar">
              <button class="btn-save-config" (click)="salvarConfiguracao()">
                <i class="bi bi-check2"></i> Salvar Regras da Loja
              </button>
            </div>
          </div>

          <!-- Galeria de Álbuns Publicados (Exatamente como em 16718.jpg) -->
          <div class="albuns-section">
            <h2 class="section-title">Álbuns Publicados na Loja</h2>
            
            <div class="albuns-grid stagger-grid">
              <div class="album-card hover-lift" *ngFor="let alb of albuns()">
                <!-- Preview com Marca D'Água Anti-Print -->
                <div class="album-cover-wrap">
                  <img [src]="getAlbumCover(alb)" [alt]="alb.nome" />
                  <div class="watermark-overlay">
                    <span class="wm-text">PROIBIDA A CIRCULAÇÃO • DESIGN ARTE</span>
                    <span class="wm-text">PROIBIDA A CIRCULAÇÃO • DESIGN ARTE</span>
                    <span class="wm-text">PROIBIDA A CIRCULAÇÃO • DESIGN ARTE</span>
                    <span class="wm-text">PROIBIDA A CIRCULAÇÃO • DESIGN ARTE</span>
                  </div>
                </div>

                <div class="album-body">
                  <h3 class="album-title">{{ alb.nome }}</h3>
                  <span class="album-client">{{ alb.localizacao || 'Cliente' }}</span>

                  <div class="album-footer-row">
                    <span class="status-tag-publicado">
                      <i class="bi bi-eye-fill"></i> Publicado
                    </span>
                    <div class="album-card-icons">
                      <button class="icon-action-btn" (click)="copiarLinkAlbum(alb)" title="Copiar Link do Álbum">
                        <i class="bi bi-copy"></i>
                      </button>
                      <button class="icon-action-btn delete" (click)="excluirAlbum(alb)" title="Excluir Álbum">
                        <i class="bi bi-trash"></i>
                      </button>
                    </div>
                  </div>

                  <span class="album-path">/loja/albuns/{{ alb.slug || 'album' }}</span>
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>

    <!-- Modal Novo Álbum -->
    <div class="modal-backdrop" *ngIf="modalNovoAlbumOpen()">
      <div class="modal-card animate-fade-in">
        <div class="modal-header">
          <h3 class="modal-title">Novo Álbum / Cobertura de Evento</h3>
          <button class="close-btn" (click)="modalNovoAlbumOpen.set(false)"><i class="bi bi-x-lg"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>NOME DO ÁLBUM *</label>
            <input type="text" [(ngModel)]="albumForm.nome" class="form-control" placeholder="Ex: Fotos Loja 3 anos" />
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>LOCALIZAÇÃO / CIDADE</label>
              <input type="text" [(ngModel)]="albumForm.localizacao" class="form-control" placeholder="Ex: Maceió - AL" />
            </div>
            <div class="form-group">
              <label>PREÇO POR FOTO (R$)</label>
              <input type="number" [(ngModel)]="albumForm.precoFotoVendida" class="form-control" placeholder="10.00" />
            </div>
          </div>

          <div class="form-group">
            <label>BANNER / CAPA DO ÁLBUM</label>
            <div class="custom-file-upload-box" (click)="bannerFileInput.click()">
              <input 
                type="file" 
                #bannerFileInput 
                (change)="onBannerFileSelected($event)" 
                accept="image/*,.png,.jpg,.jpeg,.webp,.gif,.bmp,.svg,.ico,.tiff,.heic" 
                style="display: none" 
              />
              <div *ngIf="!albumForm.bannerUrl" class="upload-placeholder">
                <i class="bi bi-image text-primary" style="font-size: 1.8rem;"></i>
                <span class="upload-title">Selecionar foto de capa no computador</span>
                <small class="upload-sub">Formatos aceitos: PNG, JPG, JPEG, WEBP, etc.</small>
              </div>
              <div *ngIf="albumForm.bannerUrl" class="upload-preview-container" (click)="$event.stopPropagation()">
                <img [src]="albumForm.bannerUrl" alt="Banner preview" class="preview-banner-rect" />
                <div class="preview-meta">
                  <span class="preview-title"><i class="bi bi-check-circle-fill text-success"></i> Capa selecionada</span>
                  <div class="preview-actions">
                    <button type="button" class="btn-action-small btn-trocar" (click)="bannerFileInput.click()">
                      <i class="bi bi-arrow-repeat"></i> Trocar
                    </button>
                    <button type="button" class="btn-action-small btn-remover" (click)="albumForm.bannerUrl = ''">
                      <i class="bi bi-trash"></i> Remover
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="form-group">
            <label>DESCRIÇÃO DO EVENTO</label>
            <textarea rows="3" [(ngModel)]="albumForm.descricao" class="form-control" placeholder="Detalhes sobre as fotos..."></textarea>
          </div>

          <div class="modal-actions-right">
            <button class="btn-cancel" (click)="modalNovoAlbumOpen.set(false)">Cancelar</button>
            <button class="btn-save" (click)="salvarNovoAlbum()">Publicar Álbum</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-body {
      padding: 1.75rem 2rem;
    }
    .page-top-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
    }
    .badge-subtitle {
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      color: var(--primary);
      text-transform: uppercase;
    }
    .page-title {
      font-size: 1.45rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
    }
    .btn-primary {
      background: #1e293b;
      color: #fff;
      border: none;
      padding: 0.7rem 1.4rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.85rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    /* Seção 16716.jpg: LOJA DE FOTOS - RETENÇÃO E DESCONTOS */
    .retencao-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.5rem;
      margin-bottom: 2rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
    }
    .retencao-title {
      font-size: 1.05rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 1.25rem 0;
      letter-spacing: 0.03em;
    }
    .form-group-retencao {
      margin-bottom: 1.25rem;
    }
    .retencao-label {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text-primary);
      display: block;
      margin-bottom: 0.5rem;
    }
    .form-control-retencao {
      width: 100%;
      padding: 0.65rem 0.85rem;
      border: 1px solid var(--border-color);
      border-radius: 8px;
      background: var(--card-bg);
      color: var(--text-primary);
      font-size: 0.95rem;
      font-weight: 600;
      outline: none;
    }
    .form-control-retencao:focus {
      border-color: var(--primary);
    }
    .descontos-box {
      margin-bottom: 1.25rem;
    }
    .descontos-title {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text-primary);
      display: block;
      margin-bottom: 0.65rem;
    }
    .faixas-list {
      display: flex;
      flex-wrap: wrap;
      gap: 0.65rem;
      margin-bottom: 0.75rem;
    }
    .faixa-item {
      background: rgba(124, 58, 237, 0.06);
      border: 1px solid rgba(124, 58, 237, 0.2);
      border-radius: 8px;
      padding: 0.4rem 0.75rem;
      font-size: 0.82rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .badge-discount {
      background: #10b981;
      color: #fff;
      font-weight: 800;
      font-size: 0.72rem;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
    }
    .btn-remove-faixa {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      font-size: 1rem;
      padding: 0;
      line-height: 1;
    }
    .btn-add-desconto {
      background: none;
      border: none;
      color: #7c3aed;
      font-size: 0.85rem;
      font-weight: 800;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      padding: 0.35rem 0;
    }
    .add-faixa-inline {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }
    .form-control-sm {
      padding: 0.45rem 0.65rem;
      border: 1px solid var(--border-color);
      border-radius: 6px;
      background: var(--card-bg);
      color: var(--text-primary);
      font-size: 0.82rem;
      outline: none;
    }
    .btn-confirm-faixa {
      background: var(--primary);
      color: #fff;
      border: none;
      padding: 0.45rem 0.85rem;
      border-radius: 6px;
      font-weight: 700;
      font-size: 0.8rem;
      cursor: pointer;
    }
    .btn-cancel-faixa {
      background: none;
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      padding: 0.45rem 0.75rem;
      border-radius: 6px;
      font-size: 0.8rem;
      cursor: pointer;
    }
    .retencao-save-bar {
      display: flex;
      justify-content: flex-end;
      padding-top: 0.75rem;
      border-top: 1px solid var(--border-color);
    }
    .btn-save-config {
      background: var(--primary);
      color: #fff;
      border: none;
      padding: 0.6rem 1.25rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.84rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }

    /* Galeria 16718.jpg */
    .albuns-section {
      margin-top: 1.5rem;
    }
    .section-title {
      font-size: 1.15rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 1.25rem 0;
    }
    .albuns-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 1.5rem;
    }
    .album-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 4px 16px rgba(0,0,0,0.04);
      display: flex;
      flex-direction: column;
    }
    .album-cover-wrap {
      height: 380px;
      position: relative;
      background: #0f172a;
      overflow: hidden;
    }
    .album-cover-wrap img {
      width: 100%;
      height: 100%;
      object-fit: cover;
      user-select: none;
      -webkit-user-drag: none;
    }

    /* Marca d'água reforçada anti-print */
    .watermark-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      flex-direction: column;
      justify-content: space-around;
      align-items: center;
      pointer-events: none;
      transform: rotate(-25deg) scale(1.3);
      opacity: 0.45;
    }
    .wm-text {
      color: rgba(255, 255, 255, 0.9);
      font-size: 0.85rem;
      font-weight: 900;
      letter-spacing: 0.2em;
      text-shadow: 0 1px 4px rgba(0,0,0,0.8);
      white-space: nowrap;
    }

    .album-body {
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .album-title {
      font-size: 1rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
    }
    .album-client {
      font-size: 0.8rem;
      color: var(--text-secondary);
    }
    .album-footer-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 0.65rem;
      padding-top: 0.65rem;
      border-top: 1px solid var(--border-color);
    }
    .status-tag-publicado {
      font-size: 0.72rem;
      font-weight: 700;
      color: #10b981;
      background: #dcfce7;
      padding: 0.2rem 0.55rem;
      border-radius: 12px;
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
    }
    .album-card-icons {
      display: flex;
      gap: 0.5rem;
    }
    .icon-action-btn {
      background: none;
      border: none;
      color: var(--text-secondary);
      font-size: 0.95rem;
      cursor: pointer;
      padding: 0.2rem;
      transition: color 0.2s;
    }
    .icon-action-btn:hover {
      color: var(--primary);
    }
    .icon-action-btn.delete:hover {
      color: #ef4444;
    }
    .album-path {
      font-size: 0.7rem;
      color: var(--text-secondary);
      font-family: monospace;
      margin-top: 0.2rem;
    }

    /* Modais */
    .modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(15, 23, 42, 0.75);
      backdrop-filter: blur(8px);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1050;
      padding: 1rem;
    }
    .modal-card {
      background: var(--bg-surface) !important;
      color: var(--text-primary) !important;
      opacity: 1 !important;
      border: 1.5px solid var(--border-color);
      border-radius: 16px;
      width: 100%;
      max-width: 540px;
      box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.5);
    }
    .modal-header {
      padding: 1.25rem 1.75rem;
      border-bottom: 1.5px solid var(--border-color);
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: var(--bg-surface);
    }
    .modal-title {
      font-size: 1.25rem;
      font-weight: 800;
      margin: 0;
      color: var(--text-primary);
    }
    .close-btn {
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      width: 32px;
      height: 32px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1rem;
      cursor: pointer;
      transition: all 0.2s;
    }
    .close-btn:hover {
      background: var(--bg-surface-hover);
      color: var(--primary);
    }
    .modal-body {
      padding: 1.75rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      background: var(--bg-surface);
    }
    .form-grid-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }
    .form-group {
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
    }
    .form-group label {
      font-size: 0.8rem;
      font-weight: 800;
      color: var(--text-primary);
      text-transform: uppercase;
      letter-spacing: 0.04em;
    }
    .form-control {
      padding: 0.75rem 0.95rem;
      border: 1.5px solid var(--border-color);
      border-radius: 8px;
      background: var(--bg-surface);
      color: var(--text-primary);
      font-size: 0.9rem;
      font-weight: 600;
      outline: none;
      transition: all 0.2s ease;
    }
    .form-control:focus {
      border-color: var(--primary);
      box-shadow: 0 0 0 3px var(--color-primary-light);
    }
    .modal-actions-right {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      margin-top: 0.75rem;
    }
    .btn-cancel {
      background: var(--bg-surface-elevated);
      border: 1.5px solid var(--border-color);
      color: var(--text-primary);
      padding: 0.7rem 1.35rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.88rem;
      cursor: pointer;
      transition: all 0.2s;
    }
    .btn-cancel:hover {
      background: var(--bg-surface-hover);
      border-color: var(--border-subtle);
    }
    .btn-save {
      background: var(--color-primary-gradient);
      color: #fff;
      border: none;
      padding: 0.7rem 1.5rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.88rem;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(124, 58, 237, 0.35);
      transition: all 0.2s;
    }
    .btn-save:hover {
      box-shadow: 0 6px 20px rgba(124, 58, 237, 0.5);
      filter: brightness(1.06);
      transform: translateY(-1px);
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
      border-color: var(--primary);
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
    .preview-banner-rect {
      width: 140px;
      height: 70px;
      object-fit: cover;
      border-radius: 8px;
      border: 2px solid var(--primary);
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
export class LojaFotosComponent implements OnInit {
  private api = inject(ApiService);

  albuns = signal<Evento[]>([]);
  configLoja: ConfiguracaoLoja = {
    diasRetencao: 10,
    faixasDesconto: [
      { id: 1, qtdMinima: 5, percentualDesconto: 10 },
      { id: 2, qtdMinima: 10, percentualDesconto: 20 },
    ],
  };

  adicionandoFaixa = false;
  novaFaixa: FaixaDesconto = { qtdMinima: 5, percentualDesconto: 10 };

  modalNovoAlbumOpen = signal(false);
  albumForm: Partial<Evento> = {
    nome: '',
    localizacao: '',
    precoFotoVendida: 10,
    bannerUrl: '',
    descricao: '',
  };

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.getEventos().subscribe((res) => {
      this.albuns.set(res);
    });

    this.api.getConfigLoja().subscribe((cfg) => {
      if (cfg) {
        this.configLoja = { ...cfg };
      }
    });
  }

  getAlbumCover(alb: Evento): string {
    if (alb.fotos && alb.fotos.length > 0) {
      return alb.fotos[0].urlOuBase64;
    }
    return alb.bannerUrl || 'https://images.unsplash.com/photo-1490481651871-ab68de25d43d?auto=format&fit=crop&w=800&q=80';
  }

  salvarFaixa(): void {
    if (this.novaFaixa.qtdMinima > 0 && this.novaFaixa.percentualDesconto > 0) {
      this.configLoja.faixasDesconto.push({ ...this.novaFaixa, id: Date.now() });
      this.adicionandoFaixa = false;
      this.novaFaixa = { qtdMinima: 5, percentualDesconto: 10 };
    }
  }

  removerFaixa(idx: number): void {
    this.configLoja.faixasDesconto.splice(idx, 1);
  }

  salvarConfiguracao(): void {
    this.api.updateConfigLoja(this.configLoja).subscribe(() => {
      alert('Configurações de retenção e descontos salvas com sucesso!');
    });
  }

  copiarLinkAlbum(alb: Evento): void {
    const slug = alb.slug || alb.nome.toLowerCase().replace(/[^a-z0-9]+/g, '-');
    const url = `${window.location.origin}/loja/albuns/${slug}`;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(url);
    }
    alert(`Link do álbum copiado:\n${url}`);
  }

  excluirAlbum(alb: Evento): void {
    if (confirm(`Tem certeza que deseja excluir o álbum "${alb.nome}"?`)) {
      this.api.deleteEvento(alb.id!).subscribe(() => {
        this.carregar();
      });
    }
  }

  openNovoAlbumModal(): void {
    this.albumForm = {
      nome: '',
      localizacao: 'Maceió - AL, Brasil',
      precoFotoVendida: 12.0,
      bannerUrl: '',
      descricao: '',
    };
    this.modalNovoAlbumOpen.set(true);
  }

  onBannerFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const reader = new FileReader();
      reader.onload = (e: ProgressEvent<FileReader>) => {
        this.albumForm.bannerUrl = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  salvarNovoAlbum(): void {
    if (!this.albumForm.nome) return;
    this.api.createEvento(this.albumForm).subscribe(() => {
      this.modalNovoAlbumOpen.set(false);
      this.carregar();
    });
  }
}
