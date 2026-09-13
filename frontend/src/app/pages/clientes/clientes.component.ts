import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Cliente, Fatura, Municipio, ServicoCatalogo } from '../../core/models';
import { HeaderComponent } from '../../shared/components/header.component';
import { SidebarComponent } from '../../shared/components/sidebar.component';

@Component({
  selector: 'app-clientes',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-header 
          title="Gestão de Clientes & Marcas" 
          subtitle="Contratos mensais, faturamento e marcas parceiras"
          (refreshAction)="carregarDados()"
        ></app-header>

        <main class="page-body">
          <div class="page-top-bar">
            <div>
              <span class="badge-subtitle">GESTÃO DE MARCAS</span>
              <h1 class="page-title">Cadastre novos clientes de design e contratos mensais.</h1>
            </div>
            <div class="top-actions">
              <button class="btn-primary" (click)="openNovoClienteModal()">
                <i class="bi bi-plus-lg"></i> NOVO CLIENTE
              </button>
            </div>
          </div>

          <!-- Filtros e Busca -->
          <div class="search-filter-card">
            <div class="search-input-wrap">
              <i class="bi bi-search"></i>
              <input 
                type="text" 
                placeholder="Buscar cliente por nome, segmento ou município..." 
                [(ngModel)]="filtroTermo"
                (input)="filtrarClientes()"
              />
            </div>
            <div class="filter-actions">
              <span class="active-count-tag">
                <i class="bi bi-building-check"></i> {{ clientesFiltrados().length }} Marcas Ativas
              </span>
            </div>
          </div>

          <!-- Grid de Marcas Ativas (Card igual a imagem 16714.jpg) -->
          <div class="marcas-grid stagger-grid">
            <div class="marca-card hover-lift" *ngFor="let cliente of clientesFiltrados()">
              <div class="marca-card-header">
                <div class="marca-logo-wrap">
                  <img *ngIf="cliente.logoUrl" [src]="cliente.logoUrl" [alt]="cliente.nome" />
                  <div *ngIf="!cliente.logoUrl" class="logo-placeholder">
                    {{ cliente.nome.substring(0, 2).toUpperCase() }}
                  </div>
                </div>
                <div class="marca-header-text">
                  <h3 class="marca-nome">{{ cliente.nome }}</h3>
                  <span class="marca-categoria">{{ cliente.categoria || cliente.segmento || 'CLIENTE' }}</span>
                </div>
              </div>

              <div class="marca-details">
                <div class="detail-row">
                  <span class="detail-label">Contratos:</span>
                  <span class="detail-value highlight-contract">{{ cliente.planoContrato || 'Plano Profissional ++' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">Telefone:</span>
                  <span class="detail-value">{{ cliente.telefone || '(82) 9985-3023' }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">Dia de Faturamento:</span>
                  <span class="detail-value font-semibold">Dia {{ cliente.diaFaturamento || 17 }}</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">Valor Mensal:</span>
                  <span class="detail-value price-tag">R$ {{ (cliente.valorMensal || 500).toFixed(2) }}</span>
                </div>
                <div class="detail-row" *ngIf="cliente.municipio">
                  <span class="detail-label">Município:</span>
                  <span class="detail-value"><i class="bi bi-geo-alt-fill text-muted"></i> {{ cliente.municipio }}</span>
                </div>
              </div>

              <!-- Resumo de Faturamento do Mês -->
              <div class="marca-faturamento-box">
                <div class="fatura-status-mini">
                  <span>Status Setembro:</span>
                  <span class="status-badge" [ngClass]="getStatusSetembro(cliente.id!).toLowerCase()">
                    {{ getStatusSetembro(cliente.id!) }}
                  </span>
                </div>
              </div>

              <div class="marca-card-actions">
                <button class="btn-action-primary" (click)="openLancarFaturaModal(cliente)">
                  <i class="bi bi-receipt"></i> Faturas & Pagamentos
                </button>
                <div class="actions-sub">
                  <button class="btn-text-edit" (click)="openEditarClienteModal(cliente)">
                    Editar Informações
                  </button>
                  <button class="btn-text-delete" (click)="excluirCliente(cliente)">
                    Excluir
                  </button>
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>

    <!-- MODAL: LANÇAMENTO DE FATURAS (Igual a imagem 16750.jpg) -->
    <div class="modal-backdrop" *ngIf="faturaModalOpen()">
      <div class="modal-card modal-lg animate-fade-in" *ngIf="clienteSelecionado()">
        <div class="modal-header">
          <div>
            <span class="badge-subtitle">FINANCEIRO & COBRANÇA</span>
            <h2 class="modal-title">LANÇAMENTO DE FATURAS</h2>
          </div>
          <button class="close-btn" (click)="faturaModalOpen.set(false)"><i class="bi bi-x-lg"></i></button>
        </div>

        <div class="modal-body">
          <!-- Card de Resumo do Cliente (Exatamente como em 16750.jpg) -->
          <div class="fatura-client-summary-card">
            <div class="summary-top">
              <div>
                <h3 class="client-name-bold">{{ clienteSelecionado()?.nome }}</h3>
                <span class="vencimento-info">Vencimento: Dia {{ clienteSelecionado()?.diaFaturamento || 17 }}</span>
              </div>
              <div class="client-monthly-total">
                R$ {{ (clienteSelecionado()?.valorMensal || 500).toFixed(2) }}
              </div>
            </div>

            <div class="summary-metrics">
              <div class="metric-paid">
                <span class="metric-lbl">Pago:</span>
                <span class="metric-val green">R$ {{ calcularPagoCliente(clienteSelecionado()?.id!).toFixed(2) }}</span>
              </div>
              <div class="metric-open">
                <span class="metric-lbl">Em aberto:</span>
                <span class="metric-val orange">R$ {{ calcularAbertoCliente(clienteSelecionado()?.id!).toFixed(2) }}</span>
              </div>
            </div>
          </div>

          <!-- Formulário de + Lançar Fatura (Exatamente como em 16750.jpg) -->
          <div class="lancar-fatura-form-box">
            <h4 class="form-section-title"><i class="bi bi-plus-circle-fill text-purple"></i> Lançar Fatura</h4>
            
            <div class="form-grid-2">
              <div class="form-group">
                <label>VENCIMENTO</label>
                <input type="date" [(ngModel)]="novaFatura.dataVencimento" class="form-control" />
              </div>
              <div class="form-group">
                <label>DATA PAGAMENTO</label>
                <input type="date" [(ngModel)]="novaFatura.dataPagamento" class="form-control" placeholder="dd/mm/aaaa" />
              </div>
            </div>

            <div class="form-grid-2">
              <div class="form-group">
                <label>VALOR</label>
                <input type="number" [(ngModel)]="novaFatura.valor" class="form-control" placeholder="500" />
              </div>
              <div class="form-group">
                <label>STATUS</label>
                <select [(ngModel)]="novaFatura.status" class="form-control">
                  <option value="Pendente">Pendente</option>
                  <option value="Pago">Pago</option>
                  <option value="Atrasado">Atrasado</option>
                </select>
              </div>
            </div>

            <button class="btn-lancar-fatura" (click)="salvarNovaFatura()">
              LANÇAR FATURA
            </button>
          </div>

          <!-- Lista de Faturas Lançadas do Cliente -->
          <div class="historico-faturas-box">
            <h4 class="form-section-title">Histórico de Faturas do Cliente</h4>
            <div class="table-responsive" *ngIf="faturasDoCliente().length > 0; else semFaturas">
              <table class="styled-table">
                <thead>
                  <tr>
                    <th>Mês Ref.</th>
                    <th>Vencimento</th>
                    <th>Valor</th>
                    <th>Data Pagamento</th>
                    <th>Status</th>
                    <th>Ações</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let fat of faturasDoCliente()">
                    <td><strong>{{ fat.mesReferencia }}</strong></td>
                    <td>{{ fat.dataVencimento | date:'dd/MM/yyyy' }}</td>
                    <td class="font-semibold">R$ {{ fat.valor.toFixed(2) }}</td>
                    <td>{{ fat.dataPagamento ? (fat.dataPagamento | date:'dd/MM/yyyy') : '—' }}</td>
                    <td>
                      <span class="status-pill" [ngClass]="fat.status.toLowerCase()">
                        {{ fat.status }}
                      </span>
                    </td>
                    <td>
                      <div class="table-actions">
                        <button 
                          class="btn-toggle-status" 
                          [class.btn-mark-paid]="fat.status !== 'PAGO'"
                          [class.btn-mark-pending]="fat.status === 'PAGO'"
                          (click)="alternarStatusFatura(fat)"
                          [title]="fat.status === 'PAGO' ? 'Marcar como Pendente' : 'Confirmar Pagamento'"
                        >
                          <i class="bi" [ngClass]="fat.status === 'PAGO' ? 'bi-arrow-counterclockwise' : 'bi-check2-circle'"></i>
                          {{ fat.status === 'PAGO' ? 'Mudar p/ Pendente' : 'Marcar como Pago' }}
                        </button>
                        <button class="btn-icon-del" (click)="excluirFatura(fat.id!)" title="Remover Fatura">
                          <i class="bi bi-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <ng-template #semFaturas>
              <div class="empty-state-mini">
                <i class="bi bi-receipt"></i>
                <p>Nenhuma fatura lançada para este cliente.</p>
              </div>
            </ng-template>
          </div>
        </div>
      </div>
    </div>

    <!-- MODAL: CADASTRAR / EDITAR CLIENTE -->
    <div class="modal-backdrop" *ngIf="clienteModalOpen()">
      <div class="modal-card animate-fade-in">
        <div class="modal-header">
          <h2 class="modal-title">{{ editandoClienteId ? 'Editar Informações do Cliente' : 'Novo Cliente / Marca' }}</h2>
          <button class="close-btn" (click)="clienteModalOpen.set(false)"><i class="bi bi-x-lg"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>NOME DA MARCA / CLIENTE *</label>
            <input type="text" [(ngModel)]="clienteForm.nome" class="form-control" placeholder="Ex: ACADEMIA TITANIUM" />
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>CATEGORIA / SEGMENTO</label>
              <input type="text" [(ngModel)]="clienteForm.categoria" class="form-control" placeholder="Ex: ACADEMIA" />
            </div>
            <div class="form-group">
              <label>CONTRATO / PLANO</label>
              <select [(ngModel)]="clienteForm.planoContrato" class="form-control">
                <option *ngFor="let s of servicos()" [value]="s.nome">{{ s.nome }} (R$ {{ s.preco }})</option>
                <option value="Plano Profissional ++">Plano Profissional ++ (R$ 500,00)</option>
                <option value="Plano Creator Plus">Plano Creator Plus (R$ 600,00)</option>
                <option value="Plano Essencial">Plano Essencial (R$ 300,00)</option>
              </select>
            </div>
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>TELEFONE / WHATSAPP</label>
              <input type="text" [(ngModel)]="clienteForm.telefone" class="form-control" placeholder="Ex: (82) 9985-3023" />
            </div>
            <div class="form-group">
              <label>MUNICÍPIO DE ATENDIMENTO</label>
              <select [(ngModel)]="clienteForm.municipio" class="form-control">
                <option *ngFor="let m of municipios()" [value]="m.nome">{{ m.nome }} - {{ m.uf }}</option>
              </select>
            </div>
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>DIA DE FATURAMENTO (VENCIMENTO)</label>
              <input type="number" [(ngModel)]="clienteForm.diaFaturamento" class="form-control" placeholder="17" min="1" max="31" />
            </div>
            <div class="form-group">
              <label>VALOR MENSAL (R$)</label>
              <input type="number" [(ngModel)]="clienteForm.valorMensal" class="form-control" placeholder="500" />
            </div>
          </div>

          <div class="form-group">
            <label>FOTO / LOGOTIPO DA MARCA</label>
            <div class="custom-file-upload-box" (click)="logoFileInput.click()">
              <input 
                type="file" 
                #logoFileInput 
                (change)="onLogoFileSelected($event)" 
                accept="image/*,.png,.jpg,.jpeg,.webp,.gif,.bmp,.svg,.ico,.tiff,.heic" 
                style="display: none" 
              />
              <div *ngIf="!clienteForm.logoUrl" class="upload-placeholder">
                <i class="bi bi-cloud-arrow-up text-primary" style="font-size: 2rem;"></i>
                <span class="upload-title">Clique para selecionar imagem no computador</span>
                <small class="upload-sub">Formatos aceitos: PNG, JPG, JPEG, WEBP, SVG, GIF, BMP e outros</small>
              </div>
              <div *ngIf="clienteForm.logoUrl" class="upload-preview-container" (click)="$event.stopPropagation()">
                <img [src]="clienteForm.logoUrl" alt="Logo preview" class="preview-img-square" />
                <div class="preview-meta">
                  <span class="preview-title"><i class="bi bi-check-circle-fill text-success"></i> Imagem carregada</span>
                  <div class="preview-actions">
                    <button type="button" class="btn-action-small btn-trocar" (click)="logoFileInput.click()">
                      <i class="bi bi-arrow-repeat"></i> Trocar
                    </button>
                    <button type="button" class="btn-action-small btn-remover" (click)="clienteForm.logoUrl = ''">
                      <i class="bi bi-trash"></i> Remover
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <div class="modal-actions-right">
            <button class="btn-cancel" (click)="clienteModalOpen.set(false)">Cancelar</button>
            <button class="btn-save" (click)="salvarCliente()">Salvar Cliente</button>
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
      align-items: flex-start;
      margin-bottom: 1.5rem;
      gap: 1rem;
    }
    .badge-subtitle {
      font-size: 0.75rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      color: var(--primary);
      text-transform: uppercase;
      display: inline-block;
      margin-bottom: 0.35rem;
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
      transition: all 0.2s;
    }
    .btn-primary:hover {
      background: #0f172a;
      transform: translateY(-1px);
    }
    .search-filter-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 0.85rem 1.25rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.75rem;
      gap: 1rem;
    }
    .search-input-wrap {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      flex: 1;
      color: var(--text-secondary);
    }
    .search-input-wrap input {
      border: none;
      background: transparent;
      outline: none;
      width: 100%;
      color: var(--text-primary);
      font-size: 0.92rem;
    }
    .active-count-tag {
      font-size: 0.82rem;
      font-weight: 700;
      color: var(--primary);
      background: rgba(124, 58, 237, 0.1);
      padding: 0.4rem 0.8rem;
      border-radius: 20px;
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
    }
    .marcas-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
      gap: 1.5rem;
    }
    .marca-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.25rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
      display: flex;
      flex-direction: column;
      gap: 1rem;
      transition: transform 0.2s, box-shadow 0.2s;
    }
    .marca-card:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
    }
    .marca-card-header {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .marca-logo-wrap {
      width: 48px;
      height: 48px;
      border-radius: 10px;
      overflow: hidden;
      background: #0f172a;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }
    .marca-logo-wrap img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .logo-placeholder {
      font-weight: 800;
      color: #fff;
      font-size: 1.1rem;
    }
    .marca-header-text {
      flex: 1;
    }
    .marca-nome {
      font-size: 1.05rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 0.2rem 0;
      letter-spacing: 0.02em;
    }
    .marca-categoria {
      font-size: 0.72rem;
      font-weight: 700;
      color: var(--text-secondary);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .marca-details {
      display: flex;
      flex-direction: column;
      gap: 0.45rem;
      padding: 0.75rem 0;
      border-top: 1px solid var(--border-color);
      border-bottom: 1px solid var(--border-color);
    }
    .detail-row {
      display: flex;
      justify-content: space-between;
      font-size: 0.84rem;
    }
    .detail-label {
      color: var(--text-secondary);
    }
    .detail-value {
      color: var(--text-primary);
      font-weight: 500;
    }
    .highlight-contract {
      color: var(--primary);
      font-weight: 700;
    }
    .price-tag {
      font-weight: 800;
      color: #10b981;
    }
    .marca-faturamento-box {
      background: rgba(124, 58, 237, 0.04);
      padding: 0.6rem 0.8rem;
      border-radius: 8px;
    }
    .fatura-status-mini {
      display: flex;
      justify-content: space-between;
      align-items: center;
      font-size: 0.8rem;
      font-weight: 600;
    }
    .status-badge {
      padding: 0.25rem 0.6rem;
      border-radius: 12px;
      font-size: 0.72rem;
      font-weight: 700;
    }
    .status-badge.pago { background: #dcfce7; color: #166534; }
    .status-badge.pendente { background: #fef3c7; color: #92400e; }
    .status-badge.atrasado { background: #fee2e2; color: #991b1b; }

    .marca-card-actions {
      display: flex;
      flex-direction: column;
      gap: 0.6rem;
      margin-top: auto;
    }
    .btn-action-primary {
      background: var(--primary);
      color: #fff;
      border: none;
      padding: 0.6rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.82rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.45rem;
      transition: background 0.2s;
    }
    .btn-action-primary:hover {
      filter: brightness(1.1);
    }
    .actions-sub {
      display: flex;
      justify-content: space-between;
    }
    .btn-text-edit {
      background: none;
      border: none;
      color: var(--primary);
      font-size: 0.8rem;
      font-weight: 700;
      cursor: pointer;
      padding: 0;
    }
    .btn-text-delete {
      background: none;
      border: none;
      color: #ef4444;
      font-size: 0.8rem;
      font-weight: 700;
      cursor: pointer;
      padding: 0;
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
      max-height: 90vh;
      overflow-y: auto;
      box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.5);
    }
    .modal-lg {
      max-width: 720px;
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

    /* Card 16750.jpg LANÇAMENTO DE FATURAS */
    .fatura-client-summary-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.2rem;
      box-shadow: 0 2px 8px rgba(0,0,0,0.03);
    }
    .summary-top {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1rem;
    }
    .client-name-bold {
      font-size: 1.15rem;
      font-weight: 800;
      margin: 0 0 0.2rem 0;
      color: var(--text-primary);
      letter-spacing: 0.02em;
    }
    .vencimento-info {
      font-size: 0.8rem;
      color: var(--text-secondary);
    }
    .client-monthly-total {
      font-size: 1.2rem;
      font-weight: 900;
      color: var(--text-primary);
    }
    .summary-metrics {
      display: flex;
      justify-content: space-between;
      padding-top: 0.8rem;
      border-top: 1px solid var(--border-color);
      font-size: 0.88rem;
      font-weight: 700;
    }
    .metric-paid .green { color: #10b981; }
    .metric-open .orange { color: #f59e0b; }

    .lancar-fatura-form-box {
      background: rgba(124, 58, 237, 0.04);
      border: 1.5px solid rgba(124, 58, 237, 0.2);
      border-radius: 12px;
      padding: 1.2rem;
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    .form-section-title {
      font-size: 0.95rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }
    .text-purple { color: var(--primary); }
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
      letter-spacing: 0.04em;
      text-transform: uppercase;
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
    .btn-lancar-fatura {
      background: var(--color-primary-gradient);
      color: #fff;
      border: none;
      padding: 0.85rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.92rem;
      cursor: pointer;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      transition: all 0.2s;
      box-shadow: 0 4px 14px rgba(124, 58, 237, 0.35);
    }
    .btn-lancar-fatura:hover {
      box-shadow: 0 6px 20px rgba(124, 58, 237, 0.5);
      filter: brightness(1.06);
      transform: translateY(-1px);
    }

    /* Tabela */
    .styled-table {
      width: 100%;
      border-collapse: collapse;
      font-size: 0.82rem;
      text-align: left;
    }
    .styled-table th {
      padding: 0.65rem 0.75rem;
      background: var(--bg-surface-elevated);
      color: var(--text-secondary);
      font-weight: 800;
      text-transform: uppercase;
      letter-spacing: 0.04em;
      border-bottom: 1.5px solid var(--border-color);
    }
    .styled-table td {
      padding: 0.75rem;
      border-bottom: 1px solid var(--border-color);
      color: var(--text-primary);
      font-weight: 500;
    }
    .status-pill {
      padding: 0.25rem 0.6rem;
      border-radius: 10px;
      font-size: 0.72rem;
      font-weight: 800;
      text-transform: uppercase;
      display: inline-block;
    }
    .status-pill.pago { background: rgba(16, 185, 129, 0.15); color: #10b981; border: 1px solid rgba(16, 185, 129, 0.3); }
    .status-pill.pendente { background: rgba(245, 158, 11, 0.15); color: #f59e0b; border: 1px solid rgba(245, 158, 11, 0.3); }
    .status-pill.atrasado { background: rgba(239, 68, 68, 0.15); color: #ef4444; border: 1px solid rgba(239, 68, 68, 0.3); }

    .table-actions {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .btn-toggle-status {
      border: none;
      padding: 0.35rem 0.65rem;
      border-radius: 6px;
      font-size: 0.72rem;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.3rem;
    }
    .btn-mark-paid {
      background: #10b981;
      color: #fff;
    }
    .btn-mark-pending {
      background: #f59e0b;
      color: #fff;
    }
    .btn-icon-del {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      font-size: 0.95rem;
    }

    .modal-actions-right {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      margin-top: 0.5rem;
    }
    .btn-cancel {
      background: transparent;
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      padding: 0.65rem 1.2rem;
      border-radius: 8px;
      font-weight: 700;
      cursor: pointer;
    }
    .btn-save {
      background: var(--primary);
      color: #fff;
      border: none;
      padding: 0.65rem 1.35rem;
      border-radius: 8px;
      font-weight: 700;
      cursor: pointer;
    }
    .empty-state-mini {
      text-align: center;
      padding: 1.5rem;
      color: var(--text-secondary);
      font-size: 0.85rem;
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
    .preview-img-square {
      width: 72px;
      height: 72px;
      object-fit: cover;
      border-radius: 10px;
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
export class ClientesComponent implements OnInit {
  private api = inject(ApiService);

  clientes = signal<Cliente[]>([]);
  clientesFiltrados = signal<Cliente[]>([]);
  faturas = signal<Fatura[]>([]);
  servicos = signal<ServicoCatalogo[]>([]);
  municipios = signal<Municipio[]>([]);

  filtroTermo = '';

  // Modais
  clienteModalOpen = signal(false);
  faturaModalOpen = signal(false);
  clienteSelecionado = signal<Cliente | null>(null);
  editandoClienteId: number | null = null;

  clienteForm: Partial<Cliente> = {
    nome: '',
    categoria: '',
    planoContrato: 'Plano Profissional ++',
    telefone: '',
    municipio: 'São Miguel dos Campos',
    diaFaturamento: 17,
    valorMensal: 500.0,
    logoUrl: '',
    status: 'ATIVO',
  };

  novaFatura = {
    dataVencimento: new Date().toISOString().split('T')[0],
    dataPagamento: '',
    valor: 500,
    status: 'Pendente',
  };

  ngOnInit(): void {
    this.carregarDados();
  }

  carregarDados(): void {
    this.api.getClientes().subscribe((res) => {
      this.clientes.set(res);
      this.filtrarClientes();
    });

    this.api.getFaturas().subscribe((res) => {
      this.faturas.set(res);
    });

    this.api.getServicos().subscribe((res) => {
      this.servicos.set(res);
    });

    this.api.getMunicipios().subscribe((res) => {
      this.municipios.set(res);
    });
  }

  filtrarClientes(): void {
    const termo = this.filtroTermo.toLowerCase().trim();
    if (!termo) {
      this.clientesFiltrados.set(this.clientes());
      return;
    }
    const filtrados = this.clientes().filter(
      (c) =>
        c.nome.toLowerCase().includes(termo) ||
        (c.categoria && c.categoria.toLowerCase().includes(termo)) ||
        (c.municipio && c.municipio.toLowerCase().includes(termo))
    );
    this.clientesFiltrados.set(filtrados);
  }

  getStatusSetembro(clienteId: number): string {
    const fat = this.faturas().find(
      (f) => f.clienteId === clienteId && (f.mesReferencia === '09/2026' || f.mesReferencia.includes('09/'))
    );
    return fat ? fat.status : 'PENDENTE';
  }

  faturasDoCliente(): Fatura[] {
    const c = this.clienteSelecionado();
    if (!c) return [];
    return this.faturas().filter((f) => f.clienteId === c.id);
  }

  calcularPagoCliente(clienteId: number): number {
    return this.faturas()
      .filter((f) => f.clienteId === clienteId && f.status === 'PAGO')
      .reduce((acc, f) => acc + f.valor, 0);
  }

  calcularAbertoCliente(clienteId: number): number {
    return this.faturas()
      .filter((f) => f.clienteId === clienteId && (f.status === 'PENDENTE' || f.status === 'ATRASADO'))
      .reduce((acc, f) => acc + f.valor, 0);
  }

  openNovoClienteModal(): void {
    this.editandoClienteId = null;
    this.clienteForm = {
      nome: '',
      categoria: 'ACADEMIA',
      planoContrato: 'Plano Profissional ++',
      telefone: '(82) 9985-3023',
      municipio: 'São Miguel dos Campos',
      diaFaturamento: 17,
      valorMensal: 500.0,
      logoUrl: '',
      status: 'ATIVO',
    };
    this.clienteModalOpen.set(true);
  }

  openEditarClienteModal(cliente: Cliente): void {
    this.editandoClienteId = cliente.id || null;
    this.clienteForm = { ...cliente };
    this.clienteModalOpen.set(true);
  }

  salvarCliente(): void {
    if (!this.clienteForm.nome) return;

    if (this.editandoClienteId) {
      this.api.updateCliente(this.editandoClienteId, this.clienteForm).subscribe(() => {
        this.clienteModalOpen.set(false);
        this.carregarDados();
      });
    } else {
      this.api.createCliente(this.clienteForm).subscribe(() => {
        this.clienteModalOpen.set(false);
        this.carregarDados();
      });
    }
  }

  onLogoFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files[0]) {
      const file = input.files[0];
      const reader = new FileReader();
      reader.onload = (e: ProgressEvent<FileReader>) => {
        this.clienteForm.logoUrl = e.target?.result as string;
      };
      reader.readAsDataURL(file);
    }
  }

  excluirCliente(cliente: Cliente): void {
    if (confirm(`Tem certeza que deseja excluir o cliente "${cliente.nome}"?`)) {
      this.api.deleteCliente(cliente.id!).subscribe(() => {
        this.carregarDados();
      });
    }
  }

  openLancarFaturaModal(cliente: Cliente): void {
    this.clienteSelecionado.set(cliente);
    this.novaFatura = {
      dataVencimento: `2026-09-${String(cliente.diaFaturamento || 17).padStart(2, '0')}`,
      dataPagamento: '',
      valor: cliente.valorMensal || 500,
      status: 'Pendente',
    };
    this.faturaModalOpen.set(true);
  }

  salvarNovaFatura(): void {
    const cliente = this.clienteSelecionado();
    if (!cliente) return;

    const fat: Partial<Fatura> = {
      clienteId: cliente.id!,
      clienteNome: cliente.nome,
      mesReferencia: '09/2026',
      valor: Number(this.novaFatura.valor),
      dataVencimento: this.novaFatura.dataVencimento,
      dataPagamento: this.novaFatura.dataPagamento || undefined,
      status: this.novaFatura.status.toUpperCase() as 'PAGO' | 'PENDENTE' | 'ATRASADO',
      observacoes: `Fatura gerada manualmente - ${cliente.nome}`,
    };

    this.api.createFatura(fat).subscribe(() => {
      this.carregarDados();
    });
  }

  alternarStatusFatura(fat: Fatura): void {
    this.api.toggleStatusFatura(fat.id!).subscribe(() => {
      this.carregarDados();
    });
  }

  excluirFatura(id: number): void {
    if (confirm('Deseja excluir esta fatura?')) {
      this.api.deleteFatura(id).subscribe(() => {
        this.carregarDados();
      });
    }
  }
}
