import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Tarefa } from '../../core/models';
import { HeaderComponent } from '../../shared/components/header.component';
import { SidebarComponent } from '../../shared/components/sidebar.component';

interface DiaCalendario {
  data: Date;
  diaNumero: number;
  mesAtual: boolean;
  tarefas: Tarefa[];
}

@Component({
  selector: 'app-calendario',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-header 
          title="Calendário de Entregas & Gravações" 
          subtitle="Planejamento mensal de prazos, gravações em campo e cronograma"
          (refreshAction)="carregarTarefas()"
        ></app-header>

        <main class="page-body">
          <!-- Barra Superior do Calendário (Exatamente como em 16707.jpg) -->
          <div class="cal-top-card">
            <div class="cal-title-box">
              <div class="cal-icon-wrap">
                <i class="bi bi-calendar-event"></i>
              </div>
              <div>
                <h1 class="cal-month-title">{{ getNomeMesAno() }}</h1>
                <span class="cal-subtitle">CALENDÁRIO DE ENTREGAS</span>
              </div>
            </div>

            <div class="cal-nav-buttons">
              <button class="btn-cal-nav" (click)="mudarMes(-1)"><i class="bi bi-chevron-left"></i></button>
              <button class="btn-cal-hoje" (click)="irParaHoje()">Hoje</button>
              <button class="btn-cal-nav" (click)="mudarMes(1)"><i class="bi bi-chevron-right"></i></button>
            </div>
          </div>

          <div class="cal-main-grid-layout">
            <!-- Grade do Calendário (Exatamente como em 16707.jpg) -->
            <div class="cal-grid-wrapper">
              <div class="cal-weekdays-header">
                <div>DOM</div>
                <div>SEG</div>
                <div>TER</div>
                <div>QUA</div>
                <div>QUI</div>
                <div>SEX</div>
                <div>SÁB</div>
              </div>

              <div class="cal-days-grid">
                <div 
                  class="cal-day-cell" 
                  *ngFor="let dia of diasMes()" 
                  [class.other-month]="!dia.mesAtual"
                  [class.selected-day]="isDiaSelecionado(dia.data)"
                  (click)="selecionarDia(dia)"
                >
                  <div class="day-number-row">
                    <span class="day-num-badge" [class.highlight-today]="isHoje(dia.data)">
                      {{ dia.diaNumero }}
                    </span>
                  </div>

                  <div class="day-tasks-list">
                    <div 
                      class="day-task-pill" 
                      *ngFor="let t of dia.tarefas.slice(0, 2)"
                      [ngClass]="getTaskClass(t)"
                      [title]="t.titulo + ' (' + t.loja + ')'"
                    >
                      {{ t.titulo }}
                    </div>
                    <span class="more-tasks-count" *ngIf="dia.tarefas.length > 2">
                      +{{ dia.tarefas.length - 2 }}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            <!-- Painel Lateral: Tarefas no Dia Selecionado + Legenda (16707.jpg) -->
            <div class="cal-side-panel">
              <div class="tarefas-dia-box">
                <div class="panel-header-row">
                  <i class="bi bi-list-check text-purple"></i>
                  <h3 class="panel-title">TAREFAS EM {{ getDataFormatadaPainel() }}.</h3>
                </div>

                <div class="tarefas-dia-content" *ngIf="tarefasDiaSelecionado().length > 0; else semTarefasDia">
                  <div class="tarefa-item-mini" *ngFor="let t of tarefasDiaSelecionado()">
                    <div class="item-mini-top">
                      <span class="item-mini-title">{{ t.titulo }}</span>
                      <span class="item-mini-status" [ngClass]="getTaskClass(t)">{{ t.status }}</span>
                    </div>
                    <span class="item-mini-loja">{{ t.loja }}</span>
                    <span class="item-mini-resp">{{ t.responsaveis.join(', ') }}</span>
                  </div>
                </div>

                <ng-template #semTarefasDia>
                  <div class="cal-empty-state">
                    <div class="empty-icon-box">
                      <i class="bi bi-calendar2"></i>
                    </div>
                    <p class="empty-text">Nenhuma entrega agendada.</p>
                    <button class="btn-create-task-cal" (click)="criarTarefaNoDia()">
                      <i class="bi bi-plus"></i> Criar uma tarefa
                    </button>
                  </div>
                </ng-template>
              </div>

              <!-- Legenda dos Status (Exatamente como em 16707.jpg) -->
              <div class="cal-legend-box">
                <h4 class="legend-title">LEGENDA DOS STATUS</h4>
                <div class="legend-grid">
                  <div class="legend-item">
                    <span class="dot a-fazer"></span>
                    <span>A Fazer</span>
                  </div>
                  <div class="legend-item">
                    <span class="dot em-andamento"></span>
                    <span>Em andamento</span>
                  </div>
                  <div class="legend-item">
                    <span class="dot concluido"></span>
                    <span>Concluido</span>
                  </div>
                  <div class="legend-item">
                    <span class="dot alteracoes"></span>
                    <span>Alteracoes</span>
                  </div>
                  <div class="legend-item">
                    <span class="dot cancelados"></span>
                    <span>Cancelados</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </main>
      </div>
    </div>
  `,
  styles: [`
    .page-body {
      padding: 1.5rem 2rem;
    }

    /* Top Card 16707.jpg */
    .cal-top-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.25rem 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
    }
    .cal-title-box {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .cal-icon-wrap {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: rgba(124, 58, 237, 0.1);
      color: var(--primary);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.25rem;
    }
    .cal-month-title {
      font-size: 1.25rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
      text-transform: capitalize;
    }
    .cal-subtitle {
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      color: var(--text-secondary);
    }
    .cal-nav-buttons {
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .btn-cal-nav {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 8px;
      width: 36px;
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--text-primary);
      cursor: pointer;
      font-size: 0.9rem;
    }
    .btn-cal-hoje {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 8px;
      padding: 0 1rem;
      height: 36px;
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text-primary);
      cursor: pointer;
    }

    /* Layout Principal */
    .cal-main-grid-layout {
      display: grid;
      grid-template-columns: 1fr 340px;
      gap: 1.5rem;
      align-items: start;
    }
    @media (max-width: 1024px) {
      .cal-main-grid-layout {
        grid-template-columns: 1fr;
      }
    }

    /* Grade de Dias 16707.jpg */
    .cal-grid-wrapper {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
    }
    .cal-weekdays-header {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      text-align: center;
      padding: 0.85rem 0;
      font-size: 0.75rem;
      font-weight: 800;
      letter-spacing: 0.05em;
      color: var(--text-secondary);
      border-bottom: 1px solid var(--border-color);
      background: rgba(0,0,0,0.02);
    }
    .cal-days-grid {
      display: grid;
      grid-template-columns: repeat(7, 1fr);
      grid-auto-rows: minmax(110px, 1fr);
      border-bottom: 1px solid var(--border-color);
      border-right: 1px solid var(--border-color);
    }
    .cal-day-cell {
      border-top: 1px solid var(--border-color);
      border-left: 1px solid var(--border-color);
      padding: 0.5rem;
      display: flex;
      flex-direction: column;
      cursor: pointer;
      transition: background 0.15s;
    }
    .cal-day-cell:hover {
      background: rgba(124, 58, 237, 0.03);
    }
    .cal-day-cell.other-month {
      opacity: 0.35;
      background: rgba(0,0,0,0.02);
    }
    .cal-day-cell.selected-day {
      border: 2px solid #7c3aed !important;
      background: rgba(124, 58, 237, 0.04);
    }
    .day-number-row {
      display: flex;
      justify-content: flex-start;
      margin-bottom: 0.35rem;
    }
    .day-num-badge {
      font-size: 0.82rem;
      font-weight: 700;
      color: var(--text-primary);
      width: 24px;
      height: 24px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
    }
    .highlight-today {
      background: #7c3aed;
      color: #fff;
    }
    .day-tasks-list {
      display: flex;
      flex-direction: column;
      gap: 0.25rem;
    }
    .day-task-pill {
      font-size: 0.68rem;
      font-weight: 600;
      padding: 0.2rem 0.4rem;
      border-radius: 4px;
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      border: 1px solid transparent;
    }
    .day-task-pill.concluido { background: #dcfce7; color: #166534; border-color: #86efac; }
    .day-task-pill.em-andamento { background: #e0f2fe; color: #0369a1; border-color: #7dd3fc; }
    .day-task-pill.a-fazer { background: #f1f5f9; color: #475569; border-color: #cbd5e1; }
    .day-task-pill.alteracoes { background: #fef3c7; color: #92400e; border-color: #fde68a; }
    .day-task-pill.cancelados { background: #fee2e2; color: #991b1b; border-color: #fca5a5; }

    .more-tasks-count {
      font-size: 0.65rem;
      font-weight: 800;
      color: var(--primary);
    }

    /* Painel Lateral 16707.jpg */
    .cal-side-panel {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }
    .tarefas-dia-box {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.5rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
      min-height: 240px;
    }
    .panel-header-row {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      margin-bottom: 1.25rem;
    }
    .text-purple { color: var(--primary); font-size: 1.1rem; }
    .panel-title {
      font-size: 0.88rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
      letter-spacing: 0.04em;
    }
    .cal-empty-state {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      padding: 2rem 0;
      text-align: center;
    }
    .empty-icon-box {
      font-size: 2.2rem;
      color: var(--text-secondary);
      opacity: 0.4;
      margin-bottom: 0.75rem;
    }
    .empty-text {
      font-size: 0.85rem;
      color: var(--text-secondary);
      margin: 0 0 1rem 0;
      font-weight: 500;
    }
    .btn-create-task-cal {
      background: none;
      border: none;
      color: #7c3aed;
      font-weight: 800;
      font-size: 0.85rem;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
    }

    .tarefas-dia-content {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
    }
    .tarefa-item-mini {
      background: rgba(124, 58, 237, 0.03);
      border: 1px solid var(--border-color);
      border-radius: 8px;
      padding: 0.75rem;
      display: flex;
      flex-direction: column;
      gap: 0.2rem;
    }
    .item-mini-top {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .item-mini-title {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text-primary);
    }
    .item-mini-status {
      font-size: 0.65rem;
      font-weight: 800;
      padding: 0.15rem 0.4rem;
      border-radius: 4px;
    }
    .item-mini-loja {
      font-size: 0.75rem;
      color: var(--primary);
      font-weight: 600;
    }
    .item-mini-resp {
      font-size: 0.7rem;
      color: var(--text-secondary);
    }

    /* Legenda 16707.jpg */
    .cal-legend-box {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.25rem 1.5rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
    }
    .legend-title {
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      color: var(--text-secondary);
      margin: 0 0 1rem 0;
    }
    .legend-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.75rem;
      font-size: 0.78rem;
      font-weight: 600;
      color: var(--text-primary);
    }
    .legend-item {
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }
    .dot {
      width: 10px;
      height: 10px;
      border-radius: 50%;
      display: inline-block;
      border: 1.5px solid;
    }
    .dot.a-fazer { border-color: #94a3b8; background: transparent; }
    .dot.em-andamento { border-color: #38bdf8; background: transparent; }
    .dot.concluido { border-color: #4ade80; background: transparent; }
    .dot.alteracoes { border-color: #facc15; background: transparent; }
    .dot.cancelados { border-color: #f87171; background: transparent; }
  `]
})
export class CalendarioComponent implements OnInit {
  private api = inject(ApiService);

  tarefas = signal<Tarefa[]>([]);
  dataAtual = signal<Date>(new Date(2026, 8, 10)); // Setembro 2026 como nos prints
  dataSelecionada = signal<Date>(new Date(2026, 8, 10));
  diasMes = signal<DiaCalendario[]>([]);

  ngOnInit(): void {
    this.carregarTarefas();
  }

  carregarTarefas(): void {
    this.api.getTarefas().subscribe((res) => {
      this.tarefas.set(res);
      this.gerarGradeCalendario();
    });
  }

  gerarGradeCalendario(): void {
    const atual = this.dataAtual();
    const ano = atual.getFullYear();
    const mes = atual.getMonth();

    const primeiroDiaMes = new Date(ano, mes, 1);
    const diaSemanaInicio = primeiroDiaMes.getDay(); // 0 = Domingo

    const dias: DiaCalendario[] = [];

    // Dias do mês anterior para preencher a primeira semana
    for (let i = diaSemanaInicio - 1; i >= 0; i--) {
      const d = new Date(ano, mes, -i);
      dias.push({
        data: d,
        diaNumero: d.getDate(),
        mesAtual: false,
        tarefas: this.getTarefasDoDia(d),
      });
    }

    // Dias do mês atual
    const ultimoDiaMes = new Date(ano, mes + 1, 0).getDate();
    for (let i = 1; i <= ultimoDiaMes; i++) {
      const d = new Date(ano, mes, i);
      dias.push({
        data: d,
        diaNumero: i,
        mesAtual: true,
        tarefas: this.getTarefasDoDia(d),
      });
    }

    // Dias do próximo mês para completar 35 ou 42 células
    const resto = 35 - dias.length;
    if (resto > 0) {
      for (let i = 1; i <= resto; i++) {
        const d = new Date(ano, mes + 1, i);
        dias.push({
          data: d,
          diaNumero: i,
          mesAtual: false,
          tarefas: this.getTarefasDoDia(d),
        });
      }
    }

    this.diasMes.set(dias);
  }

  getTarefasDoDia(d: Date): Tarefa[] {
    const ano = d.getFullYear();
    const mes = String(d.getMonth() + 1).padStart(2, '0');
    const dia = String(d.getDate()).padStart(2, '0');
    const dataStr = `${ano}-${mes}-${dia}`;

    return this.tarefas().filter(
      (t) => t.dataEntrega === dataStr || t.dataGravacao === dataStr
    );
  }

  isHoje(d: Date): boolean {
    return d.getDate() === 10 && d.getMonth() === 8 && d.getFullYear() === 2026;
  }

  isDiaSelecionado(d: Date): boolean {
    const sel = this.dataSelecionada();
    return (
      d.getDate() === sel.getDate() &&
      d.getMonth() === sel.getMonth() &&
      d.getFullYear() === sel.getFullYear()
    );
  }

  selecionarDia(dia: DiaCalendario): void {
    this.dataSelecionada.set(dia.data);
  }

  tarefasDiaSelecionado(): Tarefa[] {
    return this.getTarefasDoDia(this.dataSelecionada());
  }

  getNomeMesAno(): string {
    const meses = [
      'Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho',
      'Julho', 'Agosto', 'Setembro', 'Outubro', 'Novembro', 'Dezembro'
    ];
    const d = this.dataAtual();
    return `${meses[d.getMonth()]} de ${d.getFullYear()}`;
  }

  getDataFormatadaPainel(): string {
    const d = this.dataSelecionada();
    const mesesAbrev = ['JAN', 'FEV', 'MAR', 'ABR', 'MAI', 'JUN', 'JUL', 'AGO', 'SET', 'OUT', 'NOV', 'DEZ'];
    return `${d.getDate()} DE ${mesesAbrev[d.getMonth()]}`;
  }

  mudarMes(delta: number): void {
    const d = new Date(this.dataAtual());
    d.setMonth(d.getMonth() + delta);
    this.dataAtual.set(d);
    this.gerarGradeCalendario();
  }

  irParaHoje(): void {
    this.dataAtual.set(new Date(2026, 8, 10));
    this.dataSelecionada.set(new Date(2026, 8, 10));
    this.gerarGradeCalendario();
  }

  getTaskClass(t: Tarefa): string {
    switch (t.status) {
      case 'CONCLUIDA': return 'concluido';
      case 'EM_DESENVOLVIMENTO': return 'em-andamento';
      case 'EM_REVISAO': case 'NAO_HOMOLOGADA': return 'alteracoes';
      case 'CANCELADA': return 'cancelados';
      default: return 'a-fazer';
    }
  }

  criarTarefaNoDia(): void {
    alert(`Abrindo criação de tarefa para a data ${this.getDataFormatadaPainel()}...`);
  }
}
