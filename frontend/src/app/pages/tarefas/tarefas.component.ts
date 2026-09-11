import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { ApiService } from '../../core/services/api.service';
import { Tarefa, ChecklistItem, Municipio, Cliente } from '../../core/models';

@Component({
  selector: 'app-tarefas',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, HeaderComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>

      <main class="main-content">
        <app-header 
          title="Gestão de Tarefas & Demandas" 
          subtitle="Acompanhamento operacional de demandas, checklist dinâmico e aprovações"
          [showNewTaskButton]="true"
          (newTaskAction)="abrirModalCriacao()"
          (refreshAction)="carregarTarefas()"
        ></app-header>

        <div class="page-body">
          <!-- Filtros de Busca -->
          <div class="card filters-card">
            <div class="filters-grid">
              <div class="filter-item">
                <label class="form-label">Buscar Demanda ou Loja</label>
                <div class="input-with-icon">
                  <input 
                    type="text" 
                    class="form-control" 
                    placeholder="Ex: Promoção, Beto, Mercado..."
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
            <div class="table-responsive">
              <table class="custom-table tarefas-table">
                <thead>
                  <tr>
                    <th style="width: 140px;">Status</th>
                    <th style="min-width: 240px;">Demanda & Cliente</th>
                    <th style="width: 140px;">Município</th>
                    <th style="width: 110px;">Prioridade</th>
                    <th style="min-width: 200px;">Responsáveis</th>
                    <th style="width: 150px;">Prazo / Entrega</th>
                    <th style="width: 140px;">Progresso</th>
                    <th style="width: 180px; text-align: right;">Ações</th>
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
                      <div class="task-title-cell" (click)="abrirModalDetalhes(tarefa)" style="cursor: pointer;">
                        <strong>{{ tarefa.titulo }}</strong>
                        <span class="store-tag"><i class="bi bi-shop"></i> {{ tarefa.loja }}</span>
                      </div>
                    </td>
                    <td>
                      <span class="muni-tag" *ngIf="tarefa.municipio">
                        <i class="bi bi-geo-alt-fill text-muted"></i> {{ tarefa.municipio }}
                      </span>
                      <span class="text-muted text-xs" *ngIf="!tarefa.municipio">-</span>
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
                      </div>
                    </td>
                    <td>
                      <div class="dates-cell">
                        <span class="text-xs" [class.text-danger]="isOverdue(tarefa)">
                          <i class="bi bi-calendar-check"></i> {{ tarefa.dataEntrega | date:'dd/MM/yyyy' }}
                        </span>
                      </div>
                    </td>
                    <td>
                      <div class="progress-container">
                        <div class="progress-bar-bg">
                          <div 
                            class="progress-bar-fill" 
                            [style.width.%]="tarefa.percentualConcluido || 0"
                            [ngClass]="getProgressBarClass(tarefa.percentualConcluido || 0)"
                          ></div>
                        </div>
                        <span class="progress-text">{{ tarefa.percentualConcluido || 0 }}%</span>
                      </div>
                    </td>
                    <td style="text-align: right;">
                      <div class="actions-group">
                        <button 
                          class="btn-icon" 
                          (click)="abrirModalDetalhes(tarefa)" 
                          title="Abrir detalhes / Checklist / Entregas"
                        >
                          <i class="bi bi-eye"></i>
                        </button>
                        <button 
                          class="btn-icon" 
                          (click)="abrirModalEdicao(tarefa)" 
                          title="Editar tarefa"
                        >
                          <i class="bi bi-pencil"></i>
                        </button>
                        <button 
                          class="btn-icon btn-danger" 
                          (click)="excluirTarefa(tarefa)" 
                          title="Excluir tarefa"
                        >
                          <i class="bi bi-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- MODAL CRIAR / EDITAR TAREFA (Exatamente como em 16746.jpg) -->
    <div class="modal-backdrop" *ngIf="showModalCriacao()">
      <div class="modal-card modal-dark animate-fade-in">
        <div class="modal-header modal-header-dark">
          <h2 class="modal-title-white">{{ editandoId ? 'Editar Tarefa' : 'Nova Tarefa' }}</h2>
          <button class="close-btn-white" (click)="fecharModalCriacao()"><i class="bi bi-x-lg"></i></button>
        </div>

        <div class="modal-body modal-body-dark">
          <!-- TÍTULO DA TAREFA (16746.jpg) -->
          <div class="form-group">
            <label class="form-lbl-dark">TÍTULO DA TAREFA</label>
            <input type="text" [(ngModel)]="tarefaForm.titulo" class="form-input-dark" placeholder="Ex: Caminhada Beto" />
          </div>

          <!-- LOJA / CLIENTE & PRIORIDADE (16746.jpg) -->
          <div class="form-grid-2">
            <div class="form-group">
              <label class="form-lbl-dark">LOJA / CLIENTE</label>
              <input type="text" [(ngModel)]="tarefaForm.loja" class="form-input-dark" placeholder="Ex: Novo São João" />
            </div>
            <div class="form-group">
              <label class="form-lbl-dark">PRIORIDADE</label>
              <select [(ngModel)]="tarefaForm.prioridade" class="form-input-dark">
                <option value="BAIXA">BAIXA</option>
                <option value="MEDIA">MEDIA</option>
                <option value="ALTA">ALTA</option>
                <option value="URGENTE">URGENTE</option>
              </select>
            </div>
          </div>

          <!-- DATA DA GRAVAÇÃO & DATA DE ENTREGA (16746.jpg) -->
          <div class="form-grid-2">
            <div class="form-group">
              <label class="form-lbl-dark">DATA DA GRAVAÇÃO</label>
              <input type="date" [(ngModel)]="tarefaForm.dataGravacao" class="form-input-dark" />
            </div>
            <div class="form-group">
              <label class="form-lbl-dark">DATA DE ENTREGA / PRAZO</label>
              <input type="date" [(ngModel)]="tarefaForm.dataEntrega" class="form-input-dark" />
            </div>
          </div>

          <!-- STATUS & RESPONSÁVEIS (16746.jpg) -->
          <div class="form-grid-2">
            <div class="form-group">
              <label class="form-lbl-dark">STATUS</label>
              <select [(ngModel)]="tarefaForm.status" class="form-input-dark">
                <option value="A_FAZER">A Fazer</option>
                <option value="EM_DESENVOLVIMENTO">Em Desenvolvimento</option>
                <option value="EM_REVISAO">Em Revisão</option>
                <option value="NAO_HOMOLOGADA">Não Homologada</option>
                <option value="CONCLUIDA">Concluída</option>
              </select>
            </div>
            <div class="form-group">
              <label class="form-lbl-dark">RESPONSÁVEIS (SEPARADOS POR VÍRGULA)</label>
              <input type="text" [(ngModel)]="responsaveisString" class="form-input-dark" placeholder="Igor Santos, Edyllaine Silva, Ingrid" />
            </div>
          </div>

          <!-- MUNICÍPIO DE ATENDIMENTO (16746.jpg - indicação em vermelho) -->
          <div class="form-group">
            <label class="form-lbl-dark highlight-label">MUNICÍPIO DE ATENDIMENTO</label>
            <select [(ngModel)]="tarefaForm.municipio" class="form-input-dark">
              <option *ngFor="let m of municipios()" [value]="m.nome">{{ m.nome }} - {{ m.uf }}</option>
            </select>
          </div>

          <!-- DESCRIÇÃO & BRIEFING DETALHADO (16746.jpg) -->
          <div class="form-group">
            <label class="form-lbl-dark">DESCRIÇÃO & BRIEFING DETALHADO</label>
            <textarea rows="4" [(ngModel)]="tarefaForm.briefing" class="form-input-dark" placeholder="Instruções de gravação, roteiro, formatos esperados..."></textarea>
          </div>

          <div class="modal-actions-right">
            <button class="btn-cancel-dark" (click)="fecharModalCriacao()">Cancelar</button>
            <button class="btn-save-dark" (click)="salvarTarefa()">Salvar Tarefa</button>
          </div>
        </div>
      </div>
    </div>

    <!-- MODAL DETALHES DA TAREFA & CHECKLIST EDITÁVEL & ARQUIVOS FINAIS (16748.jpg, 16735.jpg, 16737.jpg) -->
    <div class="modal-backdrop" *ngIf="showModalDetalhes()">
      <div class="modal-card modal-dark modal-lg animate-fade-in" *ngIf="tarefaSelecionada() as t">
        <div class="modal-header modal-header-dark">
          <div>
            <span class="badge-status-top" [ngClass]="t.status.toLowerCase()">{{ getStatusLabel(t.status) }}</span>
            <h2 class="modal-title-white mt-1">{{ t.titulo }}</h2>
            <span class="store-subtitle-purple">{{ t.loja }}</span>
          </div>
          <div class="header-right-actions">
            <button class="btn-delete-hdr" (click)="excluirTarefa(t)"><i class="bi bi-trash"></i> EXCLUIR</button>
            <button class="btn-close-hdr" (click)="fecharModalDetalhes()">FECHAR</button>
          </div>
        </div>

        <div class="modal-body modal-body-dark">
          <!-- Metadados da Demanda (16748.jpg) -->
          <div class="task-info-top-grid">
            <div class="info-block">
              <span class="info-lbl">RESPONSÁVEIS:</span>
              <span class="info-val">{{ t.responsaveis.join(', ') || 'Nenhum' }}</span>
            </div>
            <div class="info-block">
              <span class="info-lbl">PRAZO DE ENTREGA:</span>
              <span class="info-val">{{ t.dataEntrega | date:'dd/MM/yyyy' }}</span>
            </div>
            <div class="info-block">
              <span class="info-lbl">DATA DA GRAVAÇÃO:</span>
              <span class="info-val">{{ t.dataGravacao ? (t.dataGravacao | date:'dd/MM/yyyy') : '—' }}</span>
            </div>
            <div class="info-block" *ngIf="t.municipio">
              <span class="info-lbl">MUNICÍPIO:</span>
              <span class="info-val">{{ t.municipio }}</span>
            </div>
          </div>

          <!-- CARD: SISTEMA DE APROVAÇÃO EXTERNA (16735.jpg) -->
          <div class="aprovacao-banner-card">
            <div>
              <span class="aprv-tag">SISTEMA DE APROVAÇÃO</span>
              <p class="aprv-heading">Link de aprovação externa gerado</p>
              <span class="aprv-status-state">Estado: <strong>{{ t.statusAprovacao || 'PENDING' }}</strong></span>
            </div>
            <button class="btn-copiar-link-purple" (click)="copiarLinkAprovacao(t)">
              <i class="bi bi-link-45deg"></i> {{ linkCopiado ? 'Link Copiado!' : 'Copiar Link' }}
            </button>
          </div>

          <!-- SEÇÃO: CHECKLIST DINÂMICO & EDITÁVEL (16748.jpg - "deixa uma forma de editar o checklist") -->
          <div class="checklist-section-box">
            <div class="checklist-hdr-row">
              <h3 class="checklist-title">CHECKLIST DA TAREFA</h3>
              <span class="percent-badge">{{ t.percentualConcluido || 0 }}% completo</span>
            </div>

            <!-- Lista de Itens do Checklist -->
            <div class="checklist-items-stack">
              <div class="checklist-row-item" *ngFor="let item of t.checklist; let idx = index">
                <label class="custom-checkbox-container">
                  <input type="checkbox" [checked]="item.concluido" (change)="toggleChecklistItem(t, item)" />
                  <span class="checkmark"></span>
                  <span class="item-text" [class.item-done]="item.concluido">{{ item.descricao }}</span>
                </label>
                <button class="btn-remove-check-item" (click)="removerItemChecklist(t, idx)" title="Remover este item">
                  <i class="bi bi-x"></i>
                </button>
              </div>
            </div>

            <!-- Adicionar Novo Item ao Checklist (16748.jpg) -->
            <div class="add-check-item-row">
              <input 
                type="text" 
                [(ngModel)]="novoItemDescricao" 
                placeholder="Adicionar novo item de checklist para esta demanda..." 
                class="form-input-dark"
                (keyup.enter)="adicionarItemChecklist(t)"
              />
              <button class="btn-add-item-check" (click)="adicionarItemChecklist(t)">
                <i class="bi bi-plus-lg"></i> Adicionar
              </button>
            </div>
          </div>

          <!-- SEÇÃO: ADICIONAR ARQUIVOS FINAIS CONCLUÍDOS (16737.jpg) -->
          <div class="arquivos-section-box">
            <h3 class="arquivos-title">ADICIONAR ARQUIVOS FINAIS CONCLUÍDOS</h3>
            
            <div class="upload-dropzone" (click)="taskFileInput.click()">
              <input 
                type="file" 
                #taskFileInput 
                multiple 
                (change)="onUploadArquivosFinais($event, t)" 
                accept="image/*,video/*,.png,.jpg,.jpeg,.webp,.gif,.bmp,.svg,.pdf,.zip,.mp4,.mov" 
                style="display: none" 
              />
              <i class="bi bi-cloud-arrow-up text-primary"></i>
              <p class="dropzone-text">Clique para selecionar arquivos no seu computador</p>
              <span class="dropzone-sub">Compatível com todos os formatos de foto, vídeo e arte (PNG, JPG, MP4, PDF, etc.)</span>
            </div>

            <!-- Grid de Arquivos Anexados com botão X para remover (16737.jpg) -->
            <div class="arquivos-attached-grid" *ngIf="t.arquivosFinais && t.arquivosFinais.length > 0">
              <div class="attached-file-pill" *ngFor="let arq of t.arquivosFinais">
                <i class="bi bi-file-earmark-image"></i>
                <span class="attached-name">{{ arq.nome }}</span>
                <button class="btn-del-attached" (click)="removerArquivoFinal(t, arq.id!)">
                  <i class="bi bi-x"></i>
                </button>
              </div>
            </div>
          </div>

          <!-- SEÇÃO: OBSERVAÇÕES & COMENTÁRIOS (16737.jpg) -->
          <div class="observacoes-section-box">
            <h3 class="obs-title">OBSERVAÇÕES</h3>
            
            <div class="obs-list" *ngIf="t.observacoes && t.observacoes.length > 0; else semObs">
              <div class="obs-item-bubble" *ngFor="let o of t.observacoes">
                <div class="obs-meta">
                  <strong>{{ o.autorNome }}</strong>
                  <span>{{ o.dataHora }}</span>
                </div>
                <p class="obs-text">{{ o.texto }}</p>
              </div>
            </div>

            <ng-template #semObs>
              <p class="sem-obs-text">Sem observações na entrega.</p>
            </ng-template>

            <!-- Input Nova Observação -->
            <div class="add-obs-form" *ngIf="addObsOpen">
              <textarea 
                rows="2" 
                [(ngModel)]="novaObservacaoTexto" 
                placeholder="Digite sua observação sobre a entrega..." 
                class="form-input-dark"
              ></textarea>
              <div class="obs-form-actions">
                <button class="btn-cancel-dark btn-sm" (click)="addObsOpen = false">Cancelar</button>
                <button class="btn-save-dark btn-sm" (click)="adicionarObservacao(t)">Salvar Observação</button>
              </div>
            </div>

            <button class="btn-add-obs-trigger" *ngIf="!addObsOpen" (click)="addObsOpen = true">
              <i class="bi bi-chat-left-text"></i> ADICIONAR OBSERVAÇÃO
            </button>
          </div>

          <!-- BRIEFING -->
          <div class="briefing-box" *ngIf="t.briefing || t.descricao">
            <span class="info-lbl">DESCRIÇÃO BRIEFING</span>
            <p class="briefing-content">{{ t.briefing || t.descricao }}</p>
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

    .filters-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.25rem;
    }
    .filters-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
      gap: 1rem;
    }
    .form-label {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--text-secondary);
      margin-bottom: 0.35rem;
      display: block;
    }
    .form-control, .form-select {
      width: 100%;
      padding: 0.65rem 0.85rem;
      border: 1px solid var(--border-color);
      border-radius: 8px;
      background: var(--card-bg);
      color: var(--text-primary);
      font-size: 0.88rem;
      outline: none;
    }

    .table-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1rem;
      box-shadow: 0 4px 14px rgba(0,0,0,0.03);
    }
    .tarefas-table th {
      font-size: 0.72rem;
      font-weight: 800;
      color: var(--text-secondary);
      padding: 0.75rem;
      border-bottom: 1px solid var(--border-color);
    }
    .tarefas-table td {
      padding: 0.85rem 0.75rem;
      border-bottom: 1px solid var(--border-color);
      vertical-align: middle;
      font-size: 0.84rem;
    }
    .task-title-cell strong {
      color: var(--text-primary);
      display: block;
      margin-bottom: 0.15rem;
    }
    .store-tag {
      font-size: 0.75rem;
      color: var(--primary);
      font-weight: 600;
    }
    .muni-tag {
      font-size: 0.75rem;
      color: var(--text-secondary);
      font-weight: 600;
    }
    .responsaveis-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 0.3rem;
    }
    .resp-pill {
      background: rgba(124, 58, 237, 0.08);
      color: var(--primary);
      font-size: 0.7rem;
      font-weight: 700;
      padding: 0.2rem 0.45rem;
      border-radius: 4px;
    }

    .progress-container {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .progress-bar-bg {
      flex: 1;
      height: 6px;
      background: rgba(0,0,0,0.08);
      border-radius: 3px;
      overflow: hidden;
    }
    .progress-bar-fill {
      height: 100%;
      background: #7c3aed;
      border-radius: 3px;
    }
    .progress-bar-fill.bg-success { background: #10b981; }
    .progress-text {
      font-size: 0.72rem;
      font-weight: 700;
      color: var(--text-secondary);
    }

    .actions-group {
      display: flex;
      justify-content: flex-end;
      gap: 0.4rem;
    }
    .btn-icon {
      background: none;
      border: 1px solid var(--border-color);
      width: 32px;
      height: 32px;
      border-radius: 6px;
      color: var(--text-primary);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .btn-icon.btn-danger {
      color: #ef4444;
      border-color: #fee2e2;
    }

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
      max-width: 560px;
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 25px 60px -15px rgba(0,0,0,0.5);
    }
    .modal-lg {
      max-width: 740px;
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
    .store-subtitle-purple {
      font-size: 0.75rem;
      font-weight: 800;
      color: var(--primary);
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }
    .badge-status-top {
      font-size: 0.68rem;
      font-weight: 800;
      padding: 0.25rem 0.6rem;
      border-radius: 6px;
      text-transform: uppercase;
    }
    .badge-status-top.a_fazer { background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.3); }
    .badge-status-top.concluida { background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.3); }

    .close-btn-white {
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      border-radius: 8px;
      width: 34px;
      height: 34px;
      display: flex;
      align-items: center;
      justify-content: center;
      cursor: pointer;
      transition: all 0.2s;
    }
    .close-btn-white:hover {
      background: var(--bg-surface-hover);
      color: var(--primary);
    }
    .header-right-actions {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .btn-delete-hdr {
      background: none;
      border: none;
      color: #ef4444;
      font-weight: 800;
      font-size: 0.8rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.35rem;
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
    }

    .modal-body-dark {
      padding: 1.75rem;
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
      background: var(--bg-surface);
    }
    .form-lbl-dark {
      font-size: 0.8rem;
      font-weight: 800;
      color: var(--text-primary);
      letter-spacing: 0.04em;
      text-transform: uppercase;
      margin-bottom: 0.4rem;
      display: block;
    }
    .highlight-label {
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

    /* Grid Metadados (16748.jpg) */
    .task-info-top-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
      gap: 1rem;
      background: var(--bg-surface-elevated);
      padding: 1.2rem;
      border-radius: 12px;
      border: 1.5px solid var(--border-color);
    }
    .info-lbl {
      font-size: 0.72rem;
      font-weight: 800;
      color: var(--text-secondary);
      letter-spacing: 0.05em;
      text-transform: uppercase;
      display: block;
    }
    .info-val {
      font-size: 0.92rem;
      font-weight: 800;
      color: var(--text-primary);
    }

    /* Card Aprovação (16735.jpg) */
    .aprovacao-banner-card {
      background: rgba(124, 58, 237, 0.08);
      border: 1.5px solid rgba(124, 58, 237, 0.25);
      border-radius: 12px;
      padding: 1.1rem 1.35rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
    }
    .aprv-tag {
      font-size: 0.68rem;
      font-weight: 800;
      color: var(--primary);
      letter-spacing: 0.05em;
      text-transform: uppercase;
    }
    .aprv-heading {
      font-size: 0.95rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0.2rem 0;
    }
    .aprv-status-state {
      font-size: 0.78rem;
      color: var(--text-secondary);
      font-weight: 600;
    }
    .btn-copiar-link-purple {
      background: var(--color-primary-gradient);
      color: #fff;
      border: none;
      padding: 0.65rem 1.2rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.85rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.4rem;
      box-shadow: 0 4px 12px rgba(124, 58, 237, 0.35);
      transition: all 0.2s;
    }
    .btn-copiar-link-purple:hover {
      box-shadow: 0 6px 18px rgba(124, 58, 237, 0.5);
      transform: translateY(-1px);
    }

    /* Checklist Box (16748.jpg) */
    .checklist-section-box {
      background: var(--bg-surface-elevated);
      border: 1.5px solid var(--border-color);
      border-radius: 12px;
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .checklist-hdr-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 0.5rem;
    }
    .checklist-title {
      font-size: 0.88rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
      letter-spacing: 0.04em;
    }
    .percent-badge {
      font-size: 0.82rem;
      font-weight: 800;
      color: #10b981;
    }
    .checklist-items-stack {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .checklist-row-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: 8px;
      padding: 0.7rem 0.95rem;
    }
    .custom-checkbox-container {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      cursor: pointer;
      font-size: 0.88rem;
      font-weight: 600;
      color: var(--text-primary);
      flex: 1;
    }
    .custom-checkbox-container input {
      accent-color: var(--primary);
      width: 17px;
      height: 17px;
    }
    .item-done {
      text-decoration: line-through;
      color: var(--text-muted);
    }
    .btn-remove-check-item {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      font-size: 1.1rem;
      padding: 0;
    }
    .add-check-item-row {
      display: flex;
      gap: 0.5rem;
      margin-top: 0.5rem;
    }
    .btn-add-item-check {
      background: var(--color-primary-gradient);
      color: #fff;
      border: none;
      padding: 0 1.25rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.85rem;
      cursor: pointer;
      white-space: nowrap;
      box-shadow: 0 4px 10px rgba(124, 58, 237, 0.3);
    }

    /* Seção Arquivos (16737.jpg) */
    .arquivos-section-box {
      background: var(--bg-surface-elevated);
      border: 1.5px solid var(--border-color);
      border-radius: 12px;
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .arquivos-title {
      font-size: 0.8rem;
      font-weight: 800;
      color: var(--text-primary);
      letter-spacing: 0.04em;
      text-transform: uppercase;
      margin: 0;
    }
    .upload-dropzone {
      border: 2px dashed var(--border-color);
      border-radius: 10px;
      padding: 1.25rem;
      text-align: center;
      cursor: pointer;
      background: var(--bg-surface);
      transition: all 0.2s;
    }
    .upload-dropzone:hover {
      background: var(--color-primary-light);
      border-color: var(--primary);
    }
    .upload-dropzone i {
      font-size: 1.8rem;
      color: var(--primary);
    }
    .dropzone-text {
      font-size: 0.9rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0.35rem 0 0.15rem 0;
    }
    .dropzone-sub {
      font-size: 0.75rem;
      color: var(--text-secondary);
      font-weight: 600;
    }
    .arquivos-attached-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.5rem;
      margin-top: 0.5rem;
    }
    .attached-file-pill {
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      border-radius: 8px;
      padding: 0.65rem 0.85rem;
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.84rem;
      font-weight: 600;
      color: var(--text-primary);
    }
    .attached-name {
      flex: 1;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .btn-del-attached {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      font-size: 1rem;
    }

    /* Observações (16737.jpg) */
    .observacoes-section-box {
      background: var(--bg-surface-elevated);
      border: 1.5px solid var(--border-color);
      border-radius: 12px;
      padding: 1.25rem;
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .obs-title {
      font-size: 0.8rem;
      font-weight: 800;
      color: var(--text-primary);
      letter-spacing: 0.04em;
      text-transform: uppercase;
      margin: 0;
    }
    .sem-obs-text {
      font-size: 0.85rem;
      color: var(--text-secondary);
      font-weight: 500;
      margin: 0;
    }
    .obs-item-bubble {
      background: var(--bg-surface);
      border-left: 3.5px solid var(--primary);
      border: 1px solid var(--border-color);
      border-left-width: 4px;
      border-radius: 8px;
      padding: 0.75rem 0.95rem;
      font-size: 0.85rem;
    }
    .obs-meta {
      display: flex;
      justify-content: space-between;
      color: var(--primary);
      font-weight: 700;
      font-size: 0.75rem;
      margin-bottom: 0.25rem;
    }
    .obs-text {
      margin: 0;
      color: var(--text-primary);
      font-weight: 500;
    }
    .btn-add-obs-trigger {
      background: var(--bg-surface);
      border: 1.5px solid var(--border-color);
      color: var(--text-primary);
      padding: 0.7rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.85rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.45rem;
      transition: all 0.2s;
    }
    .btn-add-obs-trigger:hover {
      background: var(--bg-surface-hover);
      border-color: var(--primary);
    }
    .add-obs-form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .obs-form-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
    }

    /* Briefing */
    .briefing-box {
      background: var(--bg-surface-elevated);
      border: 1.5px solid var(--border-color);
      border-radius: 12px;
      padding: 1.25rem;
    }
    .briefing-content {
      font-size: 0.88rem;
      color: var(--text-primary);
      font-weight: 500;
      margin: 0.35rem 0 0 0;
      line-height: 1.5;
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
    .btn-sm {
      padding: 0.4rem 0.85rem;
      font-size: 0.8rem;
    }
  `]
})
export class TarefasComponent implements OnInit {
  private api = inject(ApiService);
  private route = inject(ActivatedRoute);

  tarefas = signal<Tarefa[]>([]);
  municipios = signal<Municipio[]>([]);
  clientes = signal<Cliente[]>([]);

  filtroLoja = '';
  filtroStatus = '';
  filtroPrioridade = '';

  showModalCriacao = signal(false);
  showModalDetalhes = signal(false);
  tarefaSelecionada = signal<Tarefa | null>(null);
  editandoId: number | null = null;

  responsaveisString = 'Igor Santos, Edyllaine Silva, Ingrid';
  novoItemDescricao = '';
  addObsOpen = false;
  novaObservacaoTexto = '';
  linkCopiado = false;

  tarefaForm: Partial<Tarefa> = {
    titulo: '',
    loja: 'Novo São João',
    prioridade: 'ALTA',
    dataGravacao: '2026-09-13',
    dataEntrega: '2026-09-16',
    status: 'A_FAZER',
    municipio: 'São Miguel dos Campos',
    briefing: '',
    responsaveis: ['Igor Santos'],
    checklist: [],
  };

  ngOnInit(): void {
    this.carregarTarefas();
    this.carregarMunicipios();
  }

  carregarTarefas(): void {
    this.api.getTarefas(this.filtroLoja, this.filtroStatus, this.filtroPrioridade).subscribe((res) => {
      this.tarefas.set(res);
    });
  }

  carregarMunicipios(): void {
    this.api.getMunicipios().subscribe((res) => {
      this.municipios.set(res);
    });
  }

  abrirModalCriacao(): void {
    this.editandoId = null;
    this.tarefaForm = {
      titulo: 'Caminhada Beto',
      loja: 'Novo São João',
      prioridade: 'ALTA',
      dataGravacao: '2026-09-13',
      dataEntrega: '2026-09-16',
      status: 'A_FAZER',
      municipio: 'São Miguel dos Campos',
      briefing: 'Instruções de gravação, roteiro, formatos esperados...',
      responsaveis: ['Igor Santos', 'Edyllaine Silva', 'Ingrid'],
      checklist: [
        { id: 1, descricao: 'Planejar conteúdo da semana', concluido: false, ordem: 1 },
        { id: 2, descricao: 'Criar roteiro dos vídeos / Reels', concluido: false, ordem: 2 },
        { id: 3, descricao: 'Gravar vídeos e takes no set', concluido: false, ordem: 3 },
        { id: 4, descricao: 'Editar e finalizar Reels', concluido: false, ordem: 4 },
        { id: 5, descricao: 'Produzir artes para feed e Stories', concluido: false, ordem: 5 },
        { id: 6, descricao: 'Revisar textos e identidade visual', concluido: false, ordem: 6 },
      ],
    };
    this.responsaveisString = 'Igor Santos, Edyllaine Silva, Ingrid';
    this.showModalCriacao.set(true);
  }

  abrirModalEdicao(tarefa: Tarefa): void {
    this.editandoId = tarefa.id || null;
    this.tarefaForm = { ...tarefa };
    this.responsaveisString = tarefa.responsaveis ? tarefa.responsaveis.join(', ') : '';
    this.showModalCriacao.set(true);
  }

  fecharModalCriacao(): void {
    this.showModalCriacao.set(false);
  }

  salvarTarefa(): void {
    if (!this.tarefaForm.titulo) return;

    this.tarefaForm.responsaveis = this.responsaveisString
      ? this.responsaveisString.split(',').map((r) => r.trim()).filter((r) => r.length > 0)
      : ['Igor Santos'];

    if (this.editandoId) {
      this.api.updateTarefa(this.editandoId, this.tarefaForm).subscribe(() => {
        this.showModalCriacao.set(false);
        this.carregarTarefas();
      });
    } else {
      this.api.createTarefa(this.tarefaForm).subscribe(() => {
        this.showModalCriacao.set(false);
        this.carregarTarefas();
      });
    }
  }

  abrirModalDetalhes(tarefa: Tarefa): void {
    this.tarefaSelecionada.set(tarefa);
    this.novoItemDescricao = '';
    this.addObsOpen = false;
    this.linkCopiado = false;
    this.showModalDetalhes.set(true);
  }

  fecharModalDetalhes(): void {
    this.showModalDetalhes.set(false);
  }

  toggleChecklistItem(tarefa: Tarefa, item: ChecklistItem): void {
    item.concluido = !item.concluido;
    this.api.updateTarefa(tarefa.id!, { checklist: tarefa.checklist }).subscribe((atualizada) => {
      this.tarefaSelecionada.set(atualizada);
      this.carregarTarefas();
    });
  }

  adicionarItemChecklist(tarefa: Tarefa): void {
    if (!this.novoItemDescricao.trim()) return;
    if (!tarefa.checklist) tarefa.checklist = [];

    tarefa.checklist.push({
      id: Date.now(),
      descricao: this.novoItemDescricao.trim(),
      concluido: false,
      ordem: tarefa.checklist.length + 1,
    });

    this.novoItemDescricao = '';
    this.api.updateTarefa(tarefa.id!, { checklist: tarefa.checklist }).subscribe((atualizada) => {
      this.tarefaSelecionada.set(atualizada);
      this.carregarTarefas();
    });
  }

  removerItemChecklist(tarefa: Tarefa, index: number): void {
    tarefa.checklist.splice(index, 1);
    this.api.updateTarefa(tarefa.id!, { checklist: tarefa.checklist }).subscribe((atualizada) => {
      this.tarefaSelecionada.set(atualizada);
      this.carregarTarefas();
    });
  }

  onUploadArquivosFinais(event: Event, tarefa: Tarefa): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      const files = Array.from(input.files);
      files.forEach((file) => {
        const reader = new FileReader();
        const tipo: 'IMAGEM' | 'VIDEO' | 'DOCUMENTO' = file.type.startsWith('video')
          ? 'VIDEO'
          : file.type.startsWith('image')
          ? 'IMAGEM'
          : 'DOCUMENTO';

        reader.onload = (e: ProgressEvent<FileReader>) => {
          const urlOuBase64 = e.target?.result as string;
          this.api.addArquivoFinal(tarefa.id!, {
            nome: file.name,
            urlOuBase64,
            tipo,
          }).subscribe((atualizada) => {
            this.tarefaSelecionada.set(atualizada);
            this.carregarTarefas();
          });
        };
        reader.readAsDataURL(file);
      });
      input.value = '';
    }
  }

  removerArquivoFinal(tarefa: Tarefa, arquivoId: number): void {
    this.api.removeArquivoFinal(tarefa.id!, arquivoId).subscribe((atualizada) => {
      this.tarefaSelecionada.set(atualizada);
      this.carregarTarefas();
    });
  }

  adicionarObservacao(tarefa: Tarefa): void {
    if (!this.novaObservacaoTexto.trim()) return;

    this.api.addObservacaoTarefa(tarefa.id!, this.novaObservacaoTexto.trim()).subscribe((atualizada) => {
      this.tarefaSelecionada.set(atualizada);
      this.novaObservacaoTexto = '';
      this.addObsOpen = false;
      this.carregarTarefas();
    });
  }

  copiarLinkAprovacao(tarefa: Tarefa): void {
    const url = `${window.location.origin}/aprovacao/${tarefa.tokenAprovacao || tarefa.id}`;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(url);
    }
    this.linkCopiado = true;
    setTimeout(() => (this.linkCopiado = false), 2500);
  }

  excluirTarefa(tarefa: Tarefa): void {
    if (confirm(`Deseja realmente excluir a tarefa "${tarefa.titulo}"?`)) {
      this.api.deleteTarefa(tarefa.id!).subscribe(() => {
        this.showModalDetalhes.set(false);
        this.carregarTarefas();
      });
    }
  }

  isOverdue(tarefa: Tarefa): boolean {
    if (tarefa.status === 'CONCLUIDA' || !tarefa.dataEntrega) return false;
    return new Date(tarefa.dataEntrega) < new Date('2026-09-10');
  }

  getStatusBadgeClass(status: string): string {
    switch (status) {
      case 'CONCLUIDA': return 'badge-success';
      case 'EM_DESENVOLVIMENTO': return 'badge-info';
      case 'EM_REVISAO': case 'NAO_HOMOLOGADA': return 'badge-warning';
      case 'ATRASADA': return 'badge-danger';
      default: return 'badge-secondary';
    }
  }

  getPrioridadeBadgeClass(prioridade: string): string {
    switch (prioridade) {
      case 'URGENTE': return 'badge-danger';
      case 'ALTA': return 'badge-warning';
      case 'MEDIA': return 'badge-primary';
      default: return 'badge-secondary';
    }
  }

  getStatusLabel(status: string): string {
    switch (status) {
      case 'A_FAZER': return 'A Fazer';
      case 'EM_DESENVOLVIMENTO': return 'Em Andamento';
      case 'EM_REVISAO': return 'Em Revisão';
      case 'NAO_HOMOLOGADA': return 'Não Homologada';
      case 'ATRASADA': return 'Atrasada';
      case 'CONCLUIDA': return 'Concluído';
      default: return status;
    }
  }

  getProgressBarClass(percentual: number): string {
    if (percentual >= 100) return 'bg-success';
    return '';
  }
}
