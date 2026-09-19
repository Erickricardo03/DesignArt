import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { ApiService } from '../../core/services/api.service';
import { RelatorioMensalItem } from '../../core/models';

@Component({
  selector: 'app-relatorios',
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, HeaderComponent],
  template: `
    <div class="app-container">
      <app-sidebar class="no-print"></app-sidebar>

      <main class="main-content">
        <app-header 
          class="no-print"
          title="Relatórios Gerenciais Mensais" 
          subtitle="Relatório consolidado por loja das tarefas feitas, criadores, participantes e demandas"
          (refreshAction)="carregarRelatorio()"
        ></app-header>

        <div class="page-body">
          <!-- Card de Filtros e Opções de Impressão (no-print) -->
          <div class="card mb-4 no-print">
            <div class="report-filters-grid">
              <div class="form-group mb-0">
                <label class="form-label">FILTRAR POR LOJA / CLIENTE</label>
                <input 
                  type="text" 
                  class="form-control" 
                  placeholder="Todas as lojas ou nome específico..." 
                  [(ngModel)]="filtroLoja" 
                  (input)="carregarRelatorio()"
                />
              </div>

              <div class="form-group mb-0">
                <label class="form-label">MÊS</label>
                <select class="form-select" [(ngModel)]="filtroMes" (change)="carregarRelatorio()">
                  <option [value]="null">Todos os Meses</option>
                  <option [value]="1">Janeiro</option>
                  <option [value]="2">Fevereiro</option>
                  <option [value]="3">Março</option>
                  <option [value]="4">Abril</option>
                  <option [value]="5">Maio</option>
                  <option [value]="6">Junho</option>
                  <option [value]="7">Julho</option>
                  <option [value]="8">Agosto</option>
                  <option [value]="9">Setembro</option>
                  <option [value]="10">Outubro</option>
                  <option [value]="11">Novembro</option>
                  <option [value]="12">Dezembro</option>
                </select>
              </div>

              <div class="form-group mb-0">
                <label class="form-label">ANO</label>
                <select class="form-select" [(ngModel)]="filtroAno" (change)="carregarRelatorio()">
                  <option [value]="2026">2026</option>
                  <option [value]="2025">2025</option>
                </select>
              </div>

              <div class="report-actions-col">
                <button class="btn btn-primary" (click)="imprimirRelatorio()">
                  <i class="bi bi-printer-fill"></i>
                  <span>Imprimir Relatório</span>
                </button>
              </div>
            </div>
          </div>

          <!-- DOCUMENTO DO RELATÓRIO OFICIAL (Formatado para visualização e impressão) -->
          <div class="card printable-report-card">
            <!-- Cabeçalho do Relatório Oficial -->
            <div class="report-official-header">
              <div class="report-brand">
                <div class="logo-box">DA</div>
                <div>
                  <h1 class="report-brand-title">Design Arte</h1>
                  <span class="report-brand-sub">AGÊNCIA CRIATIVA & OPERACIONAL</span>
                </div>
              </div>

              <div class="report-title-box">
                <h2>RELATÓRIO MENSAL DE TAREFAS FEITAS POR LOJA</h2>
                <p>
                  Período: <strong>{{ getNomeMes(filtroMes) }} / {{ filtroAno }}</strong> | 
                  Loja: <strong>{{ filtroLoja || 'Todas as Lojas Atendidas' }}</strong>
                </p>
              </div>
            </div>

            <!-- TABELA DO RELATÓRIO OFICIAL (VISÍVEL NA TELA E FORMATADA PARA IMPRESSÃO) -->
            <div class="table-container-printable">
              <table class="report-table">
                <thead>
                  <tr>
                    <th class="col-loja">LOJA / CLIENTE</th>
                    <th class="col-demanda">DEMANDA FEITA (TÍTULO DA TAREFA)</th>
                    <th class="col-status" style="color: #ef4444; font-weight: 900;">STATUS</th>
                    <th class="col-criador">QUEM CRIOU A TAREFA</th>
                    <th class="col-participantes">PARTICIPANTES</th>
                    <th class="col-prazo">PRAZO</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let item of itensRelatorio()">
                    <td class="col-loja">
                      <strong class="store-name">{{ item.loja }}</strong>
                    </td>
                    <td class="col-demanda">
                      <span class="demand-title">{{ item.tituloDemanda }}</span>
                    </td>
                    <td class="col-status">
                      <span class="status-badge" [ngClass]="getStatusBadgeClass(item.status)">
                        {{ getStatusLabel(item.status) }}
                      </span>
                    </td>
                    <td class="col-criador">
                      <span class="creator-name">{{ item.criadorNome || '—' }}</span>
                    </td>
                    <td class="col-participantes">
                      <div class="participantes-list">
                        <span class="part-pill" *ngFor="let p of item.participantes">
                          {{ p }}
                        </span>
                        <span class="text-muted text-xs" *ngIf="!item.participantes?.length">-</span>
                      </div>
                    </td>
                    <td class="col-prazo">
                      <span class="prazo-text">{{ item.dataEntrega | date:'dd/MM/yyyy' }}</span>
                    </td>
                  </tr>

                  <tr *ngIf="itensRelatorio().length === 0">
                    <td colspan="6" class="text-center py-5">
                      <p class="text-muted mb-0">Nenhuma demanda registrada para os parâmetros selecionados.</p>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>

            <!-- Resumo e Assinatura do Relatório -->
            <div class="report-footer-section mt-5">
              <div class="report-stats-box">
                <div class="stat-bubble">
                  <span class="text-xs text-muted">TOTAL DE DEMANDAS</span>
                  <h3>{{ itensRelatorio().length }}</h3>
                </div>
                <div class="stat-bubble">
                  <span class="text-xs text-muted">DEMANDAS CONCLUÍDAS</span>
                  <h3 class="text-success">{{ countConcluidas() }}</h3>
                </div>
              </div>

              <div class="signature-box">
                <div class="sig-line"></div>
                <span class="font-bold">Diretoria Operacional & Audiovisual</span>
                <span class="text-xs text-muted">Design Arte • Agência Criativa & Operacional</span>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  `,
  styles: [`
    .report-filters-grid {
      display: grid;
      grid-template-columns: 2fr 1fr 1fr auto;
      gap: 1.25rem;
      align-items: flex-end;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .report-actions-col {
      display: flex;
      align-items: flex-end;
    }

    .printable-report-card {
      padding: 2.5rem;
      background: var(--bg-surface);
      border: 1px solid var(--border-color);
      width: 100%;
      max-width: 100%;
      min-width: 0;
      overflow: hidden;
      box-sizing: border-box;
    }

    .report-official-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
      padding-bottom: 1.5rem;
      border-bottom: 2px solid var(--border-color);
      flex-wrap: wrap;
    }

    .report-brand {
      display: flex;
      align-items: center;
      gap: 1rem;
    }

    .logo-box {
      width: 48px;
      height: 48px;
      border-radius: var(--radius-md);
      background: var(--color-primary-gradient);
      color: white;
      font-weight: 900;
      font-size: 1.3rem;
      display: flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
    }

    .report-brand-title {
      font-size: 1.4rem;
      margin: 0;
      line-height: 1.1;
    }

    .report-brand-sub {
      font-size: 0.7rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      color: var(--color-primary);
    }

    .report-title-box {
      text-align: right;
    }

    .report-title-box h2 {
      font-size: 1.05rem;
      margin: 0 0 0.25rem 0;
      letter-spacing: -0.01em;
      word-break: break-word;
    }

    .report-title-box p {
      font-size: 0.8rem;
      color: var(--text-muted);
      margin: 0;
      word-break: break-word;
    }

    .table-container-printable {
      width: 100%;
      max-width: 100%;
      overflow: hidden;
      box-sizing: border-box;
      margin-top: 1.5rem;
    }

    .report-table {
      width: 100%;
      table-layout: fixed;
      border-collapse: collapse;
      border: 1px solid var(--border-color);
      background: var(--bg-surface);
      margin: 0;
    }

    .report-table thead th {
      background: var(--bg-surface-elevated);
      color: var(--text-secondary);
      font-size: 0.75rem;
      font-weight: 800;
      letter-spacing: 0.05em;
      text-transform: uppercase;
      padding: 0.75rem 0.5rem;
      border-bottom: 2px solid var(--border-color);
      text-align: left;
      vertical-align: middle;
      box-sizing: border-box;
      word-break: break-word;
      overflow-wrap: break-word;
    }

    .report-table tbody td {
      padding: 0.7rem 0.5rem;
      border-bottom: 1px solid var(--border-color);
      vertical-align: middle;
      box-sizing: border-box;
      font-size: 0.82rem;
      word-wrap: break-word;
      overflow-wrap: break-word;
    }

    .col-loja {
      width: 22%;
    }

    .col-demanda {
      width: 28%;
    }

    .col-status {
      width: 14%;
      text-align: center;
    }

    .col-criador {
      width: 14%;
    }

    .col-participantes {
      width: 12%;
    }

    .col-prazo {
      width: 10%;
      text-align: center;
    }

    .store-name {
      font-weight: 800;
      color: var(--color-primary);
      display: block;
      line-height: 1.25;
    }

    .demand-title {
      font-weight: 700;
      color: var(--text-primary);
      display: block;
      line-height: 1.3;
    }

    .status-badge {
      display: inline-block;
      max-width: 100%;
      padding: 0.25rem 0.55rem;
      border-radius: 9999px;
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      white-space: normal;
      word-break: break-word;
      text-align: center;
      border: 1.5px solid currentColor;
    }

    .creator-name {
      font-weight: 600;
      color: var(--text-primary);
      font-size: 0.8rem;
    }

    .participantes-list {
      display: flex;
      flex-wrap: wrap;
      gap: 0.25rem;
    }

    .part-pill {
      font-size: 0.7rem;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      font-weight: 600;
      white-space: normal;
      word-break: break-word;
      max-width: 100%;
    }

    .prazo-text {
      font-size: 0.78rem;
      font-weight: 700;
      color: var(--text-muted);
    }

    .report-footer-section {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 1.5rem;
      padding-top: 1.5rem;
      border-top: 1px solid var(--border-color);
      flex-wrap: wrap;
    }

    .report-stats-box {
      display: flex;
      gap: 1.5rem;
      flex-wrap: wrap;
    }

    .stat-bubble h3 {
      font-size: 1.6rem;
      margin: 0;
    }

    .signature-box {
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      min-width: 200px;
    }

    .sig-line {
      width: 100%;
      height: 1px;
      background: var(--text-muted);
      margin-bottom: 0.5rem;
    }

    @media (max-width: 768px) {
      .report-filters-grid { grid-template-columns: 1fr; gap: 0.75rem; }
      .report-actions-col .btn { width: 100%; }
      .report-official-header { flex-direction: column; align-items: flex-start; text-align: left; gap: 0.85rem; }
      .report-title-box { text-align: left; }
      .report-footer-section { flex-direction: column; align-items: flex-start; }
      .printable-report-card { padding: 1rem 0.5rem !important; overflow: hidden !important; }
      .report-stats-box { width: 100%; justify-content: space-between; }
      .signature-box { width: 100%; margin-top: 1rem; }

      .report-table thead th {
        font-size: 0.62rem !important;
        padding: 0.5rem 0.25rem !important;
      }
      .report-table tbody td {
        font-size: 0.72rem !important;
        padding: 0.45rem 0.25rem !important;
      }
      .col-loja { width: 20% !important; }
      .col-demanda { width: 24% !important; }
      .col-status { width: 20% !important; }
      .col-criador { width: 18% !important; }
      .col-participantes { display: none !important; }
      .col-prazo { width: 18% !important; }
      .status-badge {
        padding: 0.15rem 0.35rem !important;
        font-size: 0.6rem !important;
        white-space: normal !important;
        line-height: 1.25 !important;
        text-align: center;
      }
    }

    @media print {
      .printable-report-card {
        padding: 0 !important;
        border: none !important;
        box-shadow: none !important;
        width: 100% !important;
        max-width: 100% !important;
        overflow: visible !important;
      }
      .table-container-printable {
        overflow: visible !important;
        width: 100% !important;
        max-width: 100% !important;
        margin-top: 1rem !important;
      }
      .report-table {
        width: 100% !important;
        table-layout: fixed !important;
        border: 1px solid #CBD5E1 !important;
        page-break-inside: auto !important;
      }
      .report-table thead th {
        background: #F1F5F9 !important;
        color: #0F172A !important;
        font-size: 7.5pt !important;
        padding: 4pt 3pt !important;
        border-bottom: 1.5pt solid #0F172A !important;
      }
      .report-table tbody tr {
        page-break-inside: avoid !important;
      }
      .report-table tbody td {
        font-size: 8pt !important;
        padding: 4.5pt 3pt !important;
        border-bottom: 0.5pt solid #E2E8F0 !important;
        color: #0F172A !important;
      }
      .col-loja { width: 22% !important; }
      .col-demanda { width: 28% !important; }
      .col-status { width: 14% !important; }
      .col-criador { width: 14% !important; }
      .col-participantes { width: 12% !important; display: table-cell !important; }
      .col-prazo { width: 10% !important; }
      .status-badge {
        border: 1pt solid currentColor !important;
        font-size: 7pt !important;
        padding: 1.5pt 4pt !important;
        -webkit-print-color-adjust: exact !important;
        print-color-adjust: exact !important;
      }
      .report-footer-section {
        page-break-inside: avoid !important;
        margin-top: 1.5rem !important;
        padding-top: 1rem !important;
        border-top: 1.5pt solid #CBD5E1 !important;
      }
    }
  `]
})
export class RelatoriosComponent implements OnInit {
  private apiService = inject(ApiService);

  itensRelatorio = signal<RelatorioMensalItem[]>([]);
  filtroLoja: string = '';
  filtroMes: number | null = null;
  filtroAno: number = 2026;

  ngOnInit(): void {
    this.carregarRelatorio();
  }

  carregarRelatorio(): void {
    this.apiService.getRelatorioMensal(
      this.filtroLoja,
      this.filtroMes || undefined,
      this.filtroAno
    ).subscribe({
      next: (res) => this.itensRelatorio.set(res),
      error: (err) => console.error('Erro ao gerar relatório:', err)
    });
  }

  imprimirRelatorio(): void {
    window.print();
  }

  countConcluidas(): number {
    return this.itensRelatorio().filter(i => i.status?.toUpperCase() === 'CONCLUIDA').length;
  }

  getNomeMes(mes: number | null): string {
    if (!mes) return 'Ano Consolidado';
    const nomes = ['', 'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'];
    return nomes[mes] || 'Mês';
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
}
