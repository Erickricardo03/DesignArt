import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Municipio, ConfiguracaoLoja, FaixaDesconto } from '../../core/models';
import { HeaderComponent } from '../../shared/components/header.component';
import { SidebarComponent } from '../../shared/components/sidebar.component';

@Component({
  selector: 'app-configuracoes',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-header 
          title="Configurações & Parâmetros" 
          subtitle="Municípios atendidos, regras do e-commerce e preferências do sistema"
          (refreshAction)="carregar()"
        ></app-header>

        <main class="page-body">
          <div class="page-top-bar">
            <div>
              <span class="badge-subtitle">PREFERÊNCIAS DO SISTEMA</span>
              <h1 class="page-title">Configurações Gerais & Parâmetros</h1>
            </div>
          </div>

          <div class="config-grid-2">
            <!-- Card: Municípios Atendidos (16746.jpg) -->
            <div class="config-card">
              <div class="config-card-header">
                <div>
                  <h2 class="config-card-title"><i class="bi bi-geo-alt-fill text-purple"></i> Municípios de Atuação</h2>
                  <p class="config-card-subtitle">Cadastre os municípios onde a agência presta serviços de captação e design.</p>
                </div>
              </div>

              <div class="add-municipio-box">
                <div class="form-grid-muni">
                  <input 
                    type="text" 
                    [(ngModel)]="novoMunicipioNome" 
                    placeholder="Nome da cidade (ex: São Miguel dos Campos)" 
                    class="form-control"
                  />
                  <input 
                    type="text" 
                    [(ngModel)]="novoMunicipioUf" 
                    placeholder="UF (AL)" 
                    class="form-control uf-input"
                    maxlength="2"
                  />
                  <button class="btn-add-muni" (click)="adicionarMunicipio()">
                    <i class="bi bi-plus-lg"></i> Adicionar
                  </button>
                </div>
              </div>

              <div class="municipios-list">
                <div class="municipio-item" *ngFor="let m of municipios()">
                  <div class="muni-info">
                    <i class="bi bi-pin-map-fill text-purple"></i>
                    <span class="muni-nome">{{ m.nome }}</span>
                    <span class="muni-uf">{{ m.uf }}</span>
                  </div>
                  <button class="btn-del-muni" (click)="removerMunicipio(m.id!)" title="Remover Município">
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </div>
            </div>

            <!-- Card: Loja de Fotos - Retenção e Descontos (16716.jpg) -->
            <div class="config-card">
              <div class="config-card-header">
                <div>
                  <h2 class="config-card-title"><i class="bi bi-images text-purple"></i> Loja de Fotos — Retenção & Descontos</h2>
                  <p class="config-card-subtitle">Defina o tempo de retenção de fotos compradas e regras de desconto progressivo.</p>
                </div>
              </div>

              <div class="form-group mb-4">
                <label class="form-label-bold">Dias de retenção após exclusão (fotos já compradas)</label>
                <input type="number" [(ngModel)]="configLoja.diasRetencao" class="form-control" min="1" max="365" />
              </div>

              <div class="faixas-wrapper">
                <label class="form-label-bold">Faixas de desconto progressivo</label>
                
                <div class="faixas-chips-list">
                  <div class="faixa-chip" *ngFor="let f of configLoja.faixasDesconto; let i = index">
                    <span>A partir de <strong>{{ f.qtdMinima }} fotos</strong>: {{ f.percentualDesconto }}% OFF</span>
                    <button class="chip-remove" (click)="removerFaixa(i)"><i class="bi bi-x"></i></button>
                  </div>
                </div>

                <div class="add-faixa-form" *ngIf="addFaixaOpen">
                  <input type="number" [(ngModel)]="novaFaixa.qtdMinima" placeholder="Mínimo fotos" class="form-control" />
                  <input type="number" [(ngModel)]="novaFaixa.percentualDesconto" placeholder="% desconto" class="form-control" />
                  <button class="btn-save-sm" (click)="confirmarFaixa()">OK</button>
                  <button class="btn-cancel-sm" (click)="addFaixaOpen = false">Cancelar</button>
                </div>

                <button class="btn-text-add" *ngIf="!addFaixaOpen" (click)="addFaixaOpen = true">
                  <i class="bi bi-plus"></i> Adicionar
                </button>
              </div>

              <div class="save-config-btn-wrap">
                <button class="btn-save-global" (click)="salvarRegrasLoja()">
                  <i class="bi bi-check2"></i> Salvar Parâmetros da Loja
                </button>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  `,
  styles: [`
    .page-body {
      padding: 1.75rem 2rem;
    }
    .page-top-bar {
      margin-bottom: 1.75rem;
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
      margin: 0.2rem 0 0 0;
    }

    .config-grid-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.75rem;
    }
    @media (max-width: 960px) {
      .config-grid-2 {
        grid-template-columns: 1fr;
      }
    }

    .config-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.5rem;
      box-shadow: 0 4px 14px rgba(0,0,0,0.03);
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .config-card-header {
      padding-bottom: 0.75rem;
      border-bottom: 1px solid var(--border-color);
    }
    .config-card-title {
      font-size: 1.1rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 0.25rem 0;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .text-purple { color: var(--primary); }
    .config-card-subtitle {
      font-size: 0.8rem;
      color: var(--text-secondary);
      margin: 0;
    }

    .form-grid-muni {
      display: grid;
      grid-template-columns: 1fr 70px auto;
      gap: 0.65rem;
    }
    .form-control {
      padding: 0.65rem 0.85rem;
      border: 1px solid var(--border-color);
      border-radius: 8px;
      background: var(--card-bg);
      color: var(--text-primary);
      font-size: 0.88rem;
      outline: none;
    }
    .uf-input {
      text-transform: uppercase;
      text-align: center;
    }
    .btn-add-muni {
      background: #1e293b;
      color: #fff;
      border: none;
      padding: 0 1rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.82rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.35rem;
    }

    .municipios-list {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      max-height: 280px;
      overflow-y: auto;
    }
    .municipio-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.65rem 0.85rem;
      background: rgba(124, 58, 237, 0.03);
      border: 1px solid var(--border-color);
      border-radius: 8px;
    }
    .muni-info {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.85rem;
    }
    .muni-nome {
      font-weight: 700;
      color: var(--text-primary);
    }
    .muni-uf {
      font-size: 0.72rem;
      font-weight: 800;
      background: var(--primary);
      color: #fff;
      padding: 0.1rem 0.4rem;
      border-radius: 4px;
    }
    .btn-del-muni {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      font-size: 0.95rem;
    }

    .form-label-bold {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text-primary);
      display: block;
      margin-bottom: 0.45rem;
    }
    .mb-4 { margin-bottom: 1rem; }

    .faixas-chips-list {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }
    .faixa-chip {
      background: rgba(124, 58, 237, 0.08);
      border: 1px solid rgba(124, 58, 237, 0.2);
      border-radius: 8px;
      padding: 0.35rem 0.7rem;
      font-size: 0.8rem;
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }
    .chip-remove {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      font-size: 1rem;
      padding: 0;
      line-height: 1;
    }
    .add-faixa-form {
      display: grid;
      grid-template-columns: 1fr 1fr auto auto;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }
    .btn-save-sm {
      background: var(--primary);
      color: #fff;
      border: none;
      padding: 0 0.85rem;
      border-radius: 6px;
      font-weight: 700;
      cursor: pointer;
    }
    .btn-cancel-sm {
      background: none;
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      padding: 0 0.65rem;
      border-radius: 6px;
      cursor: pointer;
    }
    .btn-text-add {
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

    .save-config-btn-wrap {
      margin-top: auto;
      padding-top: 1rem;
      border-top: 1px solid var(--border-color);
      display: flex;
      justify-content: flex-end;
    }
    .btn-save-global {
      background: var(--primary);
      color: #fff;
      border: none;
      padding: 0.65rem 1.35rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.85rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }
  `]
})
export class ConfiguracoesComponent implements OnInit {
  private api = inject(ApiService);

  municipios = signal<Municipio[]>([]);
  novoMunicipioNome = '';
  novoMunicipioUf = 'AL';

  configLoja: ConfiguracaoLoja = {
    diasRetencao: 10,
    faixasDesconto: [
      { id: 1, qtdMinima: 5, percentualDesconto: 10 },
      { id: 2, qtdMinima: 10, percentualDesconto: 20 },
    ],
  };

  addFaixaOpen = false;
  novaFaixa: FaixaDesconto = { qtdMinima: 5, percentualDesconto: 10 };

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.getMunicipios().subscribe((res) => {
      this.municipios.set(res);
    });

    this.api.getConfigLoja().subscribe((cfg) => {
      if (cfg) {
        this.configLoja = { ...cfg };
      }
    });
  }

  adicionarMunicipio(): void {
    if (!this.novoMunicipioNome.trim()) return;
    this.api.createMunicipio({ nome: this.novoMunicipioNome.trim(), uf: this.novoMunicipioUf.toUpperCase() }).subscribe(() => {
      this.novoMunicipioNome = '';
      this.carregar();
    });
  }

  removerMunicipio(id: number): void {
    if (confirm('Deseja remover este município?')) {
      this.api.deleteMunicipio(id).subscribe(() => {
        this.carregar();
      });
    }
  }

  confirmarFaixa(): void {
    if (this.novaFaixa.qtdMinima > 0 && this.novaFaixa.percentualDesconto > 0) {
      this.configLoja.faixasDesconto.push({ ...this.novaFaixa, id: Date.now() });
      this.addFaixaOpen = false;
      this.novaFaixa = { qtdMinima: 5, percentualDesconto: 10 };
    }
  }

  removerFaixa(i: number): void {
    this.configLoja.faixasDesconto.splice(i, 1);
  }

  salvarRegrasLoja(): void {
    this.api.updateConfigLoja(this.configLoja).subscribe(() => {
      alert('Parâmetros salvos com sucesso!');
    });
  }
}
