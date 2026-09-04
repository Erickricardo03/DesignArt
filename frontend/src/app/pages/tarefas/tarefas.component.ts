import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { ApiService } from '../../core/services/api.service';
import { Tarefa, ChecklistItem } from '../../core/models';

@Component({
  selector: 'app-tarefas',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, HeaderComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>

      <main class="main-content">
        <app-header 
          title="Gestão de Tarefas & Produção" 
          subtitle="Acompanhamento operacional de demandas, roteiros e checklist de entregas"
          [showNewTaskButton]="true"
          (newTaskAction)="abrirModalCriacao()"
          (refreshAction)="carregarTarefas()"
        ></app-header>

        <div class="page-body">
          <!-- Filtros de Busca -->
          <div class="card filters-card">
            <div class="filters-grid">
              <div class="filter-item">
                <label class="form-label">Buscar Loja / Cliente</label>
                <div class="input-with-icon">
                  <input 
                    type="text" 
                    class="form-control" 
                    placeholder="Ex: JM Moda Fitness, Ateliê da Ysa..."
                    [(ngModel)]="filtroLoja" 
                    (input)="carregarTarefas()"
                  />
                </div>
              </div>

              <div class="filter-item">
                <label class="form-label">Status</label>
                <select class="form-select" [(ngModel)]="filtroStatus" (change)="carregarTarefas()">
                  <option value="">Todos os Status</option>
                  <option value="A_FAZER">A Fazer</option>
                  <option value="EM_DESENVOLVIMENTO">Em Desenvolvimento</option>
                  <option value="EM_REVISAO">Em Revisão</option>
                  <option value="NAO_HOMOLOGADA">Não Homologada</option>
                  <option value="ATRASADA">Atrasadas</option>
                  <option value="CONCLUIDA">Concluídas</option>
                </select>
              </div>

              <div class="filter-item">
                <label class="form-label">Prioridade</label>
                <select class="form-select" [(ngModel)]="filtroPrioridade" (change)="carregarTarefas()">
                  <option value="">Todas as Prioridades</option>
                  <option value="BAIXA">Baixa</option>
                  <option value="MEDIA">Média</option>
                  <option value="ALTA">Alta</option>
                  <option value="URGENTE">Urgente</option>
                </select>
              </div>
            </div>
          </div>

          <!-- Tabela de Tarefas -->
          <div class="card table-card">
            <div class="table-scroll-hint">
              <i class="bi bi-arrows-expand" style="transform: rotate(45deg);"></i>
              <span>Use a barra de rolagem horizontal abaixo para visualizar todas as informações e ações da demanda</span>
            </div>

            <div class="table-responsive">
              <table class="custom-table tarefas-table">
                <thead>
                  <tr>
                    <th style="width: 150px;">Status</th>
                    <th style="min-width: 260px;">Título & Loja</th>
                    <th style="width: 120px;">Prioridade</th>
                    <th style="min-width: 220px;">Responsáveis</th>
                    <th style="width: 170px;">Gravação / Entrega</th>
                    <th style="width: 180px;">Progresso (%)</th>
                    <th style="width: 230px; text-align: right;">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let tarefa of tarefas()" [class.row-overdue]="isOverdue(tarefa)">
                    <td>
                      <span class="badge" [ngClass]="getStatusBadgeClass(tarefa.status)">
                        {{ getStatusLabel(tarefa.status) }}
                      </span>
                    </td>
                    <td>
                      <div class="task-title-cell">
                        <strong>{{ tarefa.titulo }}</strong>
                        <span class="store-tag"><i class="bi bi-shop"></i> {{ tarefa.loja }}</span>
                      </div>
                    </td>
                    <td>
                      <span class="badge" [ngClass]="getPrioridadeBadgeClass(tarefa.prioridade)">
                        {{ tarefa.prioridade }}
                      </span>
                    </td>
                    <td>
                      <div class="responsaveis-tags">
                        <span class="resp-pill" *ngFor="let r of tarefa.responsaveis">
                          {{ r }}
                        </span>
                        <span class="text-muted text-xs" *ngIf="!tarefa.responsaveis?.length">-</span>
                      </div>
                    </td>
                    <td>
                      <div class="dates-cell">
                        <span *ngIf="tarefa.dataGravacao" class="text-xs text-muted">
                          <i class="bi bi-camera-video"></i> {{ tarefa.dataGravacao | date:'dd/MM/yyyy' }}
                        </span>
                        <span class="text-sm font-semibold" [class.text-danger]="isOverdue(tarefa)">
                          <i class="bi bi-calendar-event"></i> {{ tarefa.dataEntrega | date:'dd/MM/yyyy' }}
                        </span>
                      </div>
                    </td>
                    <td>
                      <div class="progress-cell">
                        <div class="progress-bar-container">
                          <div class="progress-bar-fill" [style.width.%]="tarefa.percentualConcluido || 0"></div>
                        </div>
                        <span class="progress-text">{{ tarefa.percentualConcluido || 0 }}%</span>
                      </div>
                    </td>
                    <td style="text-align: right;">
                      <div class="action-buttons-group">
                        <button class="btn btn-secondary btn-sm" (click)="abrirChecklistModal(tarefa)" title="Ver Checklist & Briefing">
                          <i class="bi bi-list-check"></i>
                          <span>Checklist</span>
                        </button>
                        <button class="btn btn-secondary btn-sm" (click)="abrirModalEdicao(tarefa)" title="Editar Tarefa">
                          <i class="bi bi-pencil-square"></i>
                          <span>Editar</span>
                        </button>
                        <button 
                          *ngIf="tarefa.status !== 'CONCLUIDA'" 
                          class="btn btn-success btn-sm" 
                          (click)="concluirTarefa(tarefa)" 
                          title="Marcar como Concluída"
                        >
                          <i class="bi bi-check-lg"></i>
                        </button>
                        <button class="btn btn-outline-danger btn-sm" (click)="excluirTarefa(tarefa)" title="Excluir">
                          <i class="bi bi-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>

                  <tr *ngIf="tarefas().length === 0">
                    <td colspan="7" class="text-center py-5">
                      <i class="bi bi-inbox text-muted" style="font-size: 2.5rem;"></i>
                      <p class="text-muted mt-2">Nenhuma tarefa encontrada com os filtros selecionados.</p>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- MODAL 1: CHECKLIST DA TAREFA & BRIEFING (Padrão Páginas 2 e 3 do PDF) -->
    <div class="modal-backdrop" *ngIf="modalChecklistAberto()" (click)="fecharChecklistModal()">
      <div class="modal-content modal-lg" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <div>
            <span class="badge" [ngClass]="getStatusBadgeClass(tarefaSelecionada()?.status || '')">
              {{ getStatusLabel(tarefaSelecionada()?.status || '') }}
            </span>
            <h3 class="mt-1 mb-0">{{ tarefaSelecionada()?.titulo }}</h3>
            <span class="store-badge-sub">{{ tarefaSelecionada()?.loja }}</span>
          </div>
          <button class="btn-ghost btn-icon" (click)="fecharChecklistModal()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <div class="modal-body">
          <!-- Detalhes do Prazo & Responsáveis (Página 3) -->
          <div class="task-details-summary">
            <div>
              <span class="text-muted text-xs">RESPONSÁVEIS:</span>
              <p class="font-semibold mb-0">
                {{ tarefaSelecionada()?.responsaveis?.join(', ') || 'Nenhum atribuído' }}
              </p>
            </div>
            <div>
              <span class="text-muted text-xs">PRAZO DE ENTREGA:</span>
              <p class="font-semibold text-primary mb-0">
                {{ tarefaSelecionada()?.dataEntrega | date:'dd/MM/yyyy' }}
              </p>
            </div>
            <div *ngIf="tarefaSelecionada()?.dataGravacao">
              <span class="text-muted text-xs">DATA DA GRAVAÇÃO:</span>
              <p class="font-semibold mb-0">
                {{ tarefaSelecionada()?.dataGravacao | date:'dd/MM/yyyy' }}
              </p>
            </div>
          </div>

          <!-- CHECKLIST DA TAREFA -->
          <div class="checklist-box">
            <div class="checklist-header">
              <h4>CHECKLIST DA TAREFA</h4>
              <span class="completion-badge">
                {{ tarefaSelecionada()?.percentualConcluido || 0 }}% completo
              </span>
            </div>

            <div class="progress-bar-container mb-3">
              <div class="progress-bar-fill" [style.width.%]="tarefaSelecionada()?.percentualConcluido || 0"></div>
            </div>

            <div class="checklist-items-list">
              <div 
                class="checklist-item" 
                *ngFor="let item of tarefaSelecionada()?.checklist"
                [class.item-done]="item.concluido"
                (click)="toggleChecklistItem(item)"
              >
                <input 
                  type="checkbox" 
                  [checked]="item.concluido" 
                  (click)="$event.stopPropagation(); toggleChecklistItem(item)"
                />
                <span class="item-desc">{{ item.descricao }}</span>
              </div>
            </div>
          </div>

          <!-- DESCRIÇÃO BRIEFING (Página 2) -->
          <div class="briefing-box mt-4">
            <h5 class="briefing-title">DESCRIÇÃO BRIEFING</h5>
            <div class="briefing-content">
              <p>{{ tarefaSelecionada()?.briefing || tarefaSelecionada()?.descricao || 'Sem briefing detalhado cadastrado.' }}</p>
            </div>
          </div>
        </div>

        <div class="modal-footer">
          <button class="btn btn-outline-danger" (click)="excluirTarefa(tarefaSelecionada()!); fecharChecklistModal()">
            <i class="bi bi-trash"></i>
            <span>EXCLUIR</span>
          </button>
          <button class="btn btn-secondary" (click)="fecharChecklistModal()">
            FECHAR
          </button>
        </div>
      </div>
    </div>

    <!-- MODAL 2: CRIAÇÃO / EDIÇÃO DE TAREFA -->
    <div class="modal-backdrop" *ngIf="modalFormAberto()" (click)="fecharModalForm()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3>{{ emEdicao() ? 'Editar Tarefa' : 'Nova Tarefa' }}</h3>
          <button class="btn-ghost btn-icon" (click)="fecharModalForm()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <form (ngSubmit)="salvarTarefa()">
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">TÍTULO DA TAREFA</label>
              <input type="text" class="form-control" [(ngModel)]="formTarefa.titulo" name="titulo" required placeholder="Ex: Produção de conteúdo de marketing" />
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">LOJA / CLIENTE</label>
                <input type="text" class="form-control" [(ngModel)]="formTarefa.loja" name="loja" required placeholder="Ex: ATELIÊ DA YSA" />
              </div>

              <div class="form-group">
                <label class="form-label">PRIORIDADE</label>
                <select class="form-select" [(ngModel)]="formTarefa.prioridade" name="prioridade">
                  <option value="BAIXA">BAIXA</option>
                  <option value="MEDIA">MÉDIA</option>
                  <option value="ALTA">ALTA</option>
                  <option value="URGENTE">URGENTE</option>
                </select>
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">DATA DA GRAVAÇÃO</label>
                <input type="date" class="form-control" [(ngModel)]="formTarefa.dataGravacao" name="dataGravacao" />
              </div>

              <div class="form-group">
                <label class="form-label">DATA DE ENTREGA / PRAZO</label>
                <input type="date" class="form-control" [(ngModel)]="formTarefa.dataEntrega" name="dataEntrega" required />
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">STATUS</label>
                <select class="form-select" [(ngModel)]="formTarefa.status" name="status">
                  <option value="A_FAZER">A Fazer</option>
                  <option value="EM_DESENVOLVIMENTO">Em Desenvolvimento</option>
                  <option value="EM_REVISAO">Em Revisão</option>
                  <option value="NAO_HOMOLOGADA">Não Homologada</option>
                  <option value="ATRASADA">Atrasada</option>
                  <option value="CONCLUIDA">Concluída</option>
                </select>
              </div>

              <div class="form-group">
                <label class="form-label">RESPONSÁVEIS (separados por vírgula)</label>
                <input type="text" class="form-control" [(ngModel)]="responsaveisInput" name="responsaveisInput" placeholder="Ex: Edyllaine Silva, Igor Santos" />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">DESCRIÇÃO & BRIEFING DETALHADO</label>
              <textarea class="form-control" rows="4" [(ngModel)]="formTarefa.briefing" name="briefing" placeholder="Instruções de gravação, roteiro, formatos esperados..."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="fecharModalForm()">Cancelar</button>
            <button type="submit" class="btn btn-primary">Salvar Tarefa</button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .filters-card {
      margin-bottom: 1.25rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
      overflow: hidden;
      box-sizing: border-box;
    }

    .table-card {
      width: 100%;
      max-width: 100%;
      min-width: 0;
      overflow: hidden;
      box-sizing: border-box;
    }

    .table-responsive {
      width: 100%;
      max-width: 100%;
      overflow-x: auto;
      overflow-y: hidden;
      padding-bottom: 0.85rem;
      scrollbar-width: thin;
      scrollbar-color: var(--color-primary) var(--bg-surface-elevated);
      display: block;
      -webkit-overflow-scrolling: touch;
      box-sizing: border-box;
    }

    .table-responsive::-webkit-scrollbar {
      height: 10px;
    }

    .table-responsive::-webkit-scrollbar-track {
      background: var(--bg-surface-elevated);
      border-radius: 8px;
    }

    .table-responsive::-webkit-scrollbar-thumb {
      background: var(--color-primary);
      border-radius: 8px;
    }

    .tarefas-table {
      width: 100%;
      min-width: 1280px;
      border-collapse: separate;
      border-spacing: 0;
    }

    .tarefas-table th, .tarefas-table td {
      white-space: nowrap;
    }

    .filters-grid {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr;
      gap: 1.25rem;
    }

    .task-title-cell {
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }

    .store-tag {
      font-size: 0.75rem;
      color: var(--text-secondary);
      font-weight: 600;
    }

    .responsaveis-tags {
      display: flex;
      gap: 0.35rem;
      flex-wrap: wrap;
    }

    .resp-pill {
      font-size: 0.725rem;
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      padding: 0.2rem 0.5rem;
      border-radius: var(--radius-sm);
      color: var(--text-primary);
    }

    .dates-cell {
      display: flex;
      flex-direction: column;
      gap: 0.15rem;
    }

    .progress-cell {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .progress-text {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--text-muted);
      min-width: 32px;
    }

    .action-buttons-group {
      display: flex;
      align-items: center;
      justify-content: flex-end;
      gap: 0.4rem;
    }

    .row-overdue {
      background: rgba(244, 63, 94, 0.04) !important;
    }

    /* Modal Checklist Styles (Páginas 2 e 3 do PDF) */
    .task-details-summary {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 1rem;
      padding: 1rem;
      background: var(--bg-surface-elevated);
      border-radius: var(--radius-md);
      margin-bottom: 1.5rem;
    }

    .store-badge-sub {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--color-primary);
      text-transform: uppercase;
      display: block;
      margin-top: 0.2rem;
    }

    .checklist-box {
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 1.25rem;
      background: var(--bg-surface);
    }

    .checklist-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 0.75rem;
    }

    .checklist-header h4 {
      font-size: 0.95rem;
      margin: 0;
      letter-spacing: 0.05em;
    }

    .completion-badge {
      font-size: 0.8rem;
      font-weight: 800;
      color: var(--color-teal);
    }

    .checklist-items-list {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      max-height: 260px;
      overflow-y: auto;
    }

    .checklist-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      padding: 0.55rem 0.75rem;
      border-radius: var(--radius-sm);
      background: var(--bg-surface-elevated);
      cursor: pointer;
      transition: background 0.15s;
    }

    .checklist-item:hover {
      background: var(--bg-surface-hover);
    }

    .checklist-item.item-done .item-desc {
      text-decoration: line-through;
      color: var(--text-muted);
    }

    .item-desc {
      font-size: 0.875rem;
    }

    .briefing-box {
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 1.25rem;
      background: var(--bg-surface-elevated);
    }

    .briefing-title {
      font-size: 0.85rem;
      font-weight: 800;
      letter-spacing: 0.05em;
      margin-bottom: 0.75rem;
      color: var(--text-secondary);
    }

    .briefing-content p {
      font-size: 0.875rem;
      color: var(--text-primary);
      line-height: 1.6;
      margin: 0;
      white-space: pre-line;
    }

    .form-row-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    @media (max-width: 768px) {
      .filters-grid { grid-template-columns: 1fr; }
      .form-row-2 { grid-template-columns: 1fr; }
      .task-details-summary { grid-template-columns: 1fr; }
    }
  `]
})
export class TarefasComponent implements OnInit {
  private apiService = inject(ApiService);
  private route = inject(ActivatedRoute);

  tarefas = signal<Tarefa[]>([]);
  filtroLoja: string = '';
  filtroStatus: string = '';
  filtroPrioridade: string = '';

  // Modals state
  modalChecklistAberto = signal<boolean>(false);
  modalFormAberto = signal<boolean>(false);
  emEdicao = signal<boolean>(false);
  tarefaSelecionada = signal<Tarefa | null>(null);

  formTarefa: Partial<Tarefa> = {};
  responsaveisInput: string = '';

  ngOnInit(): void {
    this.route.queryParams.subscribe(params => {
      if (params['status']) {
        this.filtroStatus = params['status'];
      }
      this.carregarTarefas();
    });
  }

  carregarTarefas(): void {
    this.apiService.getTarefas(this.filtroLoja, this.filtroStatus, this.filtroPrioridade).subscribe({
      next: (res) => this.tarefas.set(res),
      error: (err) => console.error('Erro ao carregar tarefas:', err)
    });
  }

  abrirChecklistModal(tarefa: Tarefa): void {
    this.tarefaSelecionada.set(tarefa);
    this.modalChecklistAberto.set(true);
  }

  fecharChecklistModal(): void {
    this.modalChecklistAberto.set(false);
  }

  toggleChecklistItem(item: ChecklistItem): void {
    const tarefa = this.tarefaSelecionada();
    if (!tarefa?.id || !item.id) return;

    this.apiService.toggleChecklistItem(tarefa.id, item.id).subscribe({
      next: (updatedTarefa) => {
        this.tarefaSelecionada.set(updatedTarefa);
        this.carregarTarefas();
      },
      error: (err) => console.error('Erro ao alternar checklist item:', err)
    });
  }

  abrirModalCriacao(): void {
    this.emEdicao.set(false);
    this.formTarefa = {
      status: 'A_FAZER',
      prioridade: 'MEDIA',
      dataEntrega: new Date(Date.now() + 5 * 86400000).toISOString().split('T')[0],
      checklist: [
        { descricao: 'Planejar conteúdo da semana', concluido: false },
        { descricao: 'Criar roteiro dos vídeos / Reels', concluido: false },
        { descricao: 'Gravar vídeos e takes no set', concluido: false },
        { descricao: 'Editar e finalizar Reels', concluido: false },
        { descricao: 'Produzir artes para feed e Stories', concluido: false },
        { descricao: 'Revisar textos e identidade visual', concluido: false },
        { descricao: 'Enviar para aprovação do cliente', concluido: false },
        { descricao: 'Agendar ou entregar material final', concluido: false }
      ]
    };
    this.responsaveisInput = 'Lucas Matheus, Edyllaine Silva';
    this.modalFormAberto.set(true);
  }

  abrirModalEdicao(tarefa: Tarefa): void {
    this.emEdicao.set(true);
    this.formTarefa = { ...tarefa };
    this.responsaveisInput = (tarefa.responsaveis || []).join(', ');
    this.modalFormAberto.set(true);
  }

  fecharModalForm(): void {
    this.modalFormAberto.set(false);
  }

  salvarTarefa(): void {
    const resps = this.responsaveisInput
      .split(',')
      .map(r => r.trim())
      .filter(r => r.length > 0);

    this.formTarefa.responsaveis = resps;

    if (this.emEdicao() && this.formTarefa.id) {
      this.apiService.updateTarefa(this.formTarefa.id, this.formTarefa).subscribe({
        next: () => {
          this.fecharModalForm();
          this.carregarTarefas();
        },
        error: (err) => console.error('Erro ao atualizar tarefa:', err)
      });
    } else {
      this.apiService.createTarefa(this.formTarefa).subscribe({
        next: () => {
          this.fecharModalForm();
          this.carregarTarefas();
        },
        error: (err) => console.error('Erro ao criar tarefa:', err)
      });
    }
  }

  concluirTarefa(tarefa: Tarefa): void {
    if (!tarefa.id) return;
    this.apiService.updateTarefaStatus(tarefa.id, 'CONCLUIDA').subscribe({
      next: () => this.carregarTarefas()
    });
  }

  excluirTarefa(tarefa: Tarefa): void {
    if (!tarefa.id || !confirm(`Tem certeza que deseja excluir a tarefa "${tarefa.titulo}"?`)) return;
    this.apiService.deleteTarefa(tarefa.id).subscribe({
      next: () => this.carregarTarefas()
    });
  }

  isOverdue(tarefa: Tarefa): boolean {
    if (!tarefa.dataEntrega || tarefa.status === 'CONCLUIDA') return false;
    const entrega = new Date(tarefa.dataEntrega);
    const hoje = new Date();
    hoje.setHours(0, 0, 0, 0);
    return entrega < hoje;
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'A_FAZER': return 'badge-a-fazer';
      case 'EM_DESENVOLVIMENTO': return 'badge-em-desenvolvimento';
      case 'EM_REVISAO': return 'badge-em-revisao';
      case 'NAO_HOMOLOGADA': return 'badge-nao-homologada';
      case 'ATRASADA': return 'badge-atrasada';
      case 'CONCLUIDA': return 'badge-concluida';
      default: return 'badge-a-fazer';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'A_FAZER': return 'A Fazer';
      case 'EM_DESENVOLVIMENTO': return 'Em Desenvolvimento';
      case 'EM_REVISAO': return 'Em Revisão';
      case 'NAO_HOMOLOGADA': return 'Não Homologada';
      case 'ATRASADA': return 'Atrasada';
      case 'CONCLUIDA': return 'Concluída';
      default: return status;
    }
  }

  getPrioridadeBadgeClass(prioridade: string): string {
    switch (prioridade) {
      case 'BAIXA': return 'badge-prioridade-baixa';
      case 'MEDIA': return 'badge-prioridade-media';
      case 'ALTA': return 'badge-prioridade-alta';
      case 'URGENTE': return 'badge-prioridade-urgente';
      default: return '';
    }
  }
}
