import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { ApiService } from '../../core/services/api.service';
import { VendaFoto, Despesa, FluxoCaixa } from '../../core/models';

@Component({
  selector: 'app-financeiro',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, HeaderComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>

      <main class="main-content">
        <app-header 
          title="Financeiro, Vendas & Fluxo de Caixa" 
          subtitle="Histórico consolidado de vendas de fotos, fluxo de caixa mensal e controle de despesas"
          (refreshAction)="carregarDados()"
        ></app-header>

        <div class="page-body">
          <!-- FLUXO DE CAIXA: Comparativos Mensais (Página 7 do PDF) -->
          <div class="card mb-4 fluxo-caixa-card">
            <div class="fluxo-header-row">
              <div>
                <span class="section-tag text-teal">FLUXO DE CAIXA</span>
                <h2>Comparativos Mensais</h2>
                <div class="chart-indicators-row">
                  <span class="legend-item"><span class="dot-entradas"></span> Entradas</span>
                  <span class="legend-item"><span class="dot-saidas"></span> Saídas</span>
                  <span class="legend-item"><span class="dot-lucro"></span> Lucro</span>
                </div>
              </div>

              <div class="fluxo-actions">
                <button class="btn btn-primary" (click)="abrirModalDespesa()">
                  <i class="bi bi-plus-circle-fill"></i>
                  <span>+ LANÇAR DESPESA</span>
                </button>
                <button class="btn btn-secondary" (click)="abrirModalNovaVenda()">
                  <i class="bi bi-receipt"></i>
                  <span>+ Nova Venda</span>
                </button>
              </div>
            </div>

            <!-- Gráfico Comparativo de Barras Triplas (Entradas, Saídas, Lucro) -->
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

            <!-- Resumo dos Totais -->
            <div class="fluxo-summary-cards">
              <div class="summary-mini-card">
                <span class="text-muted text-xs">TOTAL ENTRADAS</span>
                <h3 class="text-primary font-bold">R$ {{ (fluxoCaixa()?.totalEntradas || 49800) | number:'1.2-2' }}</h3>
              </div>
              <div class="summary-mini-card">
                <span class="text-muted text-xs">TOTAL SAÍDAS / DESPESAS</span>
                <h3 class="text-warning font-bold">R$ {{ (fluxoCaixa()?.totalSaidas || 18800) | number:'1.2-2' }}</h3>
              </div>
              <div class="summary-mini-card">
                <span class="text-muted text-xs">LUCRO LÍQUIDO</span>
                <h3 class="text-success font-bold">R$ {{ (fluxoCaixa()?.lucroLiquido || 31000) | number:'1.2-2' }}</h3>
              </div>
            </div>
          </div>

          <!-- HISTÓRICO DE VENDAS DE FOTOS (Páginas 4 e 5 do PDF) -->
          <div class="card mb-4 sales-master-card">
            <div class="sales-header-row">
              <div>
                <h3>Histórico de Vendas de Fotos & Mídias</h3>
                <p class="text-muted mb-0">Listagem de clientes, status de pagamento e disponibilização</p>
              </div>

              <div class="sales-filters-and-export">
                <div class="segmented-control">
                  <button 
                    class="seg-btn" 
                    [class.active]="filtroStatusVenda === 'TODOS'"
                    (click)="setFiltroVenda('TODOS')"
                  >
                    Todos
                  </button>
                  <button 
                    class="seg-btn" 
                    [class.active]="filtroStatusVenda === 'PAGO'"
                    (click)="setFiltroVenda('PAGO')"
                  >
                    Pagos
                  </button>
                  <button 
                    class="seg-btn" 
                    [class.active]="filtroStatusVenda === 'PENDENTE'"
                    (click)="setFiltroVenda('PENDENTE')"
                  >
                    A Receber / Pendentes
                  </button>
                  <button 
                    class="seg-btn" 
                    [class.active]="filtroStatusVenda === 'ATRASADO'"
                    (click)="setFiltroVenda('ATRASADO')"
                  >
                    Atrasados
                  </button>
                </div>

                <button class="btn btn-secondary" (click)="exportarVendasCSV()">
                  <i class="bi bi-file-earmark-spreadsheet-fill"></i>
                  <span>Exportar Vendas</span>
                </button>
              </div>
            </div>

            <!-- Tabela de Vendas (Página 4 do PDF) -->
            <div class="table-responsive mt-3">
              <table class="custom-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>Data & Hora</th>
                    <th>Cliente</th>
                    <th>Fotos</th>
                    <th>Vídeos</th>
                    <th>Valor</th>
                    <th>Status</th>
                    <th style="text-align: right;">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let venda of vendas()">
                    <td><strong class="text-primary">{{ venda.codigoVenda }}</strong></td>
                    <td class="text-muted text-sm">{{ venda.dataVenda | date:'dd/MM/yyyy à\\s HH:mm' }}</td>
                    <td>
                      <div class="client-cell">
                        <span class="font-bold">{{ venda.clienteNome }}</span>
                        <span class="text-xs text-muted" *ngIf="venda.eventoNome">{{ venda.eventoNome }}</span>
                      </div>
                    </td>
                    <td><span class="badge badge-em-revisao">{{ venda.qtdFotos }}</span></td>
                    <td><span class="badge badge-em-dev">{{ venda.qtdVideos }}</span></td>
                    <td>
                      <div class="price-cell">
                        <strong class="text-success">R$ {{ venda.valorTotal | number:'1.2-2' }}</strong>
                        <span class="text-xs text-muted" *ngIf="venda.dataDisponivelInfo">{{ venda.dataDisponivelInfo }}</span>
                      </div>
                    </td>
                    <td>
                      <span class="badge" [ngClass]="getStatusVendaBadge(venda.status)">
                        {{ getStatusVendaLabel(venda.status) }}
                      </span>
                    </td>
                    <td style="text-align: right;">
                      <div class="action-buttons-group">
                        <button 
                          *ngIf="venda.status !== 'PAGO'"
                          class="btn btn-success btn-xs" 
                          (click)="marcarVendaComoPaga(venda)"
                          title="Marcar como Pago"
                        >
                          <i class="bi bi-check-lg"></i>
                        </button>
                        <button class="btn btn-outline-danger btn-xs" (click)="excluirVenda(venda)" title="Excluir">
                          <i class="bi bi-trash"></i>
                        </button>
                      </div>
                    </td>
                  </tr>

                  <tr *ngIf="vendas().length === 0">
                    <td colspan="8" class="text-center py-4">
                      <p class="text-muted mb-0">Nenhuma venda encontrada para o filtro selecionado.</p>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- REGISTRO DE DESPESAS (Página 7 do PDF) -->
          <div class="card expenses-card">
            <div class="card-header-clean">
              <div>
                <h3>Registro de Despesas da Agência</h3>
                <p class="text-muted mb-0">Custos operacionais com equipamentos, locação, cachês e assinaturas</p>
              </div>
              <button class="btn btn-primary btn-sm" (click)="abrirModalDespesa()">
                <i class="bi bi-plus-lg"></i>
                <span>Lançar Nova Despesa</span>
              </button>
            </div>

            <div class="table-responsive">
              <table class="custom-table">
                <thead>
                  <tr>
                    <th>Data</th>
                    <th>Descrição</th>
                    <th>Categoria</th>
                    <th>Forma de Pagamento</th>
                    <th>Valor (R$)</th>
                    <th>Status</th>
                    <th style="text-align: right;">Ações</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let desp of despesas()">
                    <td class="text-muted">{{ desp.dataDespesa | date:'dd/MM/yyyy' }}</td>
                    <td><strong>{{ desp.descricao }}</strong></td>
                    <td><span class="badge badge-em-dev">{{ desp.categoria }}</span></td>
                    <td>{{ desp.formaPagamento || 'PIX' }}</td>
                    <td class="text-danger font-bold">- R$ {{ desp.valor | number:'1.2-2' }}</td>
                    <td>
                      <span class="badge" [ngClass]="desp.status === 'PAGO' ? 'badge-concluida' : 'badge-atrasada'">
                        {{ desp.status }}
                      </span>
                    </td>
                    <td style="text-align: right;">
                      <button class="btn btn-outline-danger btn-xs" (click)="excluirDespesa(desp)" title="Excluir">
                        <i class="bi bi-trash"></i>
                      </button>
                    </td>
                  </tr>

                  <tr *ngIf="despesas().length === 0">
                    <td colspan="7" class="text-center py-4">
                      <p class="text-muted mb-0">Nenhuma despesa lançada.</p>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- MODAL 1: LANÇAR DESPESA -->
    <div class="modal-backdrop" *ngIf="modalDespesaAberto()" (click)="fecharModalDespesa()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3>Lançar Nova Despesa</h3>
          <button class="btn-ghost btn-icon" (click)="fecharModalDespesa()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <form (ngSubmit)="salvarDespesa()">
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">DESCRIÇÃO DO CUSTO / DESPESA</label>
              <input type="text" class="form-control" [(ngModel)]="formDespesa.descricao" name="descricao" required placeholder="Ex: Locação de Lentes Sony G-Master" />
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">CATEGORIA</label>
                <select class="form-select" [(ngModel)]="formDespesa.categoria" name="categoria" required>
                  <option value="Equipamentos">Equipamentos & Lentes</option>
                  <option value="Locação">Locação de Espaço / Estúdio</option>
                  <option value="Transporte">Transporte & Combustível</option>
                  <option value="Alimentação">Alimentação no Set</option>
                  <option value="Equipe/Cachês">Equipe & Cachês Freelancer</option>
                  <option value="Software/Assinaturas">Software & Assinaturas (Adobe/Storage)</option>
                  <option value="Marketing">Marketing & Anúncios</option>
                  <option value="Outros">Outros</option>
                </select>
              </div>

              <div class="form-group">
                <label class="form-label">VALOR (R$)</label>
                <input type="number" step="0.01" class="form-control" [(ngModel)]="formDespesa.valor" name="valor" required placeholder="0.00" />
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">DATA DO GASTO</label>
                <input type="date" class="form-control" [(ngModel)]="formDespesa.dataDespesa" name="dataDespesa" required />
              </div>

              <div class="form-group">
                <label class="form-label">FORMA DE PAGAMENTO</label>
                <select class="form-select" [(ngModel)]="formDespesa.formaPagamento" name="formaPagamento">
                  <option value="PIX">PIX</option>
                  <option value="Cartão de Crédito">Cartão de Crédito</option>
                  <option value="Boleto">Boleto</option>
                  <option value="Transferência">Transferência</option>
                </select>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">OBSERVAÇÕES ADICIONAIS</label>
              <textarea class="form-control" rows="2" [(ngModel)]="formDespesa.observacoes" name="observacoes" placeholder="Ex: Referente à gravação da JM Fitness"></textarea>
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="fecharModalDespesa()">Cancelar</button>
            <button type="submit" class="btn btn-primary">Registrar Despesa</button>
          </div>
        </form>
      </div>
    </div>

    <!-- MODAL 2: LANÇAR NOVA VENDA -->
    <div class="modal-backdrop" *ngIf="modalNovaVendaAberto()" (click)="fecharModalNovaVenda()">
      <div class="modal-content" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <h3>Lançar Venda de Mídias / Fotos</h3>
          <button class="btn-ghost btn-icon" (click)="fecharModalNovaVenda()">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <form (ngSubmit)="salvarVenda()">
          <div class="modal-body">
            <div class="form-group">
              <label class="form-label">NOME DO CLIENTE / COMPRADOR</label>
              <input type="text" class="form-control" [(ngModel)]="formVenda.clienteNome" name="clienteNome" required placeholder="Ex: Adrianny Evelyn" />
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">QUANTIDADE DE FOTOS</label>
                <input type="number" class="form-control" [(ngModel)]="formVenda.qtdFotos" name="qtdFotos" required />
              </div>

              <div class="form-group">
                <label class="form-label">QUANTIDADE DE VÍDEOS</label>
                <input type="number" class="form-control" [(ngModel)]="formVenda.qtdVideos" name="qtdVideos" />
              </div>
            </div>

            <div class="form-row-2">
              <div class="form-group">
                <label class="form-label">VALOR TOTAL (R$)</label>
                <input type="number" step="0.50" class="form-control" [(ngModel)]="formVenda.valorTotal" name="valorTotal" required />
              </div>

              <div class="form-group">
                <label class="form-label">STATUS</label>
                <select class="form-select" [(ngModel)]="formVenda.status" name="status">
                  <option value="PAGO">Pago</option>
                  <option value="PENDENTE">Pendente / A Receber</option>
                  <option value="ATRASADO">Atrasado</option>
                </select>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">EVENTO RELACIONADO (OPCIONAL)</label>
              <input type="text" class="form-control" [(ngModel)]="formVenda.eventoNome" name="eventoNome" placeholder="Ex: BARRA RUN 2026" />
            </div>
          </div>

          <div class="modal-footer">
            <button type="button" class="btn btn-secondary" (click)="fecharModalNovaVenda()">Cancelar</button>
            <button type="submit" class="btn btn-primary">Registrar Venda</button>
          </div>
        </form>
      </div>
    </div>
  `,
  styles: [`
    .fluxo-caixa-card, .sales-master-card {
      border: 1px solid var(--border-color);
      width: 100%;
      max-width: 100%;
      min-width: 0;
      overflow: hidden;
      box-sizing: border-box;
    }

    .fluxo-header-row {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1.5rem;
      margin-bottom: 1.5rem;
      flex-wrap: wrap;
    }

    .chart-indicators-row {
      display: flex;
      gap: 1rem;
      margin-top: 0.5rem;
      font-size: 0.8rem;
      flex-wrap: wrap;
    }

    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.4rem;
      font-weight: 600;
    }

    .dot-entradas { width: 10px; height: 10px; border-radius: 2px; background: #E11D48; }
    .dot-saidas { width: 10px; height: 10px; border-radius: 2px; background: #F97316; }
    .dot-lucro { width: 10px; height: 10px; border-radius: 2px; background: #10B981; }

    .fluxo-actions {
      display: flex;
      gap: 0.75rem;
      flex-wrap: wrap;
    }

    .fluxo-chart-wrapper {
      height: 200px;
      width: 100%;
      max-width: 100%;
      display: flex;
      align-items: flex-end;
      padding-top: 1rem;
      border-bottom: 1px solid var(--border-color);
      padding-bottom: 0.75rem;
      margin-bottom: 1.5rem;
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
      box-sizing: border-box;
    }

    .fluxo-bars-container {
      width: 100%;
      min-width: 320px;
      max-width: 100%;
      height: 100%;
      display: flex;
      align-items: flex-end;
      justify-content: space-around;
      gap: 0.35rem;
    }

    .fluxo-month-group {
      flex: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
    }

    .tri-bar-box {
      width: 100%;
      max-width: 36px;
      height: 100%;
      display: flex;
      align-items: flex-end;
      justify-content: center;
      gap: 2px;
    }

    .bar-col-entry {
      width: 7px;
      background: #E11D48;
      border-top-left-radius: 2px;
      border-top-right-radius: 2px;
      transition: height 0.4s;
    }

    .bar-col-exit {
      width: 7px;
      background: #F97316;
      border-top-left-radius: 2px;
      border-top-right-radius: 2px;
      transition: height 0.4s;
    }

    .bar-col-profit {
      width: 7px;
      background: #10B981;
      border-top-left-radius: 2px;
      border-top-right-radius: 2px;
      transition: height 0.4s;
    }

    .month-lbl {
      font-size: 0.7rem;
      color: var(--text-muted);
      margin-top: 0.4rem;
    }

    .fluxo-summary-cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: 0.75rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .summary-mini-card {
      padding: 0.85rem 1rem;
      background: var(--bg-surface-elevated);
      border-radius: var(--radius-md);
      border: 1px solid var(--border-color);
      min-width: 0;
    }

    .summary-mini-card h3 {
      font-size: 1.25rem;
      margin: 0.2rem 0 0 0;
      word-break: break-word;
    }

    /* Sales Master Card */
    .sales-header-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1rem;
      margin-bottom: 1rem;
      flex-wrap: wrap;
    }

    .sales-filters-and-export {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      flex-wrap: wrap;
    }

    .segmented-control {
      display: flex;
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-md);
      padding: 3px;
      max-width: 100%;
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
    }

    .seg-btn {
      background: transparent;
      border: none;
      padding: 0.4rem 0.75rem;
      font-size: 0.775rem;
      font-weight: 700;
      color: var(--text-muted);
      border-radius: var(--radius-sm);
      cursor: pointer;
      transition: all 0.2s;
      white-space: nowrap;
    }

    .seg-btn.active {
      background: var(--bg-surface);
      color: var(--text-primary);
      box-shadow: var(--shadow-sm);
    }

    .client-cell {
      display: flex;
      flex-direction: column;
    }

    .price-cell {
      display: flex;
      flex-direction: column;
    }

    .table-responsive {
      width: 100%;
      max-width: 100%;
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
      display: block;
    }

    .custom-table {
      min-width: 850px;
    }

    .custom-table th, .custom-table td {
      white-space: nowrap;
    }

    .btn-xs {
      padding: 0.25rem 0.5rem;
      font-size: 0.75rem;
    }

    .form-row-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1rem;
    }

    @media (max-width: 900px) {
      .fluxo-header-row { flex-direction: column; gap: 0.85rem; }
      .fluxo-actions { width: 100%; flex-direction: column; }
      .fluxo-actions .btn { width: 100%; }
      .sales-header-row { flex-direction: column; align-items: flex-start; gap: 0.85rem; }
      .sales-filters-and-export { flex-direction: column; align-items: stretch; width: 100%; gap: 0.65rem; }
      .sales-filters-and-export .btn { width: 100%; }
      .segmented-control { width: 100%; }
      .seg-btn { flex: 1; text-align: center; }
      .fluxo-summary-cards { grid-template-columns: 1fr; }
      .form-row-2 { grid-template-columns: 1fr; }
    }
  `]
})
export class FinanceiroComponent implements OnInit {
  private apiService = inject(ApiService);

  vendas = signal<VendaFoto[]>([]);
  despesas = signal<Despesa[]>([]);
  fluxoCaixa = signal<FluxoCaixa | null>(null);

  filtroStatusVenda: string = 'TODOS';

  modalDespesaAberto = signal<boolean>(false);
  modalNovaVendaAberto = signal<boolean>(false);

  formDespesa: Partial<Despesa> = {};
  formVenda: Partial<VendaFoto> = {};

  ngOnInit(): void {
    this.carregarDados();
  }

  carregarDados(): void {
    this.carregarVendas();
    this.carregarDespesas();
    this.carregarFluxoCaixa();
  }

  carregarVendas(): void {
    this.apiService.getVendas(this.filtroStatusVenda).subscribe({
      next: (res) => this.vendas.set(res),
      error: (err) => console.error('Erro ao carregar vendas:', err)
    });
  }

  carregarDespesas(): void {
    this.apiService.getDespesas().subscribe({
      next: (res) => this.despesas.set(res),
      error: (err) => console.error('Erro ao carregar despesas:', err)
    });
  }

  carregarFluxoCaixa(): void {
    this.apiService.getFluxoCaixa().subscribe({
      next: (res) => this.fluxoCaixa.set(res),
      error: (err) => console.error('Erro ao carregar fluxo de caixa:', err)
    });
  }

  setFiltroVenda(status: string): void {
    this.filtroStatusVenda = status;
    this.carregarVendas();
  }

  getFluxoBarHeight(val: number): number {
    const max = 10000;
    return Math.min(100, Math.max(6, (val / max) * 100));
  }

  abrirModalDespesa(): void {
    this.formDespesa = {
      categoria: 'Equipamentos',
      formaPagamento: 'PIX',
      status: 'PAGO',
      dataDespesa: new Date().toISOString().split('T')[0]
    };
    this.modalDespesaAberto.set(true);
  }

  fecharModalDespesa(): void {
    this.modalDespesaAberto.set(false);
  }

  salvarDespesa(): void {
    this.apiService.createDespesa(this.formDespesa).subscribe({
      next: () => {
        this.fecharModalDespesa();
        this.carregarDados();
      }
    });
  }

  abrirModalNovaVenda(): void {
    this.formVenda = {
      qtdFotos: 1,
      qtdVideos: 0,
      valorTotal: 10.00,
      status: 'PAGO'
    };
    this.modalNovaVendaAberto.set(true);
  }

  fecharModalNovaVenda(): void {
    this.modalNovaVendaAberto.set(false);
  }

  salvarVenda(): void {
    this.apiService.createVenda(this.formVenda).subscribe({
      next: () => {
        this.fecharModalNovaVenda();
        this.carregarDados();
      }
    });
  }

  marcarVendaComoPaga(venda: VendaFoto): void {
    if (!venda.id) return;
    this.apiService.updateVendaStatus(venda.id, 'PAGO').subscribe({
      next: () => this.carregarVendas()
    });
  }

  excluirVenda(venda: VendaFoto): void {
    if (!venda.id || !confirm(`Excluir venda ${venda.codigoVenda}?`)) return;
    this.apiService.deleteVenda(venda.id).subscribe({
      next: () => this.carregarVendas()
    });
  }

  excluirDespesa(desp: Despesa): void {
    if (!desp.id || !confirm(`Excluir despesa "${desp.descricao}"?`)) return;
    this.apiService.deleteDespesa(desp.id).subscribe({
      next: () => this.carregarDados()
    });
  }

  exportarVendasCSV(): void {
    const list = this.vendas();
    let csv = 'ID;Data;Cliente;Fotos;Videos;Valor;Status\n';
    list.forEach(v => {
      csv += `"${v.codigoVenda}";"${v.dataVenda}";"${v.clienteNome}";${v.qtdFotos};${v.qtdVideos};"${v.valorTotal}";"${v.status}"\n`;
    });

    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = `vendas_designarte_${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
  }

  getStatusVendaBadge(status: string): string {
    switch (status) {
      case 'PAGO': return 'badge-concluida';
      case 'PENDENTE': return 'badge-em-desenvolvimento';
      case 'ATRASADO': return 'badge-atrasada';
      default: return '';
    }
  }

  getStatusVendaLabel(status: string): string {
    switch (status) {
      case 'PAGO': return 'Pago';
      case 'PENDENTE': return 'A Receber';
      case 'ATRASADO': return 'Atrasado';
      default: return status;
    }
  }
}
