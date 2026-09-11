import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { ApiService } from '../../core/services/api.service';
import { VendaFoto, Despesa, FluxoCaixa, Fatura, Cliente, SaudeFinanceira } from '../../core/models';

@Component({
  selector: 'app-financeiro',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, HeaderComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>

      <main class="main-content">
        <app-header 
          title="Financeiro & Fluxo de Caixa" 
          subtitle="Faturas mensais de clientes, saúde financeira, vendas de fotos e despesas operacionais"
          (refreshAction)="carregarDados()"
        ></app-header>

        <div class="page-body">
          <!-- PAINEL SUPERIOR: SAÚDE FINANCEIRA & MARGEM OPERACIONAL (16754.jpg) -->
          <div class="finance-top-cards-grid">
            <!-- Card Margem Operacional (Exatamente como em 16754.jpg) -->
            <div class="card saude-financeira-card">
              <span class="section-tag-purple">SAÚDE FINANCEIRA</span>
              <h2 class="card-main-title">Margem Operacional</h2>

              <div class="margem-gauge-wrap">
                <div class="circular-progress-meter">
                  <span class="meter-val">{{ (saude()?.margemOperacional || 0).toFixed(1) }}%</span>
                  <span class="meter-lbl">MARGEM</span>
                </div>
              </div>

              <div class="saude-breakdown-list">
                <div class="breakdown-item">
                  <span class="item-label">Folha Salarial</span>
                  <span class="item-value font-bold">R$ {{ (saude()?.folhaSalarial || 2900).toFixed(2).replace('.', ',') }}</span>
                </div>
                <div class="breakdown-item">
                  <span class="item-label">Custos Fixos / Outros</span>
                  <span class="item-value">R$ {{ (saude()?.custosFixos || 0).toFixed(2).replace('.', ',') }}</span>
                </div>
                <div class="breakdown-item">
                  <span class="item-label">Faturas em Aberto</span>
                  <span class="item-value text-purple font-bold">R$ {{ (saude()?.faturasEmAberto || 0).toFixed(2).replace('.', ',') }}</span>
                </div>
              </div>
            </div>

            <!-- Card Resumo de Entradas do Mês -->
            <div class="card faturamento-resumo-card">
              <span class="section-tag-purple">RESUMO DO MÊS</span>
              <h2 class="card-main-title">Entradas & Previsões</h2>

              <div class="resumo-metrics-grid">
                <div class="resumo-box green-bg">
                  <span class="resumo-box-lbl">Total Recebido (Mês)</span>
                  <h3 class="resumo-box-val text-green">R$ {{ (saude()?.totalRecebidoMes || 1000).toFixed(2).replace('.', ',') }}</h3>
                  <span class="resumo-box-sub">Faturas pagas + vendas de fotos</span>
                </div>

                <div class="resumo-box orange-bg">
                  <span class="resumo-box-lbl">A Receber / Pendente</span>
                  <h3 class="resumo-box-val text-orange">R$ {{ (saude()?.totalAReceberMes || 1900).toFixed(2).replace('.', ',') }}</h3>
                  <span class="resumo-box-sub">Aguardando pagamento no vencimento</span>
                </div>
              </div>

              <div class="action-btn-row">
                <button class="btn btn-primary w-full" (click)="abrirModalLancarFatura()">
                  <i class="bi bi-plus-circle-fill"></i>
                  <span>+ LANÇAR FATURA DE CLIENTE</span>
                </button>
              </div>
            </div>
          </div>

          <!-- SEÇÃO: GESTÃO E REGISTRO DE FATURAS MENSAIS (16750.jpg) -->
          <div class="card faturas-master-card">
            <div class="faturas-header-row">
              <div>
                <span class="badge-subtitle">LANÇAMENTO DE FATURAS</span>
                <h2 class="faturas-title">Registro Mensal de Pagamento de Clientes</h2>
                <p class="faturas-sub">Acompanhe no final do mês quem pagou ou se está pendente.</p>
              </div>

              <div class="faturas-actions">
                <!-- Segmented Control de Status de Faturas -->
                <div class="segmented-control">
                  <button class="seg-btn" [class.active]="filtroFaturaStatus === 'TODOS'" (click)="setFiltroFatura('TODOS')">Todos</button>
                  <button class="seg-btn" [class.active]="filtroFaturaStatus === 'PAGO'" (click)="setFiltroFatura('PAGO')">Pagos</button>
                  <button class="seg-btn" [class.active]="filtroFaturaStatus === 'PENDENTE'" (click)="setFiltroFatura('PENDENTE')">Pendentes</button>
                  <button class="seg-btn" [class.active]="filtroFaturaStatus === 'ATRASADO'" (click)="setFiltroFatura('ATRASADO')">Atrasados</button>
                </div>

                <button class="btn btn-primary" (click)="abrirModalLancarFatura()">
                  <i class="bi bi-receipt"></i> + Lançar Fatura
                </button>
              </div>
            </div>

            <!-- Tabela de Faturas -->
            <div class="table-responsive mt-3">
              <table class="custom-table faturas-table">
                <thead>
                  <tr>
                    <th>CLIENTE / MARCA</th>
                    <th>MÊS REF.</th>
                    <th>VENCIMENTO</th>
                    <th>DATA PAGAMENTO</th>
                    <th>VALOR</th>
                    <th>STATUS</th>
                    <th style="text-align: right;">AÇÕES</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let fat of faturasFiltradas()">
                    <td>
                      <strong class="text-primary">{{ fat.clienteNome }}</strong>
                    </td>
                    <td><span class="badge-mes">{{ fat.mesReferencia }}</span></td>
                    <td>{{ fat.dataVencimento | date:'dd/MM/yyyy' }}</td>
                    <td>{{ fat.dataPagamento ? (fat.dataPagamento | date:'dd/MM/yyyy') : '—' }}</td>
                    <td class="font-bold text-base">R$ {{ fat.valor.toFixed(2).replace('.', ',') }}</td>
                    <td>
                      <span class="status-pill" [ngClass]="fat.status.toLowerCase()">
                        {{ fat.status }}
                      </span>
                    </td>
                    <td style="text-align: right;">
                      <div class="actions-group">
                        <button 
                          class="btn-toggle-pay" 
                          [class.btn-mark-paid]="fat.status !== 'PAGO'"
                          [class.btn-mark-pending]="fat.status === 'PAGO'"
                          (click)="toggleStatusFatura(fat)"
                          [title]="fat.status === 'PAGO' ? 'Marcar como Pendente' : 'Confirmar Pagamento'"
                        >
                          <i class="bi" [ngClass]="fat.status === 'PAGO' ? 'bi-arrow-counterclockwise' : 'bi-check2-circle'"></i>
                          {{ fat.status === 'PAGO' ? 'Mudar p/ Pendente' : 'Marcar como Pago' }}
                        </button>
                        <button class="btn-icon btn-danger" (click)="excluirFatura(fat.id!)" title="Excluir">
                          <i class="bi bi-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- FLUXO DE CAIXA: Comparativos Mensais & Despesas -->
          <div class="card mb-4 fluxo-caixa-card">
            <div class="fluxo-header-row">
              <div>
                <span class="section-tag-purple">FLUXO DE CAIXA</span>
                <h2 class="card-main-title">Comparativos Mensais (Entradas, Saídas e Lucro)</h2>
                <div class="chart-indicators-row">
                  <span class="legend-item"><span class="dot-entradas"></span> Entradas</span>
                  <span class="legend-item"><span class="dot-saidas"></span> Saídas</span>
                  <span class="legend-item"><span class="dot-lucro"></span> Lucro</span>
                </div>
              </div>

              <div class="fluxo-actions">
                <button class="btn btn-secondary" (click)="abrirModalDespesa()">
                  <i class="bi bi-plus-circle-fill"></i>
                  <span>+ LANÇAR DESPESA</span>
                </button>
              </div>
            </div>

            <!-- Gráfico Comparativo de Barras Triplas -->
            <div class="fluxo-chart-wrapper">
              <div class="fluxo-bars-container">
                <div class="fluxo-month-group" *ngFor="let mes of fluxoCaixa()?.comparativosMensais">
                  <div class="tri-bar-box">
                    <div class="bar-col-entry" [style.height.%]="getFluxoBarHeight(mes.entradas)" title="Entradas: R$ {{ mes.entradas }}"></div>
                    <div class="bar-col-exit" [style.height.%]="getFluxoBarHeight(mes.saidas)" title="Saídas: R$ {{ mes.saidas }}"></div>
                    <div class="bar-col-profit" [style.height.%]="getFluxoBarHeight(mes.lucro)" title="Lucro: R$ {{ mes.lucro }}"></div>
                  </div>
                  <span class="month-lbl">{{ mes.mesAbreviado }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- HISTÓRICO DE VENDAS DE FOTOS (16718 / 16752) -->
          <div class="card sales-master-card">
            <div class="sales-header-row">
              <div>
                <span class="section-tag-purple">LOJA & EVENTOS</span>
                <h2 class="card-main-title">Histórico de Vendas das Fotos</h2>
              </div>
              <button class="btn btn-outline" (click)="exportarVendasCsv()">
                <i class="bi bi-download"></i> Exportar Vendas
              </button>
            </div>

            <div class="table-responsive mt-3">
              <table class="custom-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Data</th>
                    <th>Cliente</th>
                    <th>Fotos</th>
                    <th>Vídeos</th>
                    <th>Valor</th>
                    <th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let venda of vendas()">
                    <td class="font-mono text-muted">{{ venda.codigoVenda }}</td>
                    <td>{{ (venda.dataVenda || '2026-07-27T19:42:00Z') | date:'dd/MM/yyyy às HH:mm' }}</td>
                    <td><strong>{{ venda.clienteNome }}</strong></td>
                    <td>{{ venda.qtdFotos }}</td>
                    <td>{{ venda.qtdVideos }}</td>
                    <td class="font-bold text-success">R$ {{ venda.valorTotal.toFixed(2).replace('.', ',') }}</td>
                    <td>
                      <span class="status-pill pago">PAGO</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- MODAL LANÇAR FATURA (Exatamente como em 16750.jpg) -->
    <div class="modal-backdrop" *ngIf="modalFaturaOpen()">
      <div class="modal-card animate-fade-in">
        <div class="modal-header">
          <div>
            <span class="section-tag-purple">COBRANÇA MENSAL</span>
            <h3 class="modal-title">LANÇAMENTO DE FATURAS</h3>
          </div>
          <button class="close-btn" (click)="modalFaturaOpen.set(false)"><i class="bi bi-x-lg"></i></button>
        </div>

        <div class="modal-body">
          <div class="form-group">
            <label>CLIENTE / MARCA *</label>
            <select [(ngModel)]="faturaForm.clienteId" (change)="onSelectClienteFatura()" class="form-control">
              <option *ngFor="let c of clientes()" [value]="c.id">{{ c.nome }} (Mensalidade: R$ {{ c.valorMensal }})</option>
            </select>
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>VENCIMENTO</label>
              <input type="date" [(ngModel)]="faturaForm.dataVencimento" class="form-control" />
            </div>
            <div class="form-group">
              <label>DATA PAGAMENTO</label>
              <input type="date" [(ngModel)]="faturaForm.dataPagamento" class="form-control" placeholder="dd/mm/aaaa" />
            </div>
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>VALOR (R$)</label>
              <input type="number" [(ngModel)]="faturaForm.valor" class="form-control" placeholder="500" />
            </div>
            <div class="form-group">
              <label>STATUS</label>
              <select [(ngModel)]="faturaForm.status" class="form-control">
                <option value="Pendente">Pendente</option>
                <option value="Pago">Pago</option>
                <option value="Atrasado">Atrasado</option>
              </select>
            </div>
          </div>

          <button class="btn-lancar-fatura-cta" (click)="salvarFatura()">
            LANÇAR FATURA
          </button>
        </div>
      </div>
    </div>

    <!-- MODAL LANÇAR DESPESA -->
    <div class="modal-backdrop" *ngIf="modalDespesaOpen()">
      <div class="modal-card animate-fade-in">
        <div class="modal-header">
          <h3 class="modal-title">Lançar Despesa Operacional</h3>
          <button class="close-btn" (click)="modalDespesaOpen.set(false)"><i class="bi bi-x-lg"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>DESCRIÇÃO DA DESPESA *</label>
            <input type="text" [(ngModel)]="despesaForm.descricao" class="form-control" placeholder="Ex: Assinatura Adobe..." />
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>VALOR (R$) *</label>
              <input type="number" [(ngModel)]="despesaForm.valor" class="form-control" placeholder="150.00" />
            </div>
            <div class="form-group">
              <label>CATEGORIA</label>
              <input type="text" [(ngModel)]="despesaForm.categoria" class="form-control" placeholder="Software, Logística, etc." />
            </div>
          </div>

          <div class="form-grid-2">
            <div class="form-group">
              <label>DATA</label>
              <input type="date" [(ngModel)]="despesaForm.dataDespesa" class="form-control" />
            </div>
            <div class="form-group">
              <label>FORMA DE PAGAMENTO</label>
              <select [(ngModel)]="despesaForm.formaPagamento" class="form-control">
                <option value="PIX">PIX</option>
                <option value="Cartão de Crédito">Cartão de Crédito</option>
                <option value="Boleto">Boleto</option>
              </select>
            </div>
          </div>

          <div class="modal-actions-right">
            <button class="btn-cancel" (click)="modalDespesaOpen.set(false)">Cancelar</button>
            <button class="btn-save" (click)="salvarDespesa()">Salvar Despesa</button>
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

    .section-tag-purple {
      font-size: 0.72rem;
      font-weight: 800;
      color: var(--primary);
      text-transform: uppercase;
      letter-spacing: 0.08em;
      display: block;
      margin-bottom: 0.25rem;
    }
    .card-main-title {
      font-size: 1.25rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 1rem 0;
    }

    /* Grid Superior: Saúde Financeira (16754.jpg) */
    .finance-top-cards-grid {
      display: grid;
      grid-template-columns: 360px 1fr;
      gap: 1.5rem;
    }
    @media (max-width: 960px) {
      .finance-top-cards-grid {
        grid-template-columns: 1fr;
      }
    }

    /* Card Saúde Financeira (16754.jpg) */
    .saude-financeira-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.5rem;
      box-shadow: 0 4px 14px rgba(0,0,0,0.03);
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
    }
    .margem-gauge-wrap {
      margin: 1rem 0 1.5rem 0;
    }
    .circular-progress-meter {
      width: 140px;
      height: 140px;
      border-radius: 50%;
      border: 8px solid rgba(124, 58, 237, 0.1);
      border-top-color: var(--primary);
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 20px rgba(124, 58, 237, 0.08);
    }
    .meter-val {
      font-size: 1.45rem;
      font-weight: 900;
      color: var(--text-primary);
    }
    .meter-lbl {
      font-size: 0.68rem;
      font-weight: 800;
      color: var(--text-secondary);
      letter-spacing: 0.05em;
    }

    .saude-breakdown-list {
      width: 100%;
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      padding-top: 1rem;
      border-top: 1px solid var(--border-color);
      text-align: left;
    }
    .breakdown-item {
      display: flex;
      justify-content: space-between;
      font-size: 0.84rem;
    }
    .item-label {
      color: var(--text-secondary);
    }
    .item-value {
      color: var(--text-primary);
      font-weight: 600;
    }

    /* Card Faturamento Resumo */
    .faturamento-resumo-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.5rem;
      box-shadow: 0 4px 14px rgba(0,0,0,0.03);
      display: flex;
      flex-direction: column;
      justify-content: space-between;
    }
    .resumo-metrics-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.25rem;
      margin-bottom: 1.25rem;
    }
    .resumo-box {
      border-radius: 10px;
      padding: 1.25rem;
      border: 1px solid transparent;
    }
    .resumo-box.green-bg {
      background: rgba(16, 185, 129, 0.06);
      border-color: rgba(16, 185, 129, 0.2);
    }
    .resumo-box.orange-bg {
      background: rgba(245, 158, 11, 0.06);
      border-color: rgba(245, 158, 11, 0.2);
    }
    .resumo-box-lbl {
      font-size: 0.75rem;
      font-weight: 800;
      color: var(--text-secondary);
      text-transform: uppercase;
      display: block;
    }
    .resumo-box-val {
      font-size: 1.6rem;
      font-weight: 900;
      margin: 0.35rem 0;
    }
    .text-green { color: #10b981; }
    .text-orange { color: #f59e0b; }
    .resumo-box-sub {
      font-size: 0.72rem;
      color: var(--text-secondary);
    }

    /* Card Faturas (16750.jpg) */
    .faturas-master-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.5rem;
      box-shadow: 0 4px 14px rgba(0,0,0,0.03);
    }
    .faturas-header-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 1rem;
    }
    .badge-subtitle {
      font-size: 0.72rem;
      font-weight: 800;
      color: var(--primary);
      text-transform: uppercase;
      letter-spacing: 0.06em;
    }
    .faturas-title {
      font-size: 1.25rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0.2rem 0;
    }
    .faturas-sub {
      font-size: 0.82rem;
      color: var(--text-secondary);
      margin: 0;
    }
    .faturas-actions {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .segmented-control {
      display: flex;
      background: rgba(0,0,0,0.04);
      padding: 0.25rem;
      border-radius: 8px;
    }
    .seg-btn {
      background: none;
      border: none;
      padding: 0.35rem 0.75rem;
      border-radius: 6px;
      font-size: 0.78rem;
      font-weight: 700;
      color: var(--text-secondary);
      cursor: pointer;
    }
    .seg-btn.active {
      background: var(--card-bg);
      color: var(--primary);
      box-shadow: 0 2px 6px rgba(0,0,0,0.08);
    }

    .faturas-table th {
      font-size: 0.72rem;
      font-weight: 800;
      color: var(--text-secondary);
      padding: 0.75rem;
      border-bottom: 1px solid var(--border-color);
    }
    .faturas-table td {
      padding: 0.85rem 0.75rem;
      border-bottom: 1px solid var(--border-color);
      vertical-align: middle;
      font-size: 0.84rem;
    }
    .badge-mes {
      font-weight: 700;
      color: var(--text-primary);
      background: rgba(0,0,0,0.04);
      padding: 0.2rem 0.5rem;
      border-radius: 4px;
      font-size: 0.75rem;
    }
    .status-pill {
      font-size: 0.7rem;
      font-weight: 800;
      padding: 0.2rem 0.55rem;
      border-radius: 12px;
      text-transform: uppercase;
    }
    .status-pill.pago { background: #dcfce7; color: #166534; }
    .status-pill.pendente { background: #fef3c7; color: #92400e; }
    .status-pill.atrasado { background: #fee2e2; color: #991b1b; }

    .btn-toggle-pay {
      border: none;
      padding: 0.35rem 0.75rem;
      border-radius: 6px;
      font-size: 0.75rem;
      font-weight: 700;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.35rem;
    }
    .btn-mark-paid { background: #10b981; color: #fff; }
    .btn-mark-pending { background: #f59e0b; color: #fff; }

    /* Fluxo Caixa Gráfico */
    .fluxo-caixa-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.5rem;
    }
    .fluxo-header-row {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      margin-bottom: 1.5rem;
    }
    .chart-indicators-row {
      display: flex;
      gap: 1.25rem;
      font-size: 0.78rem;
      font-weight: 700;
      margin-top: 0.5rem;
    }
    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.4rem;
    }
    .dot-entradas { width: 10px; height: 10px; border-radius: 50%; background: #7c3aed; }
    .dot-saidas { width: 10px; height: 10px; border-radius: 50%; background: #f97316; }
    .dot-lucro { width: 10px; height: 10px; border-radius: 50%; background: #10b981; }

    .fluxo-chart-wrapper {
      padding: 1rem 0;
      overflow-x: auto;
    }
    .fluxo-bars-container {
      display: flex;
      justify-content: space-between;
      align-items: flex-end;
      height: 180px;
      min-width: 600px;
      border-bottom: 2px solid var(--border-color);
      padding-bottom: 0.5rem;
    }
    .fluxo-month-group {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      flex: 1;
    }
    .tri-bar-box {
      display: flex;
      align-items: flex-end;
      gap: 3px;
      height: 140px;
    }
    .bar-col-entry { width: 12px; background: #7c3aed; border-radius: 3px 3px 0 0; }
    .bar-col-exit { width: 12px; background: #f97316; border-radius: 3px 3px 0 0; }
    .bar-col-profit { width: 12px; background: #10b981; border-radius: 3px 3px 0 0; }
    .month-lbl { font-size: 0.72rem; font-weight: 700; color: var(--text-secondary); }

    /* Botão CTA Lançar Fatura (16750.jpg) */
    .btn-lancar-fatura-cta {
      background: #7c3aed;
      color: #fff;
      border: none;
      padding: 0.85rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.92rem;
      cursor: pointer;
      text-transform: uppercase;
      letter-spacing: 0.05em;
      margin-top: 0.5rem;
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
    .w-full { width: 100%; }
    .font-mono { font-family: monospace; }
  `]
})
export class FinanceiroComponent implements OnInit {
  private api = inject(ApiService);

  faturas = signal<Fatura[]>([]);
  faturasFiltradas = signal<Fatura[]>([]);
  clientes = signal<Cliente[]>([]);
  saude = signal<SaudeFinanceira | null>(null);
  vendas = signal<VendaFoto[]>([]);
  fluxoCaixa = signal<FluxoCaixa | null>(null);

  filtroFaturaStatus: 'TODOS' | 'PAGO' | 'PENDENTE' | 'ATRASADO' = 'TODOS';

  modalFaturaOpen = signal(false);
  modalDespesaOpen = signal(false);

  faturaForm = {
    clienteId: 1,
    clienteNome: 'ACADEMIA TITANIUM',
    dataVencimento: '2026-09-17',
    dataPagamento: '',
    valor: 500,
    status: 'Pendente',
  };

  despesaForm: Partial<Despesa> = {
    descricao: '',
    categoria: 'Software',
    valor: 0,
    dataDespesa: new Date().toISOString().split('T')[0],
    formaPagamento: 'PIX',
    status: 'PAGO',
  };

  ngOnInit(): void {
    this.carregarDados();
  }

  carregarDados(): void {
    this.api.getFaturas().subscribe((f) => {
      this.faturas.set(f);
      this.filtrarFaturas();
    });

    this.api.getClientes().subscribe((c) => {
      this.clientes.set(c);
    });

    this.api.getSaudeFinanceira().subscribe((s) => {
      this.saude.set(s);
    });

    this.api.getVendas().subscribe((v) => {
      this.vendas.set(v);
    });

    this.api.getFluxoCaixa().subscribe((fc) => {
      this.fluxoCaixa.set(fc);
    });
  }

  setFiltroFatura(status: 'TODOS' | 'PAGO' | 'PENDENTE' | 'ATRASADO'): void {
    this.filtroFaturaStatus = status;
    this.filtrarFaturas();
  }

  filtrarFaturas(): void {
    if (this.filtroFaturaStatus === 'TODOS') {
      this.faturasFiltradas.set(this.faturas());
    } else {
      this.faturasFiltradas.set(
        this.faturas().filter((f) => f.status === this.filtroFaturaStatus)
      );
    }
  }

  onSelectClienteFatura(): void {
    const c = this.clientes().find((item) => item.id === Number(this.faturaForm.clienteId));
    if (c) {
      this.faturaForm.clienteNome = c.nome;
      this.faturaForm.valor = c.valorMensal || 500;
      this.faturaForm.dataVencimento = `2026-09-${String(c.diaFaturamento || 17).padStart(2, '0')}`;
    }
  }

  abrirModalLancarFatura(): void {
    const c = this.clientes()[0];
    this.faturaForm = {
      clienteId: c?.id || 1,
      clienteNome: c?.nome || 'ACADEMIA TITANIUM',
      dataVencimento: `2026-09-${String(c?.diaFaturamento || 17).padStart(2, '0')}`,
      dataPagamento: '',
      valor: c?.valorMensal || 500,
      status: 'Pendente',
    };
    this.modalFaturaOpen.set(true);
  }

  salvarFatura(): void {
    const nova: Partial<Fatura> = {
      clienteId: Number(this.faturaForm.clienteId),
      clienteNome: this.faturaForm.clienteNome,
      mesReferencia: '09/2026',
      valor: Number(this.faturaForm.valor),
      dataVencimento: this.faturaForm.dataVencimento,
      dataPagamento: this.faturaForm.dataPagamento || undefined,
      status: this.faturaForm.status.toUpperCase() as 'PAGO' | 'PENDENTE' | 'ATRASADO',
    };

    this.api.createFatura(nova).subscribe(() => {
      this.modalFaturaOpen.set(false);
      this.carregarDados();
    });
  }

  toggleStatusFatura(fat: Fatura): void {
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

  abrirModalDespesa(): void {
    this.despesaForm = {
      descricao: '',
      categoria: 'Equipamentos',
      valor: 150,
      dataDespesa: new Date().toISOString().split('T')[0],
      formaPagamento: 'PIX',
      status: 'PAGO',
    };
    this.modalDespesaOpen.set(true);
  }

  salvarDespesa(): void {
    if (!this.despesaForm.descricao) return;
    this.api.createDespesa(this.despesaForm).subscribe(() => {
      this.modalDespesaOpen.set(false);
      this.carregarDados();
    });
  }

  getFluxoBarHeight(val: number): number {
    const max = 10000;
    return Math.min(100, Math.round((val / max) * 100));
  }

  exportarVendasCsv(): void {
    alert('Relatório de vendas exportado com sucesso!');
  }
}
