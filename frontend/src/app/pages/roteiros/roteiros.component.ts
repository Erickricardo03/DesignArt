import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { ApiService } from '../../core/services/api.service';
import { Roteiro, Tarefa, Cliente, RoteiroCena } from '../../core/models';

@Component({
  selector: 'app-roteiros',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, HeaderComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>

      <main class="main-content">
        <app-header 
          title="Roteiros & Gravações" 
          subtitle="Crie scripts e compartilhe com a equipe no set."
          [showNewTaskButton]="true"
          (newTaskAction)="abrirModalCriacao()"
          (refreshAction)="carregarRoteiros()"
        ></app-header>

        <div class="page-body">
          <!-- Filtro de Busca -->
          <div class="card search-filter-bar">
            <div class="search-wrap-full">
              <i class="bi bi-search"></i>
              <input 
                type="text" 
                placeholder="Filtrar por título, cliente ou tarefa associada..." 
                [(ngModel)]="filtroLoja" 
                (input)="filtrar()"
              />
            </div>
            <button class="btn-create-script" (click)="abrirModalCriacao()">
              <i class="bi bi-plus-lg"></i> NOVO ROTEIRO
            </button>
          </div>

          <!-- Grid de Roteiros (Exatamente como em 16756.jpg) -->
          <div class="scripts-grid">
            <div 
              class="script-dark-card" 
              *ngFor="let r of roteirosFiltrados()"
            >
              <div class="script-card-header">
                <div class="script-title-area">
                  <span class="script-client-name">{{ r.clienteNome || r.loja }}</span>
                  <h2 class="script-heading">{{ r.titulo }}</h2>
                  <span class="script-creator">CRIADOR: {{ r.criadorNome || 'LUCAS MATHEUS' }}</span>
                  <span class="linked-task-pill" *ngIf="r.tarefaTitulo">
                    <i class="bi bi-link-45deg"></i> Tarefa: {{ r.tarefaTitulo }}
                  </span>
                </div>

                <!-- Botão Gravação Concluída (16756.jpg) -->
                <button 
                  class="btn-status-gravacao" 
                  [class.concluida]="r.status === 'CONCLUIDO' || r.feito"
                  (click)="toggleGravacao(r)"
                >
                  <i class="bi" [ngClass]="(r.status === 'CONCLUIDO' || r.feito) ? 'bi-check-circle-fill' : 'bi-circle'"></i>
                  <span>{{ (r.status === 'CONCLUIDO' || r.feito) ? 'GRAVAÇÃO CONCLUÍDA' : 'PENDENTE' }}</span>
                </button>
              </div>

              <!-- Cenas do Roteiro (16756.jpg) -->
              <div class="script-scenes-box">
                <div class="scene-line" *ngFor="let c of r.cenas">
                  <span class="scene-text">{{ c.descricao }}</span>
                </div>
                <div class="scene-line" *ngIf="!r.cenas || r.cenas.length === 0">
                  <span class="scene-text">{{ r.conteudoScript || 'Sem roteiro detalhado cadastrado.' }}</span>
                </div>
              </div>

              <div class="script-camera-note" *ngIf="r.instrucoesCamera">
                <i class="bi bi-camera-reels-fill text-purple"></i>
                <span><strong>Câmera:</strong> {{ r.instrucoesCamera }}</span>
              </div>

              <!-- Footer (16756.jpg) -->
              <div class="script-card-footer">
                <span class="script-date">
                  <i class="bi bi-calendar3"></i> {{ (r.dataGravacao || '2026-08-20') | date:'dd/MM/yyyy' }}
                </span>

                <div class="footer-btn-group">
                  <button class="btn-abrir-roteiro" (click)="abrirModalLeitura(r)">
                    <i class="bi bi-eye"></i> ABRIR ROTEIRO
                  </button>
                  <button class="btn-editar-roteiro" (click)="abrirModalEdicao(r)">
                    <i class="bi bi-pencil"></i> EDITAR
                  </button>
                  <button class="btn-excluir-roteiro" (click)="excluirRoteiro(r)" title="Excluir">
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- MODAL CRIAR / EDITAR ROTEIRO (Exatamente como em 16762.jpg) -->
    <div class="modal-backdrop" *ngIf="showModalCriacao()">
      <div class="modal-card modal-dark modal-lg animate-fade-in">
        <div class="modal-header modal-header-dark">
          <div>
            <h2 class="modal-title-white">CRIAR NOVO ROTEIRO DE GRAVAÇÃO</h2>
            <span class="modal-sub-white">Preencha as informações e redija o roteiro em tela cheia.</span>
          </div>
          <button class="btn-close-hdr" (click)="fecharModalCriacao()">VOLTAR PARA A LISTA</button>
        </div>

        <div class="modal-body modal-body-dark">
          <!-- TÍTULO DO ROTEIRO & CLIENTE VINCULADO (16762.jpg) -->
          <div class="form-grid-2">
            <div class="form-group">
              <label class="form-lbl-dark">TÍTULO DO ROTEIRO *</label>
              <input type="text" [(ngModel)]="roteiroForm.titulo" class="form-input-dark" placeholder="Ex: Lançamento Coleção Primavera" />
            </div>
            <div class="form-group">
              <label class="form-lbl-dark">CLIENTE VINCULADO *</label>
              <select [(ngModel)]="roteiroForm.clienteNome" (change)="onClienteChange()" class="form-input-dark">
                <option *ngFor="let c of clientes()" [value]="c.nome">{{ c.nome }}</option>
              </select>
            </div>
          </div>

          <!-- TAREFA ASSOCIADA & DIREÇÃO DE CÂMERA (16762.jpg) -->
          <div class="form-grid-2">
            <div class="form-group">
              <label class="form-lbl-dark highlight-purple">TAREFA ASSOCIADA (OPCIONAL)</label>
              <select [(ngModel)]="roteiroForm.tarefaId" class="form-input-dark">
                <option [ngValue]="undefined">Selecione a Tarefa</option>
                <option *ngFor="let t of tarefas()" [ngValue]="t.id">{{ t.titulo }} ({{ t.loja }})</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-lbl-dark">DIREÇÃO / INSTRUÇÕES DE CÂMERA</label>
              <input type="text" [(ngModel)]="roteiroForm.instrucoesCamera" class="form-input-dark" placeholder="Ex: Lente 50mm, luz suave quente..." />
            </div>
          </div>

          <!-- DATA DA GRAVAÇÃO & STATUS -->
          <div class="form-grid-2">
            <div class="form-group">
              <label class="form-lbl-dark">DATA DA GRAVAÇÃO</label>
              <input type="date" [(ngModel)]="roteiroForm.dataGravacao" class="form-input-dark" />
            </div>
            <div class="form-group">
              <label class="form-lbl-dark">STATUS</label>
              <select [(ngModel)]="roteiroForm.status" class="form-input-dark">
                <option value="PENDENTE">PENDENTE</option>
                <option value="EM_GRAVACAO">EM GRAVAÇÃO</option>
                <option value="CONCLUIDO">CONCLUÍDO</option>
              </select>
            </div>
          </div>

          <!-- CENAS DO ROTEIRO -->
          <div class="form-group">
            <div class="flex-between mb-2">
              <label class="form-lbl-dark">CENAS DETALHADAS DO SCRIPT</label>
              <button class="btn-add-scene-sm" (click)="adicionarCenaForm()">
                <i class="bi bi-plus"></i> Adicionar Cena
              </button>
            </div>

            <div class="scenes-inputs-stack">
              <div class="scene-input-row" *ngFor="let c of cenasForm; let idx = index">
                <span class="scene-index-badge">CENA {{ idx + 1 }}</span>
                <input type="text" [(ngModel)]="c.descricao" class="form-input-dark flex-1" placeholder="Ação, diálogo e enquadramento..." />
                <button class="btn-remove-scene" (click)="removerCenaForm(idx)"><i class="bi bi-x"></i></button>
              </div>
            </div>
          </div>

          <div class="modal-actions-right">
            <button class="btn-cancel-dark" (click)="fecharModalCriacao()">Cancelar</button>
            <button class="btn-save-dark" (click)="salvarRoteiro()">Salvar Roteiro</button>
          </div>
        </div>
      </div>
    </div>

    <!-- MODAL MODO LEITURA / SET NO CELULAR -->
    <div class="modal-backdrop" *ngIf="showModalLeitura()">
      <div class="modal-card modal-dark modal-lg animate-fade-in" *ngIf="roteiroSelecionado() as r">
        <div class="modal-header modal-header-dark">
          <div>
            <span class="modal-sub-white">{{ r.clienteNome || r.loja }}</span>
            <h2 class="modal-title-white">{{ r.titulo }}</h2>
          </div>
          <button class="btn-close-hdr" (click)="showModalLeitura.set(false)">FECHAR</button>
        </div>

        <div class="modal-body modal-body-dark">
          <div class="reader-mode-box">
            <div class="reader-scene" *ngFor="let c of r.cenas">
              <p class="reader-scene-text">{{ c.descricao }}</p>
            </div>
          </div>

          <div class="reader-camera-bar" *ngIf="r.instrucoesCamera">
            <strong>Instruções de Câmera & Luz:</strong>
            <p>{{ r.instrucoesCamera }}</p>
          </div>

          <div class="modal-actions-right">
            <button class="btn-status-gravacao concluida" (click)="toggleGravacao(r)">
              <i class="bi" [ngClass]="(r.status === 'CONCLUIDO' || r.feito) ? 'bi-check-circle-fill' : 'bi-circle'"></i>
              <span>{{ (r.status === 'CONCLUIDO' || r.feito) ? 'GRAVAÇÃO CONCLUÍDA' : 'MARCAR CONCLUÍDO' }}</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .app-container {
      display: flex;
      min-height: 100vh;
      background: var(--bg-primary);
    }
    .main-content {
      flex: 1;
      display: flex;
      flex-direction: column;
      min-width: 0;
    }
    .page-body {
      padding: 1.5rem 2rem;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    .search-filter-bar {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 0.85rem 1.25rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
    }
    .search-wrap-full {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      flex: 1;
      color: var(--text-secondary);
    }
    .search-wrap-full input {
      border: none;
      background: transparent;
      outline: none;
      width: 100%;
      color: var(--text-primary);
      font-size: 0.88rem;
    }
    .btn-create-script {
      background: #1e293b;
      color: #fff;
      border: none;
      padding: 0.65rem 1.25rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.82rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }

    /* Cards Escuros Idênticos à Imagem 16756.jpg */
    .scripts-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
      gap: 1.5rem;
    }
    .script-dark-card {
      background: #151c28;
      border: 1px solid #2d3748;
      border-radius: 14px;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1.2rem;
      box-shadow: 0 10px 30px rgba(0,0,0,0.3);
      position: relative;
    }
    .script-card-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 1rem;
    }
    .script-title-area {
      flex: 1;
    }
    .script-client-name {
      font-size: 0.75rem;
      font-weight: 800;
      color: #60a5fa;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      display: block;
      margin-bottom: 0.2rem;
    }
    .script-heading {
      font-size: 1.05rem;
      font-weight: 800;
      color: #ffffff;
      margin: 0 0 0.35rem 0;
      line-height: 1.35;
    }
    .script-creator {
      font-size: 0.7rem;
      font-weight: 700;
      color: #94a3b8;
      display: block;
    }
    .linked-task-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
      font-size: 0.68rem;
      font-weight: 700;
      color: #c084fc;
      background: rgba(192, 132, 252, 0.1);
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      margin-top: 0.4rem;
    }

    /* Botão Gravação Concluída (16756.jpg) */
    .btn-status-gravacao {
      background: rgba(16, 185, 129, 0.1);
      border: 1px solid #10b981;
      color: #34d399;
      padding: 0.45rem 0.85rem;
      border-radius: 20px;
      font-size: 0.72rem;
      font-weight: 800;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      white-space: nowrap;
      transition: all 0.2s;
    }
    .btn-status-gravacao.concluida {
      background: #064e3b;
      color: #34d399;
      border-color: #059669;
    }

    /* Caixa de Cenas (16756.jpg) */
    .script-scenes-box {
      background: #1e2736;
      border: 1px solid #2d3748;
      border-radius: 10px;
      padding: 1.1rem;
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
    }
    .scene-line {
      line-height: 1.45;
    }
    .scene-text {
      font-size: 0.84rem;
      color: #cbd5e1;
    }
    .script-camera-note {
      font-size: 0.78rem;
      color: #94a3b8;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      background: rgba(124, 58, 237, 0.08);
      padding: 0.5rem 0.75rem;
      border-radius: 6px;
    }
    .text-purple { color: #a78bfa; }

    /* Footer (16756.jpg) */
    .script-card-footer {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: auto;
      padding-top: 0.85rem;
      border-top: 1px solid #2d3748;
    }
    .script-date {
      font-size: 0.78rem;
      color: #94a3b8;
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .footer-btn-group {
      display: flex;
      align-items: center;
      gap: 0.6rem;
    }
    .btn-abrir-roteiro {
      background: linear-gradient(135deg, #7c3aed, #9333ea);
      color: #ffffff;
      border: none;
      padding: 0.55rem 1.1rem;
      border-radius: 8px;
      font-size: 0.78rem;
      font-weight: 800;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.35rem;
      box-shadow: 0 4px 12px rgba(124, 58, 237, 0.4);
    }
    .btn-editar-roteiro {
      background: #1e293b;
      border: 1px solid #475569;
      color: #ffffff;
      padding: 0.55rem 0.95rem;
      border-radius: 8px;
      font-size: 0.78rem;
      font-weight: 800;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.35rem;
    }
    .btn-excluir-roteiro {
      background: none;
      border: none;
      color: #f87171;
      font-size: 1rem;
      cursor: pointer;
      padding: 0.3rem;
    }

    /* Modais (16762.jpg) */
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
    .modal-dark {
      background: var(--bg-surface) !important;
      color: var(--text-primary) !important;
      border: 1.5px solid var(--border-color);
      border-radius: 16px;
      width: 100%;
      max-width: 720px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 25px 60px -15px rgba(0,0,0,0.5);
    }
    .modal-header-dark {
      padding: 1.25rem 1.75rem;
      border-bottom: 1.5px solid var(--border-color);
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: var(--bg-surface);
    }
    .modal-title-white {
      font-size: 1.35rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
    }
    .modal-sub-white {
      font-size: 0.8rem;
      color: var(--text-secondary);
      font-weight: 600;
    }
    .btn-close-hdr {
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      border-radius: 6px;
      padding: 0.4rem 0.85rem;
      font-weight: 800;
      font-size: 0.75rem;
      cursor: pointer;
      transition: all 0.2s;
    }
    .btn-close-hdr:hover {
      background: var(--bg-surface-hover);
      color: var(--primary);
    }
    .modal-body-dark {
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
    .form-lbl-dark {
      font-size: 0.8rem;
      font-weight: 800;
      color: var(--text-primary);
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }
    .highlight-purple {
      color: var(--primary);
    }
    .form-input-dark {
      width: 100%;
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
    .form-input-dark:focus {
      border-color: var(--primary);
      box-shadow: 0 0 0 3px var(--color-primary-light);
    }
    .flex-between {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .mb-2 { margin-bottom: 0.5rem; }
    .btn-add-scene-sm {
      background: var(--color-primary-light);
      border: 1px solid var(--primary);
      color: var(--primary);
      padding: 0.25rem 0.65rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 700;
      cursor: pointer;
    }
    .scenes-inputs-stack {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .scene-input-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .scene-index-badge {
      font-size: 0.75rem;
      font-weight: 800;
      background: var(--bg-surface-elevated);
      color: var(--text-primary);
      border: 1px solid var(--border-color);
      padding: 0.5rem 0.75rem;
      border-radius: 6px;
      white-space: nowrap;
    }
    .btn-remove-scene {
      background: none;
      border: none;
      color: #ef4444;
      font-size: 1.1rem;
      cursor: pointer;
    }

    .modal-actions-right {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      margin-top: 0.75rem;
    }
    .btn-cancel-dark {
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
    .btn-cancel-dark:hover {
      background: var(--bg-surface-hover);
      border-color: var(--border-subtle);
    }
    .btn-save-dark {
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
    .btn-save-dark:hover {
      box-shadow: 0 6px 20px rgba(124, 58, 237, 0.5);
      filter: brightness(1.06);
      transform: translateY(-1px);
    }

    /* Leitor */
    .reader-mode-box {
      background: var(--bg-surface-elevated);
      border: 1.5px solid var(--border-color);
      border-radius: 12px;
      padding: 1.5rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .reader-scene-text {
      font-size: 1.05rem;
      line-height: 1.6;
      color: var(--text-primary);
      font-weight: 600;
      margin: 0;
    }
    .reader-camera-bar {
      background: var(--bg-surface);
      border-left: 4px solid var(--primary);
      border: 1px solid var(--border-color);
      border-left-width: 4px;
      border-radius: 8px;
      padding: 0.85rem 1rem;
      font-size: 0.88rem;
      color: var(--text-secondary);
      font-weight: 600;
    }
  `]
})
export class RoteirosComponent implements OnInit {
  private api = inject(ApiService);

  roteiros = signal<Roteiro[]>([]);
  roteirosFiltrados = signal<Roteiro[]>([]);
  tarefas = signal<Tarefa[]>([]);
  clientes = signal<Cliente[]>([]);
  filtroLoja = '';

  showModalCriacao = signal(false);
  showModalLeitura = signal(false);
  roteiroSelecionado = signal<Roteiro | null>(null);
  editandoId: number | null = null;

  cenasForm: RoteiroCena[] = [];

  roteiroForm: Partial<Roteiro> = {
    titulo: '',
    loja: 'ATELIÊ DA YSA',
    clienteNome: 'ATELIÊ DA YSA',
    instrucoesCamera: 'Lente 50mm, luz suave quente...',
    dataGravacao: '2026-09-20',
    status: 'PENDENTE',
  };

  ngOnInit(): void {
    this.carregarRoteiros();
    this.carregarAuxiliares();
  }

  carregarRoteiros(): void {
    this.api.getRoteiros().subscribe((res) => {
      this.roteiros.set(res);
      this.filtrar();
    });
  }

  carregarAuxiliares(): void {
    this.api.getTarefas().subscribe((t) => this.tarefas.set(t));
    this.api.getClientes().subscribe((c) => this.clientes.set(c));
  }

  filtrar(): void {
    const t = this.filtroLoja.toLowerCase().trim();
    if (!t) {
      this.roteirosFiltrados.set(this.roteiros());
      return;
    }
    this.roteirosFiltrados.set(
      this.roteiros().filter(
        (r) =>
          r.titulo.toLowerCase().includes(t) ||
          r.loja.toLowerCase().includes(t) ||
          (r.tarefaTitulo && r.tarefaTitulo.toLowerCase().includes(t)) ||
          (r.criadorNome && r.criadorNome.toLowerCase().includes(t))
      )
    );
  }

  onClienteChange(): void {
    if (this.roteiroForm.clienteNome) {
      this.roteiroForm.loja = this.roteiroForm.clienteNome;
    }
  }

  abrirModalCriacao(): void {
    this.editandoId = null;
    this.roteiroForm = {
      titulo: '',
      loja: 'ACADEMIA TITANIUM',
      clienteNome: 'ACADEMIA TITANIUM',
      instrucoesCamera: 'Ex: Lente 50mm, luz suave quente...',
      dataGravacao: '2026-09-20',
      status: 'PENDENTE',
      tarefaId: undefined,
    };
    this.cenasForm = [
      { ordem: 1, descricao: 'CENA 1: Entrada dinâmica no espaço com enquadramento amplo.' },
      { ordem: 2, descricao: 'CENA 2: Detalhes dos equipamentos e interação com clientes.' },
      { ordem: 3, descricao: 'CENA 3: Encerramento com chamada para ação e logo da marca.' },
    ];
    this.showModalCriacao.set(true);
  }

  abrirModalEdicao(roteiro: Roteiro): void {
    this.editandoId = roteiro.id || null;
    this.roteiroForm = { ...roteiro };
    this.cenasForm = roteiro.cenas ? [...roteiro.cenas] : [];
    this.showModalCriacao.set(true);
  }

  fecharModalCriacao(): void {
    this.showModalCriacao.set(false);
  }

  adicionarCenaForm(): void {
    this.cenasForm.push({
      ordem: this.cenasForm.length + 1,
      descricao: `CENA ${this.cenasForm.length + 1}: `,
    });
  }

  removerCenaForm(idx: number): void {
    this.cenasForm.splice(idx, 1);
  }

  salvarRoteiro(): void {
    if (!this.roteiroForm.titulo) return;

    this.roteiroForm.cenas = this.cenasForm;
    this.roteiroForm.loja = this.roteiroForm.clienteNome || this.roteiroForm.loja || 'Cliente';

    // Se selecionou tarefa associada, preenche o título da tarefa
    if (this.roteiroForm.tarefaId) {
      const t = this.tarefas().find((item) => item.id === this.roteiroForm.tarefaId);
      if (t) this.roteiroForm.tarefaTitulo = t.titulo;
    }

    if (this.editandoId) {
      this.api.updateRoteiro(this.editandoId, this.roteiroForm).subscribe(() => {
        this.showModalCriacao.set(false);
        this.carregarRoteiros();
      });
    } else {
      this.api.createRoteiro(this.roteiroForm).subscribe(() => {
        this.showModalCriacao.set(false);
        this.carregarRoteiros();
      });
    }
  }

  abrirModalLeitura(r: Roteiro): void {
    this.roteiroSelecionado.set(r);
    this.showModalLeitura.set(true);
  }

  toggleGravacao(r: Roteiro): void {
    this.api.toggleGravacaoRoteiro(r.id!).subscribe((atualizado) => {
      this.carregarRoteiros();
      if (this.roteiroSelecionado()?.id === r.id) {
        this.roteiroSelecionado.set(atualizado);
      }
    });
  }

  excluirRoteiro(r: Roteiro): void {
    if (confirm(`Deseja excluir o roteiro "${r.titulo}"?`)) {
      this.api.deleteRoteiro(r.id!).subscribe(() => {
        this.carregarRoteiros();
      });
    }
  }
}
