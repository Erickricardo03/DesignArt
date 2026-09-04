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

            <!-- Tabela Detalhada Conforme Requisito do PDF (Página 1) -->
            <!-- "cada relatório precisa mostra quem criou a tarefa, loja, e os percipientes e a demanda feita" -->
            <div class="table-responsive mt-4">
              <table class="custom-table report-table">
                <thead>
                  <tr>
                    <th>Loja / Cliente</th>
                    <th>Demanda Feita (Título da Tarefa)</th>
                    <th>Quem Criou a Tarefa</th>
                    <th>Participantes & Executores (Percipientes)</th>
                    <th>Prazo</th>
                    <th>Status</th>
                    <th>Progresso</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let item of itensRelatorio()">
                    <td>
                      <strong class="text-primary text-base">{{ item.loja }}</strong>
                    </td>
                    <td>
                      <span class="font-bold">{{ item.tituloDemanda }}</span>
                    </td>
                    <td>
                      <span class="creator-badge">{{ item.criadorNome || 'Lucas Matheus' }}</span>
                    </td>
                    <td>
                      <div class="participantes-list">
                        <span class="part-pill" *ngFor="let p of item.participantes">
                          {{ p }}
                        </span>
                        <span class="text-muted text-xs" *ngIf="!item.participantes?.length">-</span>
                      </div>
                    </td>
                    <td class="text-muted text-sm">
                      {{ item.dataEntrega | date:'dd/MM/yyyy' }}
                    </td>
                    <td>
                      <span class="badge" [ngClass]="getStatusBadgeClass(item.status)">
                        {{ getStatusLabel(item.status) }}
                      </span>
                    </td>
                    <td>
                      <div class="progress-cell">
                        <div class="progress-bar-container" style="width: 70px;">
                          <div class="progress-bar-fill" [style.width.%]="item.percentualConcluido || 0"></div>
                        </div>
                        <span class="text-xs font-bold">{{ item.percentualConcluido || 0 }}%</span>
                      </div>
                    </td>
                  </tr>

                  <tr *ngIf="itensRelatorio().length === 0">
                    <td colspan="7" class="text-center py-5">
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

    .table-responsive {
      width: 100%;
      max-width: 100%;
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
      display: block;
      box-sizing: border-box;
    }

    .report-table {
      min-width: 850px;
    }

    .report-table th, .report-table td {
      white-space: nowrap;
    }

    .creator-badge {
      font-weight: 700;
      font-size: 0.825rem;
      color: var(--text-secondary);
    }

    .participantes-list {
      display: flex;
      gap: 0.35rem;
      flex-wrap: wrap;
      max-width: 320px;
      white-space: normal;
    }

    .part-pill {
      font-size: 0.725rem;
      padding: 0.2rem 0.5rem;
      border-radius: var(--radius-sm);
      background: var(--bg-surface-elevated);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      white-space: nowrap;
    }

    .progress-cell {
      display: flex;
      align-items: center;
      gap: 0.5rem;
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

    @media (max-width: 900px) {
      .report-filters-grid { grid-template-columns: 1fr; gap: 0.75rem; }
      .report-actions-col .btn { width: 100%; }
      .report-official-header { flex-direction: column; align-items: flex-start; text-align: left; gap: 0.85rem; }
      .report-title-box { text-align: left; }
      .report-footer-section { flex-direction: column; align-items: flex-start; }
      .printable-report-card { padding: 1.25rem 0.85rem; }
      .report-stats-box { width: 100%; justify-content: space-between; }
      .signature-box { width: 100%; margin-top: 1rem; }
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
