import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { ApiService } from '../../core/services/api.service';
import { Roteiro } from '../../core/models';

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
          subtitle="Crie scripts detalhados e compartilhe com a equipe diretamente no set"
          [showNewTaskButton]="true"
          (newTaskAction)="abrirModalCriacao()"
          (refreshAction)="carregarRoteiros()"
        ></app-header>

        <div class="page-body">
          <!-- Filtro de Busca -->
          <div class="card mb-4">
            <div class="search-bar-row">
              <div class="input-with-icon flex-1">
                <input 
                  type="text" 
                  class="form-control" 
                  placeholder="Filtrar por loja ou cliente (Ex: JM MODA FITNESS)..." 
                  [(ngModel)]="filtroLoja" 
                  (input)="carregarRoteiros()"
                />
              </div>
              <button class="btn btn-primary" (click)="abrirModalCriacao()">
                <i class="bi bi-plus-lg"></i>
                <span>Novo Roteiro</span>
              </button>
            </div>
          </div>

          <!-- Grid de Roteiros (Padrão Página 2 do PDF) -->
          <div class="scripts-grid">
            <div 
              class="card script-card" 
              *ngFor="let roteiro of roteiros()"
              [class.script-card-done]="roteiro.feito"
            >
              <div class="script-card-header">
                <div class="store-info">
                  <span class="client-name">{{ roteiro.loja }}</span>
                  <h3 class="script-title">{{ roteiro.titulo }}</h3>
                  <span class="creator-tag">CRIADOR: {{ roteiro.criadorNome || 'LUCAS MATHEUS' }}</span>
                </div>

                <!-- Sinalização de Feito / Concluído (Página 2 do PDF) -->
                <button 
                  class="btn-status-toggle" 
                  [class.btn-status-done]="roteiro.feito"
                  (click)="toggleFeito(roteiro)"
                  title="Clique para alternar o status de gravação"
                >
                  <i class="bi" [ngClass]="roteiro.feito ? 'bi-check-circle-fill' : 'bi-circle'"></i>
                  <span>{{ roteiro.feito ? 'GRAVAÇÃO CONCLUÍDA' : 'MARCAR COMO FEITO' }}</span>
                </button>
              </div>

              <div class="script-preview" *ngIf="roteiro.conteudoScript">
                <p>{{ roteiro.conteudoScript }}</p>
              </div>

              <div class="script-card-footer">
                <div class="footer-left">
                  <span class="record-date" *ngIf="roteiro.dataGravacao">
                    <i class="bi bi-calendar-check"></i> {{ roteiro.dataGravacao | date:'dd/MM/yyyy' }}
                  </span>
                </div>

                <div class="footer-actions">
                  <button class="btn btn-primary btn-sm" (click)="abrirModalLeitura(roteiro)">
                    <i class="bi bi-eye"></i>
                    <span>ABRIR ROTEIRO</span>
                  </button>
                  <button class="btn btn-secondary btn-sm" (click)="abrirModalEdicao(roteiro)">
                    <i class="bi bi-pencil"></i>
                    <span>EDITAR</span>
                  </button>
                  <button class="btn btn-ghost btn-sm text-danger" (click)="excluirRoteiro(roteiro)" title="Excluir">
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </div>
            </div>

            <div *ngIf="roteiros().length === 0" class="empty-state-card card">
              <i class="bi bi-camera-reels text-muted" style="font-size: 3rem;"></i>
              <p class="text-muted mt-2">Nenhum roteiro cadastrado no momento.</p>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- MODAL 1: MODO LEITURA NO SET ("ABRIR ROTEIRO") -->
    <div class="modal-backdrop" *ngIf="modalLeituraAberto()" (click)="fecharModalLeitura()">
      <div class="modal-content modal-lg script-view-modal" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <div>
            <span class="badge" [ngClass]="roteiroSelecionado()?.feito ? 'badge-concluida' : 'badge-em-desenvolvimento'">
              {{ roteiroSelecionado()?.feito ? 'GRAVADO & CONCLUÍDO' : 'EM GRAVAÇÃO NO SET' }}
            </span>
            <h2 class="mt-2 mb-0">{{ roteiroSelecionado()?.titulo }}</h2>
            <span class="store-badge-sub">{{ roteiroSelecionado()?.loja }}</span>
          </div>
          <button class="btn-ghost btn-icon" (click)="fecharModalLeitura()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <div class="modal-body script-body-content">
          <div class="set-meta-box">
            <div>
              <span class="meta-lbl">CRIADOR / DIRETOR:</span>
              <strong>{{ roteiroSelecionado()?.criadorNome || 'LUCAS MATHEUS' }}</strong>
            </div>
            <div>
              <span class="meta-lbl">DATA PREVISTA:</span>
              <strong>{{ roteiroSelecionado()?.dataGravacao | date:'dd/MM/yyyy' }}</strong>
            </div>
          </div>

          <div class="script-text-container">
            <h4><i class="bi bi-camera-reels-fill text-primary"></i> SCRIPT / ROTEIRO DE GRAVAÇÃO</h4>
            <div class="script-rich-text">
              {{ roteiroSelecionado()?.conteudoScript }}
            </div>
          </div>

          <div class="set-tech-box mt-3" *ngIf="roteiroSelecionado()?.observacoesSet">
            <h5><i class="bi bi-gear-fill text-warning"></i> OBSERVAÇÕES TÉCNICAS DO SET & ILUMINAÇÃO</h5>
            <p>{{ roteiroSelecionado()?.observacoesSet }}</p>
          </div>
        </div>

        <div class="modal-footer">
          <button 
            class="btn" 
            [ngClass]="roteiroSelecionado()?.feito ? 'btn-secondary' : 'btn-success'"
            (click)="toggleFeitoModal()"
          >
            <i class="bi bi-check2-circle"></i>
            <span>{{ roteiroSelecionado()?.feito ? 'Desmarcar Concluído' : 'Sinalizar como Gravado / Concluído' }}</span>
          </button>
          <button class="btn btn-secondary" (click)="fecharModalLeitura()">
            Fechar
          </button>
        </div>
      </div>
    </div>

    <!-- MODAL 2: CRIAÇÃO / EDIÇÃO DE ROTEIRO -->
    <div class="modal-backdrop" *ngIf="modalFormAberto()" (click)="fecharModalForm()">
      <div class="modal-content modal-lg" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3>{{ emEdicao() ? 'Editar Roteiro' : 'Novo Roteiro de Gravação' }}</h3>
          <button class="btn-ghost btn-icon" (click)="fecharModalForm()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <form (ngSubmit)="salvarRoteiro()">
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">TÍTULO DO ROTEIRO</label>
              <input type="text" class="form-control" [(ngModel)]="formRoteiro.titulo" name="titulo" required placeholder="Ex: NOVO ESPAÇO FITNESS | JM MODA FITNESS" />
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">LOJA / CLIENTE</label>
                <input type="text" class="form-control" [(ngModel)]="formRoteiro.loja" name="loja" required placeholder="Ex: JM MODA FITNESS" />
              </div>

              <div class="form-group">
                <label class="form-label">CRIADOR / ROTEIRISTA</label>
                <input type="text" class="form-control" [(ngModel)]="formRoteiro.criadorNome" name="criadorNome" placeholder="Ex: LUCAS MATHEUS" />
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">DATA DA GRAVAÇÃO NO SET</label>
              <input type="date" class="form-control" [(ngModel)]="formRoteiro.dataGravacao" name="dataGravacao" />
            </div>

            <div class="form-group">
              <label class="form-label">CONTEÚDO DO SCRIPT (CENAS, FALAS E TAKES)</label>
              <textarea class="form-control" rows="7" [(ngModel)]="formRoteiro.conteudoScript" name="conteudoScript" required placeholder="CENA 1: ...&#10;CENA 2: ...&#10;LOCUÇÃO: ..."></textarea>
            </div>

            <div class="form-group">
              <label class="form-label">OBSERVAÇÕES DO SET (LENTES, ILUMINAÇÃO, ÁUDIO)</label>
              <textarea class="form-control" rows="3" [(ngModel)]="formRoteiro.observacoesSet" name="observacoesSet" placeholder="Ex: Lente 24-70mm f/2.8, iluminação 5600K com bastões RGB azuis."></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="fecharModalForm()">Cancelar</button>
            <button type="submit" class="btn btn-primary">Salvar Roteiro</button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .search-bar-row {
      display: flex;
      gap: 1rem;
      align-items: center;
      width: 100%;
      max-width: 100%;
      min-width: 0;
      flex-wrap: wrap;
    }

    .flex-1 {
      flex: 1;
      min-width: 0;
    }

    .scripts-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1.25rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .script-card {
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      border: 1px solid var(--border-color);
      transition: all 0.2s ease;
      min-width: 0;
      max-width: 100%;
      box-sizing: border-box;
      overflow: hidden;
    }

    .script-card:hover {
      border-color: var(--color-primary);
      box-shadow: var(--shadow-md);
    }

    .script-card-done {
      border-left: 4px solid var(--color-success) !important;
    }

    .script-card-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1rem;
    }

    .client-name {
      font-size: 0.8rem;
      font-weight: 800;
      color: var(--color-primary);
      text-transform: uppercase;
      letter-spacing: 0.05em;
      display: block;
    }

    .script-title {
      font-size: 1.15rem;
      margin: 0.2rem 0;
      line-height: 1.3;
    }

    .creator-tag {
      font-size: 0.725rem;
      font-weight: 700;
      color: var(--text-muted);
      text-transform: uppercase;
    }

    .btn-status-toggle {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.4rem 0.75rem;
      border-radius: var(--radius-full);
      border: 1px solid var(--border-subtle);
      background: var(--bg-surface-elevated);
      color: var(--text-secondary);
      font-size: 0.7rem;
      font-weight: 800;
      letter-spacing: 0.04em;
      cursor: pointer;
      transition: all 0.2s ease;
      white-space: nowrap;
    }

    .btn-status-toggle:hover {
      background: var(--bg-surface-hover);
    }

    .btn-status-done {
      background: rgba(16, 185, 129, 0.15);
      color: #10B981;
      border-color: #10B981;
    }

    .script-preview {
      background: var(--bg-surface-elevated);
      padding: 1rem;
      border-radius: var(--radius-md);
      margin-bottom: 1.25rem;
      max-height: 120px;
      overflow: hidden;
      position: relative;
    }

    .script-preview p {
      font-size: 0.85rem;
      color: var(--text-secondary);
      line-height: 1.6;
      margin: 0;
      white-space: pre-line;
    }

    .script-card-footer {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding-top: 1rem;
      border-top: 1px solid var(--border-color);
    }

    .record-date {
      font-size: 0.75rem;
      color: var(--text-muted);
      font-weight: 600;
    }

    .footer-actions {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    /* Modal Read View */
    .set-meta-box {
      display: flex;
      gap: 2rem;
      padding: 1rem;
      background: var(--bg-surface-elevated);
      border-radius: var(--radius-md);
      margin-bottom: 1.5rem;
    }

    .meta-lbl {
      font-size: 0.7rem;
      color: var(--text-muted);
      display: block;
    }

    .script-text-container {
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 1.5rem;
    }

    .script-text-container h4 {
      font-size: 1rem;
      margin-bottom: 1rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }

    .script-rich-text {
      font-size: 0.95rem;
      line-height: 1.8;
      white-space: pre-line;
      color: var(--text-primary);
    }

    .set-tech-box {
      background: rgba(245, 158, 11, 0.08);
      border: 1px solid rgba(245, 158, 11, 0.3);
      padding: 1rem 1.25rem;
      border-radius: var(--radius-md);
    }

    .set-tech-box h5 {
      font-size: 0.85rem;
      margin-bottom: 0.4rem;
      color: var(--color-warning);
    }

    .set-tech-box p {
      font-size: 0.85rem;
      margin: 0;
      color: var(--text-primary);
    }

    .empty-state-card {
      grid-column: 1 / -1;
      text-align: center;
      padding: 4rem 1rem;
    }

    @media (max-width: 768px) {
      .scripts-grid { grid-template-columns: 1fr; }
      .script-card-header { flex-direction: column; gap: 0.75rem; }
      .script-card-footer { flex-direction: column; gap: 0.75rem; align-items: stretch; }
      .footer-actions { width: 100%; justify-content: space-between; }
      .footer-actions .btn { flex: 1; }
      .set-meta-box { flex-direction: column; gap: 0.75rem; }
    }
  `]
})
export class RoteirosComponent implements OnInit {
  private apiService = inject(ApiService);

  roteiros = signal<Roteiro[]>([]);
  filtroLoja: string = '';

  modalLeituraAberto = signal<boolean>(false);
  modalFormAberto = signal<boolean>(false);
  emEdicao = signal<boolean>(false);
  roteiroSelecionado = signal<Roteiro | null>(null);

  formRoteiro: Partial<Roteiro> = {};

  ngOnInit(): void {
    this.carregarRoteiros();
  }

  carregarRoteiros(): void {
    this.apiService.getRoteiros(this.filtroLoja).subscribe({
      next: (res) => this.roteiros.set(res),
      error: (err) => console.error('Erro ao carregar roteiros:', err)
    });
  }

  toggleFeito(roteiro: Roteiro): void {
    if (!roteiro.id) return;
    this.apiService.toggleRoteiroConcluido(roteiro.id).subscribe({
      next: (updated) => {
        this.carregarRoteiros();
      }
    });
  }

  toggleFeitoModal(): void {
    const sel = this.roteiroSelecionado();
    if (!sel?.id) return;
    this.apiService.toggleRoteiroConcluido(sel.id).subscribe({
      next: (updated) => {
        this.roteiroSelecionado.set(updated);
        this.carregarRoteiros();
      }
    });
  }

  abrirModalLeitura(roteiro: Roteiro): void {
    this.roteiroSelecionado.set(roteiro);
    this.modalLeituraAberto.set(true);
  }

  fecharModalLeitura(): void {
    this.modalLeituraAberto.set(false);
  }

  abrirModalCriacao(): void {
    this.emEdicao.set(false);
    this.formRoteiro = {
      criadorNome: 'LUCAS MATHEUS',
      dataGravacao: new Date().toISOString().split('T')[0],
      status: 'PENDENTE',
      feito: false
    };
    this.modalFormAberto.set(true);
  }

  abrirModalEdicao(roteiro: Roteiro): void {
    this.emEdicao.set(true);
    this.formRoteiro = { ...roteiro };
    this.modalFormAberto.set(true);
  }

  fecharModalForm(): void {
    this.modalFormAberto.set(false);
  }

  salvarRoteiro(): void {
    if (this.emEdicao() && this.formRoteiro.id) {
      this.apiService.updateRoteiro(this.formRoteiro.id, this.formRoteiro).subscribe({
        next: () => {
          this.fecharModalForm();
          this.carregarRoteiros();
        }
      });
    } else {
      this.apiService.createRoteiro(this.formRoteiro).subscribe({
        next: () => {
          this.fecharModalForm();
          this.carregarRoteiros();
        }
      });
    }
  }

  excluirRoteiro(roteiro: Roteiro): void {
    if (!roteiro.id || !confirm(`Deseja excluir o roteiro "${roteiro.titulo}"?`)) return;
    this.apiService.deleteRoteiro(roteiro.id).subscribe({
      next: () => this.carregarRoteiros()
    });
  }
}
