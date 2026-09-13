import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { SidebarComponent } from '../../shared/components/sidebar.component';
import { HeaderComponent } from '../../shared/components/header.component';
import { ApiService } from '../../core/services/api.service';
import { DashboardStats, Tarefa } from '../../core/models';
import { CountUpDirective } from '../../shared/directives/count-up.directive';
import { RevealOnScrollDirective } from '../../shared/directives/reveal-on-scroll.directive';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, SidebarComponent, HeaderComponent, CountUpDirective, RevealOnScrollDirective],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>

      <main class="main-content">
        <app-header 
          title="Dashboard & Indicadores" 
          subtitle="Visão consolidada de produção, avisos de prazos e métricas operacionais"
          (refreshAction)="carregarDados()"
        ></app-header>

        <div class="page-body">
          <!-- Banner de Avisos (Tarefas Vencidas e Próximas - PDF Página 1) -->
          <div class="alerts-section" *ngIf="stats()?.avisos?.length">
            <div class="section-title-row">
              <div class="title-with-badge">
                <i class="bi bi-bell-fill text-warning"></i>
                <h3>Avisos Importantes de Prazos</h3>
                <span class="badge badge-prioridade-urgente">{{ stats()!.avisos.length }} alertas</span>
              </div>
              <span class="subtext">Tarefas próximas do vencimento e vencidas que exigem atenção</span>
            </div>

            <div class="alerts-grid stagger-grid">
              <div
                *ngFor="let aviso of stats()?.avisos"
                class="alert-card"
                [ngClass]="aviso.tipo === 'VENCIDA' ? 'alert-overdue' : 'alert-upcoming'"
              >
                <div class="alert-icon">
                  <i class="bi" [ngClass]="aviso.tipo === 'VENCIDA' ? 'bi-exclamation-triangle-fill' : 'bi-clock-history'"></i>
                </div>
                <div class="alert-info">
                  <div class="alert-header">
                    <span class="alert-tag">{{ aviso.tipo === 'VENCIDA' ? 'VENCIDA' : 'PRÓXIMA DO PRAZO' }}</span>
                    <span class="alert-store">{{ aviso.loja }}</span>
                  </div>
                  <h4 class="alert-title">{{ aviso.titulo }}</h4>
                  <p class="alert-msg">{{ aviso.mensagem }} (Entrega: {{ aviso.dataEntrega | date:'dd/MM/yyyy' }})</p>
                </div>
                <a [routerLink]="['/tarefas']" class="btn btn-secondary btn-sm">
                  Ver Tarefa
                </a>
              </div>
            </div>
          </div>

          <!-- Status das Tarefas - Badges Grandes (PDF Página 1) -->
          <div class="status-summary-grid stagger-grid">
            <div class="status-card bg-afazer" routerLink="/tarefas" [queryParams]="{status: 'A_FAZER'}">
              <div class="status-header">
                <span class="status-number" [appCountUp]="stats()?.aFazer || 0"></span>
                <i class="bi bi-hourglass-top"></i>
              </div>
              <div class="status-label">A FAZER</div>
            </div>

            <div class="status-card bg-em-dev" routerLink="/tarefas" [queryParams]="{status: 'EM_DESENVOLVIMENTO'}">
              <div class="status-header">
                <span class="status-number" [appCountUp]="stats()?.emDesenvolvimento || 0"></span>
                <i class="bi bi-code-slash"></i>
              </div>
              <div class="status-label">EM DESENVOLVIMENTO</div>
            </div>

            <div class="status-card bg-em-revisao" routerLink="/tarefas" [queryParams]="{status: 'EM_REVISAO'}">
              <div class="status-header">
                <span class="status-number" [appCountUp]="stats()?.emRevisaoOuNaoHomologada || 0"></span>
                <i class="bi bi-eye-fill"></i>
              </div>
              <div class="status-label">EM REVISÃO / NÃO HOMOLOGADA</div>
            </div>

            <div class="status-card bg-atrasadas" routerLink="/tarefas" [queryParams]="{status: 'ATRASADA'}">
              <div class="status-header">
                <span class="status-number" [appCountUp]="stats()?.atrasadas || 0"></span>
                <i class="bi bi-exclamation-octagon-fill"></i>
              </div>
              <div class="status-label">ATRASADAS</div>
            </div>

            <div class="status-card bg-concluidas" routerLink="/tarefas" [queryParams]="{status: 'CONCLUIDA'}">
              <div class="status-header">
                <span class="status-number" [appCountUp]="stats()?.concluidas || 0"></span>
                <i class="bi bi-check2-circle"></i>
              </div>
              <div class="status-label">CONCLUÍDAS</div>
            </div>
          </div>

          <!-- Linha de Gráficos e Ranking (Páginas 3 e 4 do PDF) -->
          <div class="charts-ranking-row">
            <!-- Gráfico de Produção / Atendimentos por Mês (Página 3) -->
            <div class="card chart-card" appReveal>
              <div class="card-header-clean">
                <div>
                  <span class="section-tag text-teal">PRODUÇÃO</span>
                  <h3>Atendimentos por Mês</h3>
                  <p class="text-muted">Visão consolidada de produção e demandas no ano</p>
                </div>
                <div class="chart-legend">
                  <span class="legend-dot bg-teal-dot"></span>
                  <span>Atendimentos</span>
                </div>
              </div>

              <!-- Barras do Gráfico em SVG Moderno -->
              <div class="custom-bar-chart">
                <div class="chart-bars-container">
                  <div class="chart-bar-col" *ngFor="let item of stats()?.producaoMensal">
                    <div class="bar-value-hover" *ngIf="item.atendimentos > 0">
                      {{ item.atendimentos }}
                    </div>
                    <div class="bar-track">
                      <div 
                        class="bar-fill-teal" 
                        [style.height.%]="getBarHeight(item.atendimentos)"
                      ></div>
                    </div>
                    <span class="bar-month-label">{{ item.mesAbreviado }}</span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Ranking de Colaboradores (Página 4 do PDF) -->
            <div class="card ranking-card" appReveal [appRevealDelay]="80">
              <div class="card-header-clean">
                <div>
                  <span class="section-tag text-success">EQUIPE & DESEMPENHO</span>
                  <h3>Top 10 Colaboradores</h3>
                  <p class="text-muted">Ranking de tarefas executadas para cálculo de gratificações</p>
                </div>
              </div>

              <div class="ranking-list">
                <div 
                  class="ranking-item" 
                  *ngFor="let colab of stats()?.rankingColaboradores; let i = index"
                >
                  <div class="ranking-position" [class.podium]="i < 3">
                    #{{ i + 1 }}
                  </div>
                  <div class="colab-info">
                    <div class="colab-name-row">
                      <span class="colab-name">{{ colab.nome }}</span>
                      <span class="colab-count">{{ colab.totalTarefasConcluidas }} tarefas</span>
                    </div>
                    <div class="ranking-bar-track">
                      <div 
                        class="ranking-bar-fill" 
                        [style.width.%]="getColabBarWidth(colab.totalTarefasConcluidas)"
                      ></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- Métricas Financeiras & Feed de Últimas Vendas (Página 5 do PDF) -->
          <div class="financial-section-row">
            <!-- Cards de Ganhos e Visitas -->
            <div class="finance-metrics-column">
              <div class="card finance-metric-card" appReveal>
                <div class="metric-icon-circle bg-green-light">
                  <i class="bi bi-currency-dollar text-success"></i>
                </div>
                <div>
                  <span class="metric-title">Ganhos no mês</span>
                  <h2 class="metric-value text-success">
                    R$ {{ (stats()?.ganhosNoMes || 202.50) | number:'1.2-2' }}
                  </h2>
                </div>
              </div>

              <div class="card finance-metric-card" appReveal [appRevealDelay]="60">
                <div class="metric-icon-circle bg-yellow-light">
                  <i class="bi bi-clock text-warning"></i>
                </div>
                <div>
                  <span class="metric-title">A receber</span>
                  <h2 class="metric-value text-warning">
                    R$ {{ (stats()?.aReceber || 202.50) | number:'1.2-2' }}
                  </h2>
                </div>
              </div>

              <div class="card finance-metric-card" appReveal [appRevealDelay]="120">
                <div class="metric-icon-circle bg-blue-light">
                  <i class="bi bi-eye-fill text-info"></i>
                </div>
                <div>
                  <span class="metric-title">Visitas na páginas</span>
                  <h2 class="metric-value">
                    <span [appCountUp]="stats()?.visitasNaPagina || 324"></span>
                  </h2>
                </div>
              </div>
            </div>

            <!-- Feed de Últimas Vendas (Mock Página 5) -->
            <div class="card recent-sales-card" appReveal [appRevealDelay]="80">
              <div class="card-header-clean">
                <h3>Últimas vendas</h3>
                <a routerLink="/financeiro" class="btn btn-ghost btn-sm text-primary">
                  Ver tudo &rarr;
                </a>
              </div>

              <div class="sales-list">
                <div class="sale-item" *ngFor="let venda of stats()?.ultimasVendas">
                  <div class="sale-client-info">
                    <h4>{{ venda.clienteNome }}</h4>
                    <span class="sale-media-count">{{ venda.qtdFotos }} mídias</span>
                  </div>
                  <div class="sale-price-info">
                    <span class="sale-value text-success">
                      R$ {{ venda.valorTotal | number:'1.2-2' }}
                    </span>
                    <span class="sale-date">
                      {{ venda.dataVenda | date:'dd/MM/yyyy às HH:mm' }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>
  `,
  styles: [`
    /* Alerts */
    .alerts-section {
      margin-bottom: 1.5rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .section-title-row {
      margin-bottom: 0.85rem;
    }

    .title-with-badge {
      display: flex;
      align-items: center;
      gap: 0.65rem;
      flex-wrap: wrap;
    }

    .title-with-badge h3 {
      font-size: 1.15rem;
      margin: 0;
      word-break: break-word;
    }

    .subtext {
      font-size: 0.8rem;
      color: var(--text-muted);
      display: block;
      margin-top: 0.2rem;
    }

    .alerts-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 0.85rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .alert-card {
      display: flex;
      align-items: center;
      gap: 0.85rem;
      padding: 0.85rem 1rem;
      border-radius: var(--radius-lg);
      border: 1px solid;
      background: var(--bg-surface);
      box-shadow: var(--shadow-sm);
      min-width: 0;
      max-width: 100%;
      box-sizing: border-box;
    }

    .alert-overdue {
      border-color: rgba(239, 68, 68, 0.4);
      background: rgba(239, 68, 68, 0.05);
    }

    .alert-upcoming {
      border-color: rgba(245, 158, 11, 0.4);
      background: rgba(245, 158, 11, 0.05);
    }

    .alert-icon {
      font-size: 1.5rem;
      flex-shrink: 0;
    }

    .alert-overdue .alert-icon { color: var(--color-danger); }
    .alert-upcoming .alert-icon { color: var(--color-warning); }

    .alert-info {
      flex: 1;
      min-width: 0;
    }

    .alert-header {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 0.2rem;
      flex-wrap: wrap;
    }

    .alert-tag {
      font-size: 0.65rem;
      font-weight: 800;
      letter-spacing: 0.05em;
    }

    .alert-overdue .alert-tag { color: var(--color-danger); }
    .alert-upcoming .alert-tag { color: var(--color-warning); }

    .alert-store {
      font-size: 0.725rem;
      font-weight: 700;
      color: var(--text-secondary);
    }

    .alert-title {
      font-size: 0.9rem;
      margin: 0 0 0.15rem 0;
      word-break: break-word;
      overflow-wrap: break-word;
    }

    .alert-msg {
      font-size: 0.775rem;
      color: var(--text-muted);
      margin: 0;
      word-break: break-word;
    }

    /* Status Grid */
    .status-summary-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
      gap: 0.75rem;
      margin-bottom: 1.5rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .status-card {
      padding: 1rem 0.85rem;
      border-radius: var(--radius-md);
      cursor: pointer;
      color: #FFFFFF;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      min-height: 95px;
      min-width: 0;
      max-width: 100%;
      box-sizing: border-box;
      overflow: hidden;
      transition: transform 0.2s, box-shadow 0.2s;
    }

    .status-card:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-md);
    }

    .status-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 0.35rem;
    }

    .status-number {
      font-size: 1.8rem;
      font-weight: 900;
      font-family: var(--font-display);
      line-height: 1;
    }

    .status-header i {
      font-size: 1.3rem;
      opacity: 0.8;
    }

    .status-label {
      font-size: 0.65rem;
      font-weight: 800;
      letter-spacing: 0.04em;
      opacity: 0.95;
      word-break: break-word;
      overflow-wrap: break-word;
      line-height: 1.25;
    }

    .bg-afazer { background: linear-gradient(135deg, #10B981, #059669); }
    .bg-em-dev { background: linear-gradient(135deg, #F97316, #EA580C); }
    .bg-em-revisao { background: linear-gradient(135deg, #06B6D4, #0891B2); }
    .bg-atrasadas { background: linear-gradient(135deg, #F43F5E, #E11D48); }
    .bg-concluidas { background: linear-gradient(135deg, #6366F1, #4F46E5); }

    /* Charts & Ranking */
    .charts-ranking-row {
      display: grid;
      grid-template-columns: 1.2fr 1fr;
      gap: 1.25rem;
      margin-bottom: 1.5rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .chart-card, .ranking-card, .recent-sales-card {
      width: 100%;
      max-width: 100%;
      min-width: 0;
      overflow: hidden;
      box-sizing: border-box;
    }

    .card-header-clean {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      margin-bottom: 1.25rem;
      gap: 0.5rem;
    }

    .card-header-clean h3 {
      font-size: 1.15rem;
      margin: 0;
      word-break: break-word;
    }

    .card-header-clean p {
      font-size: 0.775rem;
      margin: 0.15rem 0 0 0;
    }

    .section-tag {
      font-size: 0.685rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      display: block;
      margin-bottom: 0.2rem;
    }

    .chart-legend {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      font-size: 0.75rem;
      color: var(--text-secondary);
      flex-shrink: 0;
    }

    .bg-teal-dot {
      width: 8px;
      height: 8px;
      border-radius: 2px;
      background: var(--color-teal);
    }

    .custom-bar-chart {
      height: 240px;
      width: 100%;
      max-width: 100%;
      display: flex;
      align-items: flex-end;
      padding-top: 1.5rem;
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
      box-sizing: border-box;
    }

    .chart-bars-container {
      width: 100%;
      min-width: 360px;
      max-width: 100%;
      height: 100%;
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 0.35rem;
      border-bottom: 1px solid var(--border-color);
      padding-bottom: 0.5rem;
    }

    .chart-bar-col {
      flex: 1;
      height: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: flex-end;
      position: relative;
    }

    .bar-track {
      width: 100%;
      max-width: 24px;
      height: 100%;
      display: flex;
      align-items: flex-end;
      background: transparent;
    }

    .bar-fill-teal {
      width: 100%;
      background: #0D9488;
      border-top-left-radius: 4px;
      border-top-right-radius: 4px;
      transition: height 0.5s ease;
    }

    .bar-month-label {
      font-size: 0.7rem;
      color: var(--text-muted);
      margin-top: 0.4rem;
    }

    .bar-value-hover {
      font-size: 0.65rem;
      font-weight: 700;
      color: var(--text-primary);
      margin-bottom: 0.2rem;
    }

    /* Ranking List */
    .ranking-list {
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      max-height: 280px;
      overflow-y: auto;
      width: 100%;
    }

    .ranking-item {
      display: flex;
      align-items: center;
      gap: 0.75rem;
      width: 100%;
      min-width: 0;
    }

    .ranking-position {
      font-family: var(--font-display);
      font-size: 0.85rem;
      font-weight: 800;
      color: var(--text-muted);
      min-width: 26px;
      flex-shrink: 0;
    }

    .ranking-position.podium {
      color: var(--color-success);
    }

    .colab-info {
      flex: 1;
      min-width: 0;
    }

    .colab-name-row {
      display: flex;
      justify-content: space-between;
      gap: 0.5rem;
      font-size: 0.8rem;
      margin-bottom: 0.2rem;
    }

    .colab-name {
      font-weight: 700;
      color: var(--text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .colab-count {
      font-weight: 700;
      color: var(--color-success);
      flex-shrink: 0;
    }

    .ranking-bar-track {
      width: 100%;
      height: 5px;
      background: var(--bg-surface-elevated);
      border-radius: var(--radius-full);
      overflow: hidden;
    }

    .ranking-bar-fill {
      height: 100%;
      background: var(--color-success);
      border-radius: var(--radius-full);
      transition: width 0.5s ease;
    }

    /* Financial Row */
    .financial-section-row {
      display: grid;
      grid-template-columns: 1fr 1.5fr;
      gap: 1.25rem;
      width: 100%;
      max-width: 100%;
      min-width: 0;
    }

    .finance-metrics-column {
      display: flex;
      flex-direction: column;
      gap: 0.85rem;
      width: 100%;
      min-width: 0;
    }

    .finance-metric-card {
      display: flex;
      align-items: center;
      gap: 1rem;
      padding: 1rem 1.15rem;
      min-width: 0;
      max-width: 100%;
      box-sizing: border-box;
    }

    .metric-icon-circle {
      width: 42px;
      height: 42px;
      border-radius: var(--radius-full);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.35rem;
      flex-shrink: 0;
    }

    .bg-green-light { background: var(--color-success-light); }
    .bg-yellow-light { background: var(--color-warning-light); }
    .bg-blue-light { background: var(--color-info-light); }

    .metric-title {
      font-size: 0.725rem;
      font-weight: 600;
      color: var(--text-muted);
      text-transform: uppercase;
      letter-spacing: 0.04em;
      display: block;
    }

    .metric-value {
      font-size: 1.4rem;
      font-weight: 900;
      margin: 0;
      line-height: 1.15;
      word-break: break-word;
    }

    /* Recent Sales */
    .sales-list {
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      overflow-y: auto;
      max-height: 260px;
      width: 100%;
    }

    .sale-item {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 0.75rem;
      padding: 0.65rem 0.85rem;
      background: var(--bg-surface-elevated);
      border-radius: var(--radius-md);
      border: 1px solid var(--border-color);
      min-width: 0;
      max-width: 100%;
      box-sizing: border-box;
    }

    .sale-client-info {
      min-width: 0;
      flex: 1;
    }

    .sale-client-info h4 {
      font-size: 0.85rem;
      margin: 0 0 0.15rem 0;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    .sale-media-count {
      font-size: 0.7rem;
      color: var(--text-muted);
    }

    .sale-price-info {
      text-align: right;
      flex-shrink: 0;
    }

    .sale-value {
      font-size: 0.95rem;
      font-weight: 800;
      display: block;
    }

    .sale-date {
      font-size: 0.7rem;
      color: var(--text-muted);
    }

    @media (max-width: 1024px) {
      .charts-ranking-row, .financial-section-row {
        grid-template-columns: 1fr;
      }
    }

    @media (max-width: 768px) {
      .status-summary-grid {
        grid-template-columns: repeat(2, 1fr);
        gap: 0.65rem;
      }
      .status-card {
        min-height: 85px;
        padding: 0.85rem 0.75rem;
      }
      .status-number {
        font-size: 1.5rem;
      }
      .alerts-grid {
        grid-template-columns: 1fr;
      }
      .alert-card {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.65rem;
      }
      .alert-card .btn {
        width: 100%;
      }
    }

    @media (max-width: 480px) {
      .status-summary-grid {
        grid-template-columns: 1fr 1fr;
      }
      .status-card:last-child {
        grid-column: 1 / -1;
      }
      .finance-metric-card {
        padding: 0.85rem;
      }
      .metric-value {
        font-size: 1.25rem;
      }
    }
  `]
})
export class DashboardComponent implements OnInit {
  private apiService = inject(ApiService);
  stats = signal<DashboardStats | null>(null);

  ngOnInit(): void {
    this.carregarDados();
  }

  carregarDados(): void {
    this.apiService.getDashboardStats().subscribe({
      next: (res) => this.stats.set(res),
      error: (err) => console.error('Erro ao carregar dashboard stats:', err)
    });
  }

  getBarHeight(value: number): number {
    const max = 4000;
    return Math.min(100, Math.max(8, (value / max) * 100));
  }

  getColabBarWidth(tarefas: number): number {
    const max = 650;
    return Math.min(100, Math.max(10, (tarefas / max) * 100));
  }
}
