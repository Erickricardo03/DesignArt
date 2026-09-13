import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Colaborador } from '../../core/models';
import { HeaderComponent } from '../../shared/components/header.component';
import { SidebarComponent } from '../../shared/components/sidebar.component';

@Component({
  selector: 'app-equipe',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-header 
          title="Gestão de Equipe & Permissões" 
          subtitle="Acessos, cargos e folha salarial da agência"
          (refreshAction)="carregar()"
        ></app-header>

        <main class="page-body">
          <div class="equipe-layout-grid">
            <!-- Card da Esquerda (Exatamente como em 16710.jpg) -->
            <div class="equipe-sidebar-card">
              <span class="badge-subtitle">GESTÃO DE EQUIPE</span>
              <h1 class="card-side-title">Cadastre novos logins, defina permissões e salários.</h1>
              
              <button class="btn-novo-colaborador" (click)="openNovoModal()">
                <i class="bi bi-plus-lg"></i> NOVO COLABORADOR
              </button>

              <div class="folha-summary-card">
                <span class="folha-lbl">Folha Salarial Mensal</span>
                <span class="folha-val">R$ {{ folhaSalarialTotal().toFixed(2).replace('.', ',') }}</span>
                <span class="folha-sub">{{ colaboradores().length }} profissionais ativos</span>
              </div>
            </div>

            <!-- Coluna Principal: Lista de Membros da Equipe -->
            <div class="equipe-main-content">
              <div class="equipe-header-bar">
                <h2 class="section-main-title">COLABORADORES & ACESSOS</h2>
                <div class="search-wrap">
                  <input 
                    type="text" 
                    placeholder="Buscar colaborador..." 
                    [(ngModel)]="busca" 
                    (input)="filtrar()"
                  />
                  <i class="bi bi-search"></i>
                </div>
              </div>

              <!-- Grid de Colaboradores -->
              <div class="colaboradores-grid stagger-grid">
                <div class="colaborador-card hover-lift" *ngFor="let c of colaboradoresFiltrados()">
                  <div class="colab-top">
                    <div class="colab-avatar-wrap">
                      <img *ngIf="c.avatarUrl" [src]="c.avatarUrl" [alt]="c.nomeCompleto" />
                      <div *ngIf="!c.avatarUrl" class="avatar-fallback">
                        {{ c.nomeCompleto.charAt(0).toUpperCase() }}
                      </div>
                    </div>
                    <div class="colab-info">
                      <h3 class="colab-name">{{ c.nomeCompleto }}</h3>
                      <span class="colab-cargo">{{ c.cargo }}</span>
                      <span class="colab-email">{{ c.email }}</span>
                    </div>
                  </div>

                  <div class="colab-meta">
                    <div class="meta-item">
                      <span class="meta-label">Salário / Remuneração:</span>
                      <span class="meta-val salary-highlight">R$ {{ c.salario.toFixed(2).replace('.', ',') }}</span>
                    </div>
                    <div class="meta-item">
                      <span class="meta-label">Nível de Acesso:</span>
                      <span class="role-badge" [ngClass]="c.role.toLowerCase()">
                        {{ c.role }}
                      </span>
                    </div>
                    <div class="meta-item">
                      <span class="meta-label">Usuário Login:</span>
                      <span class="meta-val font-mono">{{ c.username }}</span>
                    </div>
                  </div>

                  <div class="colab-actions">
                    <button class="btn-edit-colab" (click)="openEditarModal(c)">
                      <i class="bi bi-pencil"></i> Editar
                    </button>
                    <button class="btn-del-colab" (click)="excluir(c)" *ngIf="c.username !== 'igorsantos'">
                      <i class="bi bi-trash"></i> Excluir
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>

    <!-- Modal Novo / Editar Colaborador -->
    <div class="modal-backdrop" *ngIf="modalOpen()">
      <div class="modal-card animate-fade-in">
        <div class="modal-header">
          <h3 class="modal-title">{{ editandoId ? 'Editar Colaborador' : 'Novo Colaborador' }}</h3>
          <button class="close-btn" (click)="modalOpen.set(false)"><i class="bi bi-x-lg"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>NOME COMPLETO *</label>
            <input type="text" [(ngModel)]="colabForm.nomeCompleto" class="form-control" placeholder="Ex: Igor Santos" />
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>LOGIN / USUÁRIO *</label>
              <input type="text" [(ngModel)]="colabForm.username" class="form-control" placeholder="igorsantos" />
            </div>
            <div class="form-group">
              <label>E-MAIL *</label>
              <input type="email" [(ngModel)]="colabForm.email" class="form-control" placeholder="colaborador@designarte.com" />
            </div>
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>CARGO / NÍVEL OPERACIONAL *</label>
              <input type="text" [(ngModel)]="colabForm.cargo" class="form-control" placeholder="Ex: Videomaker & Editora" />
            </div>
            <div class="form-group">
              <label>SALÁRIO MENSAL (R$) *</label>
              <input type="number" [(ngModel)]="colabForm.salario" class="form-control" placeholder="1000.00" />
            </div>
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>PERMISSÃO / PAPEL NO SISTEMA</label>
              <select [(ngModel)]="colabForm.role" class="form-control">
                <option value="ADMIN">ADMIN (Acesso Total)</option>
                <option value="OPERACIONAL">OPERACIONAL (Edição / Roteiro)</option>
                <option value="FOTOGRAFO">FOTÓGRAFO (Eventos / Upload)</option>
                <option value="CLIENTE">CLIENTE (Apenas Visualização)</option>
              </select>
            </div>
            <div class="form-group">
              <label>FOTO DE PERFIL / AVATAR</label>
              <div class="custom-file-upload-box" (click)="avatarFileInput.click()">
                <input 
                  type="file" 
                  #avatarFileInput 
                  (change)="onAvatarFileSelected($event)" 
                  accept="image/*,.png,.jpg,.jpeg,.webp,.gif,.bmp,.svg,.ico,.tiff,.heic" 
                  style="display: none" 
                />
                <div *ngIf="!colabForm.avatarUrl" class="upload-placeholder">
                  <i class="bi bi-person-bounding-box text-primary" style="font-size: 1.8rem;"></i>
                  <span class="upload-title">Selecionar foto no computador</span>
                  <small class="upload-sub">Formatos aceitos: PNG, JPG, JPEG, WEBP, etc.</small>
                </div>
                <div *ngIf="colabForm.avatarUrl" class="upload-preview-container" (click)="$event.stopPropagation()">
                  <img [src]="colabForm.avatarUrl" alt="Avatar preview" class="preview-avatar-circle" />
                  <div class="preview-meta">
                    <span class="preview-title"><i class="bi bi-check-circle-fill text-success"></i> Foto selecionada</span>
                    <div class="preview-actions">
                      <button type="button" class="btn-action-small btn-trocar" (click)="avatarFileInput.click()">
                        <i class="bi bi-arrow-repeat"></i> Trocar
                      </button>
                      <button type="button" class="btn-action-small btn-remover" (click)="colabForm.avatarUrl = ''">
                        <i class="bi bi-trash"></i> Remover
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="modal-actions-right">
            <button class="btn-cancel" (click)="modalOpen.set(false)">Cancelar</button>
            <button class="btn-save" (click)="salvar()">Salvar Colaborador</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .page-body {
      padding: 2rem;
    }
    .equipe-layout-grid {
      display: grid;
      grid-template-columns: 320px 1fr;
      gap: 2rem;
      align-items: start;
    }
    .equipe-layout-grid > * {
      min-width: 0;
    }
    @media (max-width: 960px) {
      .equipe-layout-grid {
        grid-template-columns: 1fr;
      }
    }

    /* Card da Esquerda (16710.jpg) */
    .equipe-sidebar-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-left: 4px solid var(--primary);
      border-radius: 12px;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
      min-width: 0;
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
    .btn-novo-colaborador {
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
    .btn-novo-colaborador:hover {
      background: #0f172a;
      transform: translateY(-1px);
    }
    .folha-summary-card {
      background: rgba(124, 58, 237, 0.06);
      border: 1px solid rgba(124, 58, 237, 0.15);
      border-radius: 10px;
      padding: 1.1rem;
      display: flex;
      flex-direction: column;
      gap: 0.3rem;
    }
    .folha-lbl {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--text-secondary);
      text-transform: uppercase;
    }
    .folha-val {
      font-size: 1.35rem;
      font-weight: 900;
      color: var(--primary);
    }
    .folha-sub {
      font-size: 0.75rem;
      color: var(--text-secondary);
    }

    /* Painel Principal */
    .equipe-header-bar {
      display: flex;
      flex-wrap: wrap;
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
      max-width: 100%;
      flex: 1 1 200px;
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
    .colaboradores-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(310px, 1fr));
      gap: 1.25rem;
    }
    .colaborador-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.35rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
      display: flex;
      flex-direction: column;
      gap: 1rem;
      transition: transform 0.2s, box-shadow 0.2s;
    }
    .colaborador-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
    }
    .colab-top {
      display: flex;
      align-items: center;
      gap: 0.85rem;
    }
    .colab-avatar-wrap {
      width: 50px;
      height: 50px;
      border-radius: 50%;
      overflow: hidden;
      background: #7c3aed;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .colab-avatar-wrap img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .avatar-fallback {
      color: #fff;
      font-weight: 800;
      font-size: 1.2rem;
    }
    .colab-info {
      flex: 1;
      overflow: hidden;
    }
    .colab-name {
      font-size: 1.02rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 0.15rem 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .colab-cargo {
      font-size: 0.78rem;
      font-weight: 700;
      color: var(--primary);
      display: block;
      margin-bottom: 0.15rem;
    }
    .colab-email {
      font-size: 0.72rem;
      color: var(--text-secondary);
      display: block;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .colab-meta {
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
      padding: 0.75rem 0;
      border-top: 1px solid var(--border-color);
      border-bottom: 1px solid var(--border-color);
      font-size: 0.8rem;
    }
    .meta-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .meta-label {
      color: var(--text-secondary);
    }
    .meta-val {
      color: var(--text-primary);
      font-weight: 600;
    }
    .salary-highlight {
      color: #10b981;
      font-weight: 800;
    }
    .font-mono {
      font-family: monospace;
      font-size: 0.82rem;
    }
    .role-badge {
      font-size: 0.68rem;
      font-weight: 800;
      padding: 0.15rem 0.45rem;
      border-radius: 10px;
      text-transform: uppercase;
    }
    .role-badge.admin { background: rgba(124, 58, 237, 0.15); color: #7c3aed; border: 1px solid rgba(124, 58, 237, 0.3); }
    .role-badge.operacional { background: rgba(2, 132, 199, 0.15); color: #0284c7; border: 1px solid rgba(2, 132, 199, 0.3); }
    .role-badge.fotografo { background: rgba(217, 119, 6, 0.15); color: #d97706; border: 1px solid rgba(217, 119, 6, 0.3); }
    .role-badge.cliente { background: rgba(100, 116, 139, 0.15); color: var(--text-secondary); border: 1px solid var(--border-color); }

    .colab-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
    }
    .btn-edit-colab {
      background: none;
      border: 1px solid var(--border-color);
      padding: 0.35rem 0.75rem;
      border-radius: 6px;
      font-size: 0.76rem;
      font-weight: 700;
      color: var(--text-primary);
      cursor: pointer;
    }
    .btn-del-colab {
      background: none;
      border: 1px solid #fee2e2;
      padding: 0.35rem 0.75rem;
      border-radius: 6px;
      font-size: 0.76rem;
      font-weight: 700;
      color: #ef4444;
      cursor: pointer;
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
    .preview-avatar-circle {
      width: 64px;
      height: 64px;
      object-fit: cover;
      border-radius: 50%;
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
export class EquipeComponent implements OnInit {
  private api = inject(ApiService);

  colaboradores = signal<Colaborador[]>([]);
  colaboradoresFiltrados = signal<Colaborador[]>([]);
  busca = '';

  modalOpen = signal(false);
  editandoId: number | null = null;
  colabForm: Partial<Colaborador> = {
    nomeCompleto: '',
    username: '',
    email: '',
    cargo: '',
    salario: 1000,
    role: 'OPERACIONAL',
    ativo: true,
    avatarUrl: '',
  };

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.getColaboradores().subscribe((res) => {
      this.colaboradores.set(res);
      this.filtrar();
    });
  }

  folhaSalarialTotal(): number {
    return this.colaboradores()
      .filter((c) => c.ativo)
      .reduce((acc, c) => acc + c.salario, 0);
  }

  filtrar(): void {
    const t = this.busca.toLowerCase().trim();
    if (!t) {
      this.colaboradoresFiltrados.set(this.colaboradores());
      return;
    }
    this.colaboradoresFiltrados.set(
      this.colaboradores().filter(
        (c) =>
          c.nomeCompleto.toLowerCase().includes(t) ||
          c.cargo.toLowerCase().includes(t) ||
          c.email.toLowerCase().includes(t)
      )
    );
  }

  openNovoModal(): void {
    this.editandoId = null;
    this.colabForm = {
      nomeCompleto: '',
      username: '',
      email: '',
      cargo: 'Videomaker & Editor',
      salario: 800,
      role: 'OPERACIONAL',
      ativo: true,
      avatarUrl: '',
    };
    this.modalOpen.set(true);
  }

  openEditarModal(c: Colaborador): void {
    this.editandoId = c.id || null;
    this.colabForm = { ...c };
    this.modalOpen.set(true);
  }

  onAvatarFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const reader = new FileReader();
      reader.onload = (e: ProgressEvent<FileReader>) => {
        this.colabForm.avatarUrl = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  salvar(): void {
    if (!this.colabForm.nomeCompleto) return;

    if (this.editandoId) {
      this.api.updateColaborador(this.editandoId, this.colabForm).subscribe(() => {
        this.modalOpen.set(false);
        this.carregar();
      });
    } else {
      this.api.createColaborador(this.colabForm).subscribe(() => {
        this.modalOpen.set(false);
        this.carregar();
      });
    }
  }

  excluir(c: Colaborador): void {
    if (confirm(`Deseja excluir o colaborador "${c.nomeCompleto}"?`)) {
      this.api.deleteColaborador(c.id!).subscribe(() => {
        this.carregar();
      });
    }
  }
}
