import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { ServicoCatalogo } from '../../core/models';
import { HeaderComponent } from '../../shared/components/header.component';
import { SidebarComponent } from '../../shared/components/sidebar.component';

@Component({
  selector: 'app-servicos',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-header 
          title="Catálogo de Serviços & Planos" 
          subtitle="Gestão de planos recorrentes e serviços avulsos da agência"
          (refreshAction)="carregar()"
        ></app-header>

        <main class="page-body">
          <div class="servicos-layout-grid">
            <!-- Coluna da Esquerda: Gestão de Catálogo (Exatamente como em 16724.jpg) -->
            <div class="servicos-sidebar-card">
              <span class="badge-subtitle">GESTÃO DE CATÁLOGO</span>
              <h1 class="card-side-title">Cadastre novos serviços prestados pela agência e seus respectivos valores.</h1>
              
              <button class="btn-novo-servico" (click)="openNovoModal()">
                <i class="bi bi-plus-lg"></i> NOVO SERVIÇO
              </button>

              <div class="side-tip-box">
                <i class="bi bi-info-circle-fill text-purple"></i>
                <p>Os serviços cadastrados aqui ficam disponíveis automaticamente na criação de contratos de clientes e na precificação de demandas.</p>
              </div>
            </div>

            <!-- Coluna Principal: Serviços Oferecidos (Exatamente como em 16724.jpg) -->
            <div class="servicos-main-content">
              <div class="servicos-header-bar">
                <h2 class="section-main-title">SERVIÇOS OFERECIDOS</h2>
                <div class="search-wrap">
                  <input 
                    type="text" 
                    placeholder="Buscar serviço..." 
                    [(ngModel)]="busca" 
                    (input)="filtrar()"
                  />
                  <i class="bi bi-search"></i>
                </div>
              </div>

              <!-- Grid de Cards de Serviços -->
              <div class="servicos-cards-grid">
                <div class="servico-item-card" *ngFor="let s of servicosFiltrados()">
                  <div class="servico-card-top">
                    <h3 class="servico-name">{{ s.nome }}</h3>
                    <span class="badge-ativo" [class.badge-inativo]="s.status === 'INATIVO'">
                      {{ s.status || 'ATIVO' }}
                    </span>
                  </div>

                  <div class="servico-price-tag">
                    R$ {{ s.preco.toFixed(2).replace('.', ',') }}
                  </div>

                  <p class="servico-desc">
                    {{ s.descricao || 'Sem descrição disponível.' }}
                  </p>

                  <div class="servico-card-actions">
                    <button class="action-btn-edit" (click)="openEditarModal(s)">Editar</button>
                    <button class="action-btn-toggle" (click)="toggleAtivo(s)">
                      {{ s.status === 'ATIVO' ? 'Desativar' : 'Ativar' }}
                    </button>
                    <button class="action-btn-delete" (click)="excluir(s)">Excluir</button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>

    <!-- Modal Novo / Editar Serviço -->
    <div class="modal-backdrop" *ngIf="modalOpen()">
      <div class="modal-card animate-fade-in">
        <div class="modal-header">
          <h3 class="modal-title">{{ editandoId ? 'Editar Serviço / Plano' : 'Novo Serviço / Plano' }}</h3>
          <button class="close-btn" (click)="modalOpen.set(false)"><i class="bi bi-x-lg"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>NOME DO PLANO / SERVIÇO *</label>
            <input type="text" [(ngModel)]="servicoForm.nome" class="form-control" placeholder="Ex: Plano Creator Plus – Mercados de Rede" />
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>VALOR (R$) *</label>
              <input type="number" [(ngModel)]="servicoForm.preco" class="form-control" placeholder="600.00" />
            </div>
            <div class="form-group">
              <label>CATEGORIA</label>
              <input type="text" [(ngModel)]="servicoForm.categoria" class="form-control" placeholder="Ex: Varejo & Redes" />
            </div>
          </div>

          <div class="form-group">
            <label>STATUS</label>
            <select [(ngModel)]="servicoForm.status" class="form-control">
              <option value="ATIVO">ATIVO</option>
              <option value="INATIVO">INATIVO</option>
            </select>
          </div>

          <div class="form-group">
            <label>DESCRIÇÃO DETALHADA DOS ENTREGÁVEIS</label>
            <textarea rows="4" [(ngModel)]="servicoForm.descricao" class="form-control" placeholder="Descreva tudo o que está incluso no plano..."></textarea>
          </div>

          <div class="modal-actions-right">
            <button class="btn-cancel" (click)="modalOpen.set(false)">Cancelar</button>
            <button class="btn-save" (click)="salvar()">Salvar Serviço</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-body {
      padding: 2rem;
    }
    .servicos-layout-grid {
      display: grid;
      grid-template-columns: 320px 1fr;
      gap: 2rem;
      align-items: start;
    }
    @media (max-width: 960px) {
      .servicos-layout-grid {
        grid-template-columns: 1fr;
      }
    }

    /* Card da Esquerda (16724.jpg) */
    .servicos-sidebar-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-left: 4px solid var(--primary);
      border-radius: 12px;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
    }
    .badge-subtitle {
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      color: var(--primary);
      text-transform: uppercase;
    }
    .card-side-title {
      font-size: 1.15rem;
      font-weight: 800;
      color: var(--text-primary);
      line-height: 1.35;
      margin: 0;
    }
    .btn-novo-servico {
      background: #1e293b;
      color: #fff;
      border: none;
      padding: 0.85rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.88rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      transition: background 0.2s, transform 0.2s;
    }
    .btn-novo-servico:hover {
      background: #0f172a;
      transform: translateY(-1px);
    }
    .side-tip-box {
      background: rgba(124, 58, 237, 0.04);
      border-radius: 8px;
      padding: 0.85rem;
      font-size: 0.78rem;
      color: var(--text-secondary);
      line-height: 1.4;
      display: flex;
      gap: 0.5rem;
    }
    .text-purple { color: var(--primary); }

    /* Painel Principal */
    .servicos-header-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
      gap: 1rem;
    }
    .section-main-title {
      font-size: 1.15rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
      letter-spacing: 0.04em;
    }
    .search-wrap {
      position: relative;
      width: 280px;
    }
    .search-wrap input {
      width: 100%;
      padding: 0.6rem 2rem 0.6rem 0.85rem;
      border: 1px solid var(--border-color);
      border-radius: 8px;
      background: var(--card-bg);
      color: var(--text-primary);
      font-size: 0.84rem;
      outline: none;
    }
    .search-wrap i {
      position: absolute;
      right: 0.75rem;
      top: 50%;
      transform: translateY(-50%);
      color: var(--text-secondary);
      pointer-events: none;
    }

    /* Cards Grid */
    .servicos-cards-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
      gap: 1.25rem;
    }
    .servico-item-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.35rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      transition: transform 0.2s, box-shadow 0.2s;
    }
    .servico-item-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
    }
    .servico-card-top {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 0.5rem;
    }
    .servico-name {
      font-size: 0.98rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
      line-height: 1.3;
    }
    .badge-ativo {
      font-size: 0.68rem;
      font-weight: 800;
      color: #10b981;
      border: 1px solid #10b981;
      padding: 0.15rem 0.45rem;
      border-radius: 10px;
      text-transform: uppercase;
    }
    .badge-inativo {
      color: #94a3b8;
      border-color: #94a3b8;
    }
    .servico-price-tag {
      font-size: 0.95rem;
      font-weight: 800;
      color: #7c3aed;
    }
    .servico-desc {
      font-size: 0.8rem;
      color: var(--text-secondary);
      line-height: 1.45;
      margin: 0;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
      min-height: 3.5em;
    }
    .servico-card-actions {
      display: flex;
      align-items: center;
      gap: 1rem;
      margin-top: auto;
      padding-top: 0.75rem;
      border-top: 1px solid var(--border-color);
      font-size: 0.78rem;
      font-weight: 700;
    }
    .action-btn-edit {
      background: none;
      border: none;
      color: #7c3aed;
      cursor: pointer;
      padding: 0;
      font-weight: 700;
    }
    .action-btn-toggle {
      background: none;
      border: none;
      color: var(--text-secondary);
      cursor: pointer;
      padding: 0;
      font-weight: 700;
    }
    .action-btn-delete {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      padding: 0;
      font-weight: 700;
    }

    /* Modal */
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
  `]
})
export class ServicosComponent implements OnInit {
  private api = inject(ApiService);

  servicos = signal<ServicoCatalogo[]>([]);
  servicosFiltrados = signal<ServicoCatalogo[]>([]);
  busca = '';

  modalOpen = signal(false);
  editandoId: number | null = null;
  servicoForm: Partial<ServicoCatalogo> = {
    nome: '',
    preco: 0,
    categoria: '',
    descricao: '',
    status: 'ATIVO',
  };

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.getServicos().subscribe((res) => {
      this.servicos.set(res);
      this.filtrar();
    });
  }

  filtrar(): void {
    const t = this.busca.toLowerCase().trim();
    if (!t) {
      this.servicosFiltrados.set(this.servicos());
      return;
    }
    this.servicosFiltrados.set(
      this.servicos().filter(
        (s) =>
          s.nome.toLowerCase().includes(t) ||
          (s.descricao && s.descricao.toLowerCase().includes(t)) ||
          (s.categoria && s.categoria.toLowerCase().includes(t))
      )
    );
  }

  openNovoModal(): void {
    this.editandoId = null;
    this.servicoForm = {
      nome: '',
      preco: 500,
      categoria: 'Planos',
      descricao: '',
      status: 'ATIVO',
    };
    this.modalOpen.set(true);
  }

  openEditarModal(s: ServicoCatalogo): void {
    this.editandoId = s.id || null;
    this.servicoForm = { ...s };
    this.modalOpen.set(true);
  }

  salvar(): void {
    if (!this.servicoForm.nome) return;

    if (this.editandoId) {
      this.api.updateServico(this.editandoId, this.servicoForm).subscribe(() => {
        this.modalOpen.set(false);
        this.carregar();
      });
    } else {
      this.api.createServico(this.servicoForm).subscribe(() => {
        this.modalOpen.set(false);
        this.carregar();
      });
    }
  }

  toggleAtivo(s: ServicoCatalogo): void {
    const novoStatus = s.status === 'ATIVO' ? 'INATIVO' : 'ATIVO';
    this.api.updateServico(s.id!, { status: novoStatus }).subscribe(() => {
      this.carregar();
    });
  }

  excluir(s: ServicoCatalogo): void {
    if (confirm(`Deseja excluir o serviço "${s.nome}"?`)) {
      this.api.deleteServico(s.id!).subscribe(() => {
        this.carregar();
      });
    }
  }
}
