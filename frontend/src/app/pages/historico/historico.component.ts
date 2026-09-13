import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AtividadeHistorico } from '../../core/models';
import { HeaderComponent } from '../../shared/components/header.component';
import { SidebarComponent } from '../../shared/components/sidebar.component';

@Component({
  selector: 'app-historico',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-header 
          title="Auditoria & Histórico de Atividades" 
          subtitle="Logs operacionais em tempo real de colaboradores e clientes"
          (refreshAction)="carregar()"
        ></app-header>

        <main class="page-body">
          <!-- Cabeçalho (Exatamente como em 16722.jpg) -->
          <div class="page-header-row">
            <div>
              <h1 class="page-title">Histórico</h1>
              <span class="role-subtitle">NÍVEL OPERACIONAL: CEO / PROGRAMADOR</span>
            </div>
            <div class="agency-status-pill">
              <i class="bi bi-calendar3"></i> Agência DesignArte Operando
            </div>
          </div>

          <!-- Tabela de Histórico (Exatamente como em 16722.jpg) -->
          <div class="card-table-wrapper">
            <div class="table-header-box">
              <h2 class="card-table-title">HISTÓRICO DE ATIVIDADES DO SISTEMA</h2>
              <div class="filter-search-wrap">
                <i class="bi bi-search"></i>
                <input 
                  type="text" 
                  placeholder="Buscar ação ou colaborador..." 
                  [(ngModel)]="filtro"
                  (input)="filtrar()"
                />
              </div>
            </div>

            <div class="table-responsive">
              <table class="historico-table">
                <thead>
                  <tr>
                    <th>DATA/HORA</th>
                    <th>COLABORADOR</th>
                    <th>AÇÃO REALIZADA</th>
                    <th>INFORMAÇÕES ADICIONAIS</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let item of historicoFiltrado()">
                    <td class="text-data">{{ item.dataHora }}</td>
                    <td class="text-colab">
                      <strong>{{ item.colaboradorNome }}</strong>
                      <span class="colab-email-muted">({{ item.colaboradorEmail }})</span>
                    </td>
                    <td>
                      <span class="action-badge-pill">
                        {{ item.acao }}
                      </span>
                    </td>
                    <td class="text-info-add">
                      {{ item.informacoesAdicionais }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </main>
      </div>
    </div>
  `,
  styles: [`
    .page-body {
      padding: 1.75rem 2rem;
    }
    .page-header-row {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.75rem;
    }
    .page-title {
      font-size: 1.55rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 0.2rem 0;
    }
    .role-subtitle {
      font-size: 0.75rem;
      font-weight: 800;
      color: var(--primary);
      letter-spacing: 0.05em;
    }
    .agency-status-pill {
      font-size: 0.8rem;
      font-weight: 700;
      background: rgba(124, 58, 237, 0.08);
      color: var(--primary);
      padding: 0.45rem 0.9rem;
      border-radius: 20px;
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }

    .card-table-wrapper {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 12px;
      padding: 1.5rem;
      box-shadow: 0 4px 16px rgba(0,0,0,0.03);
    }
    .table-header-box {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.25rem;
      gap: 0.75rem;
    }
    .card-table-title {
      font-size: 0.95rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
      letter-spacing: 0.04em;
    }
    .filter-search-wrap {
      position: relative;
      width: 280px;
      max-width: 100%;
      flex: 1 1 200px;
    }
    .filter-search-wrap input {
      width: 100%;
      padding: 0.5rem 1.8rem 0.5rem 0.75rem;
      border: 1px solid var(--border-color);
      border-radius: 8px;
      background: var(--card-bg);
      color: var(--text-primary);
      font-size: 0.82rem;
      outline: none;
    }
    .filter-search-wrap i {
      position: absolute;
      right: 0.65rem;
      top: 50%;
      transform: translateY(-50%);
      color: var(--text-secondary);
    }

    /* Tabela Idêntica à imagem 16722.jpg */
    .historico-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.82rem;
    }
    .historico-table th {
      padding: 0.85rem 0.75rem;
      color: var(--text-secondary);
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.05em;
      border-bottom: 1px solid var(--border-color);
    }
    .historico-table td {
      padding: 0.85rem 0.75rem;
      border-bottom: 1px solid var(--border-color);
      color: var(--text-primary);
      vertical-align: middle;
    }
    .text-data {
      font-size: 0.78rem;
      color: var(--text-secondary);
      white-space: nowrap;
    }
    .text-colab {
      font-size: 0.82rem;
    }
    .colab-email-muted {
      color: var(--text-secondary);
      font-size: 0.75rem;
      margin-left: 0.25rem;
    }
    .action-badge-pill {
      background: #ede9fe;
      color: #6d28d9;
      font-size: 0.65rem;
      font-weight: 800;
      letter-spacing: 0.04em;
      padding: 0.25rem 0.55rem;
      border-radius: 6px;
      text-transform: uppercase;
      white-space: nowrap;
      display: inline-block;
    }
    .text-info-add {
      font-size: 0.82rem;
      color: var(--text-primary);
    }
  `]
})
export class HistoricoComponent implements OnInit {
  private api = inject(ApiService);

  historico = signal<AtividadeHistorico[]>([]);
  historicoFiltrado = signal<AtividadeHistorico[]>([]);
  filtro = '';

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.getHistorico().subscribe((res) => {
      this.historico.set(res);
      this.filtrar();
    });
  }

  filtrar(): void {
    const t = this.filtro.toLowerCase().trim();
    if (!t) {
      this.historicoFiltrado.set(this.historico());
      return;
    }
    this.historicoFiltrado.set(
      this.historico().filter(
        (h) =>
          h.colaboradorNome.toLowerCase().includes(t) ||
          h.acao.toLowerCase().includes(t) ||
          h.informacoesAdicionais.toLowerCase().includes(t)
      )
    );
  }
}
