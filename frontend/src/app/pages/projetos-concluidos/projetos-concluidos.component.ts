import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { Tarefa } from '../../core/models';
import { HeaderComponent } from '../../shared/components/header.component';
import { SidebarComponent } from '../../shared/components/sidebar.component';

@Component({
  selector: 'app-projetos-concluidos',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-header 
          title="Histórico de Projetos Concluídos" 
          subtitle="Entregas finalizadas, mídias geradas e status de aprovação de clientes"
          (refreshAction)="carregar()"
        ></app-header>

        <main class="page-body">
          <!-- Cabeçalho (Exatamente como em 16717.jpg) -->
          <div class="page-header-row">
            <div>
              <h1 class="page-title">Projetos Concluídos</h1>
              <span class="role-subtitle">NÍVEL OPERACIONAL: CEO / PROGRAMADOR</span>
            </div>
            <div class="agency-status-pill">
              <i class="bi bi-calendar3"></i> Agência DesignArte Operando
            </div>
          </div>

          <!-- Tabela de Projetos Concluídos (Exatamente como em 16717.jpg) -->
          <div class="card-table-wrapper">
            <div class="table-card-header">
              <h2 class="card-table-title">PROJETOS CONCLUÍDOS</h2>
              <div class="table-search-mini">
                <i class="bi bi-search"></i>
                <input 
                  type="text" 
                  placeholder="Filtrar por projeto ou cliente..." 
                  [(ngModel)]="filtro" 
                  (input)="filtrar()"
                />
              </div>
            </div>

            <div class="table-responsive">
              <table class="projetos-table">
                <thead>
                  <tr>
                    <th>MÍDIA FINAL</th>
                    <th>PROJETO / TAREFA</th>
                    <th>CLIENTE</th>
                    <th>APROVAÇÃO</th>
                    <th>CONCLUÍDO EM</th>
                    <th>QUEM FEZ</th>
                    <th>QUEM ATRIBUIU</th>
                  </tr>
                </thead>
                <tbody>
                  <tr *ngFor="let p of projetosFiltrados()" (click)="abrirDetalhes(p)" class="clickable-row">
                    <td>
                      <!-- Thumbnail Mídia Final (Igual ao print 16717.jpg) -->
                      <div class="media-thumb-wrap">
                        <ng-container *ngIf="isTipoVideo(p); else thumbImg">
                          <div class="video-badge-box">
                            <span>VÍDEO</span>
                          </div>
                        </ng-container>
                        <ng-template #thumbImg>
                          <img [src]="getThumbUrl(p)" alt="Mídia Final" />
                          <span class="thumb-count-tag" *ngIf="getMediaCount(p) > 0">
                            +{{ getMediaCount(p) }}
                          </span>
                        </ng-template>
                      </div>
                    </td>

                    <td>
                      <span class="projeto-title font-semibold">{{ p.titulo }}</span>
                    </td>

                    <td>
                      <span class="cliente-name-text">{{ p.loja }}</span>
                    </td>

                    <td>
                      <span 
                        class="aprovacao-badge" 
                        [ngClass]="p.statusAprovacao === 'APROVADO' ? 'aprovado' : 'aguardando'"
                      >
                        {{ p.statusAprovacao === 'APROVADO' ? 'APROVADO' : 'AGUARDANDO CLIENTE' }}
                      </span>
                    </td>

                    <td class="text-date">
                      {{ (p.dataConclusao || p.dataEntrega || '2026-08-31') | date:'dd/MM/yyyy' }}
                    </td>

                    <td class="text-team">
                      {{ (p.responsaveis && p.responsaveis.length > 0) ? p.responsaveis.join(', ') : (p.criadorNome || 'Igor Santos') }}
                    </td>

                    <td class="text-creator">
                      {{ p.quemAtribuiu || p.criadorNome || 'Igor Santos' }}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </main>
      </div>
    </div>

    <!-- Modal Detalhes do Projeto / Links de Entrega -->
    <div class="modal-backdrop" *ngIf="modalOpen()">
      <div class="modal-card modal-lg animate-fade-in" *ngIf="projetoSelecionado()">
        <div class="modal-header">
          <div>
            <span class="badge-subtitle">{{ projetoSelecionado()?.loja }}</span>
            <h2 class="modal-title">{{ projetoSelecionado()?.titulo }}</h2>
          </div>
          <button class="close-btn" (click)="modalOpen.set(false)"><i class="bi bi-x-lg"></i></button>
        </div>

        <div class="modal-body">
          <!-- Card de Aprovação Externa (16735.jpg) -->
          <div class="aprovacao-link-box">
            <div>
              <span class="aprv-lbl">SISTEMA DE APROVAÇÃO</span>
              <p class="aprv-title">Link de aprovação externa gerado</p>
              <span class="aprv-status">Estado: <strong>{{ projetoSelecionado()?.statusAprovacao || 'PENDING' }}</strong></span>
            </div>
            <button class="btn-copiar-link" (click)="copiarLinkAprovacao(projetoSelecionado()!)">
              <i class="bi bi-link-45deg"></i> {{ linkCopiado ? 'Link Copiado!' : 'Copiar Link' }}
            </button>
          </div>

          <!-- Galeria de Arquivos Finais da Demanda -->
          <div class="arquivos-finais-section">
            <h4 class="section-title-sm">Arquivos Finais Entregues ({{ (projetoSelecionado()?.arquivosFinais?.length || 0) }} arquivos)</h4>
            <div class="arquivos-grid" *ngIf="(projetoSelecionado()?.arquivosFinais?.length || 0) > 0; else semArquivos">
              <div class="arquivo-card" *ngFor="let arq of projetoSelecionado()?.arquivosFinais">
                <div class="arq-preview">
                  <img *ngIf="arq.tipo !== 'VIDEO'" [src]="arq.urlOuBase64" [alt]="arq.nome" />
                  <div *ngIf="arq.tipo === 'VIDEO'" class="video-preview-icon">
                    <i class="bi bi-play-circle-fill"></i>
                  </div>
                </div>
                <div class="arq-info">
                  <span class="arq-nome">{{ arq.nome }}</span>
                  <a [href]="arq.urlOuBase64" target="_blank" download class="btn-download-arq">
                    <i class="bi bi-download"></i> Baixar Original
                  </a>
                </div>
              </div>
            </div>
            <ng-template #semArquivos>
              <div class="empty-state-mini">
                <p>Nenhum arquivo anexado ainda a este projeto.</p>
              </div>
            </ng-template>
          </div>

          <div class="modal-actions-right">
            <button 
              class="btn-aprov-toggle"
              [class.btn-mark-approved]="projetoSelecionado()?.statusAprovacao !== 'APROVADO'"
              (click)="toggleAprovacao(projetoSelecionado()!)"
            >
              <i class="bi bi-check-circle"></i>
              {{ projetoSelecionado()?.statusAprovacao === 'APROVADO' ? 'Mudar para Aguardando Cliente' : 'Marcar como Aprovado pelo Cliente' }}
            </button>
            <button class="btn-cancel" (click)="modalOpen.set(false)">Fechar</button>
          </div>
        </div>
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
    .table-card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.25rem;
    }
    .card-table-title {
      font-size: 1.05rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0;
      letter-spacing: 0.04em;
    }
    .table-search-mini {
      position: relative;
      width: 260px;
    }
    .table-search-mini input {
      width: 100%;
      padding: 0.5rem 1.8rem 0.5rem 0.75rem;
      border: 1px solid var(--border-color);
      border-radius: 8px;
      background: var(--card-bg);
      color: var(--text-primary);
      font-size: 0.82rem;
      outline: none;
    }
    .table-search-mini i {
      position: absolute;
      right: 0.65rem;
      top: 50%;
      transform: translateY(-50%);
      color: var(--text-secondary);
    }

    /* Tabela Idêntica à imagem 16717.jpg */
    .projetos-table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.84rem;
    }
    .projetos-table th {
      padding: 0.85rem 0.75rem;
      color: var(--text-secondary);
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.05em;
      border-bottom: 1px solid var(--border-color);
    }
    .projetos-table td {
      padding: 0.85rem 0.75rem;
      border-bottom: 1px solid var(--border-color);
      color: var(--text-primary);
      vertical-align: middle;
    }
    .clickable-row {
      cursor: pointer;
      transition: background 0.15s;
    }
    .clickable-row:hover {
      background: rgba(124, 58, 237, 0.03);
    }

    /* Media Thumbnail */
    .media-thumb-wrap {
      width: 44px;
      height: 44px;
      border-radius: 8px;
      overflow: hidden;
      position: relative;
      background: #ef4444;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    .media-thumb-wrap img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .thumb-count-tag {
      position: absolute;
      bottom: 2px;
      right: 2px;
      background: rgba(0,0,0,0.75);
      color: #fff;
      font-size: 0.65rem;
      font-weight: 800;
      padding: 0.1rem 0.3rem;
      border-radius: 4px;
    }
    .video-badge-box {
      background: #7c3aed;
      color: #fff;
      width: 100%;
      height: 100%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 0.65rem;
      font-weight: 900;
      letter-spacing: 0.05em;
    }

    .projeto-title {
      font-weight: 700;
      color: var(--text-primary);
    }
    .cliente-name-text {
      color: var(--text-secondary);
      font-weight: 600;
    }

    /* Badges de Aprovação */
    .aprovacao-badge {
      font-size: 0.68rem;
      font-weight: 800;
      letter-spacing: 0.04em;
      padding: 0.25rem 0.65rem;
      border-radius: 6px;
      text-transform: uppercase;
      display: inline-block;
    }
    .aprovacao-badge.aguardando {
      background: #fef3c7;
      color: #b45309;
    }
    .aprovacao-badge.aprovado {
      background: #dcfce7;
      color: #15803d;
    }

    .text-date {
      color: var(--text-secondary);
      font-size: 0.8rem;
    }
    .text-team {
      font-size: 0.8rem;
      font-weight: 600;
    }
    .text-creator {
      font-size: 0.8rem;
      color: var(--text-secondary);
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
      max-width: 720px;
      max-height: 90vh;
      overflow-y: auto;
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
    .badge-subtitle {
      font-size: 0.75rem;
      font-weight: 800;
      color: var(--primary);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .modal-title {
      font-size: 1.35rem;
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

    /* Card de Aprovação Externa (16735.jpg) */
    .aprovacao-link-box {
      background: rgba(124, 58, 237, 0.05);
      border: 1px solid rgba(124, 58, 237, 0.2);
      border-radius: 12px;
      padding: 1.1rem 1.35rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1rem;
    }
    .aprv-lbl {
      font-size: 0.68rem;
      font-weight: 800;
      color: var(--primary);
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }
    .aprv-title {
      font-size: 0.95rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0.2rem 0;
    }
    .aprv-status {
      font-size: 0.78rem;
      color: var(--text-secondary);
    }
    .btn-copiar-link {
      background: #7c3aed;
      color: #fff;
      border: none;
      padding: 0.6rem 1.1rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.82rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.4rem;
      white-space: nowrap;
    }
    .btn-copiar-link:hover {
      background: #6d28d9;
    }

    .section-title-sm {
      font-size: 0.9rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 0.75rem 0;
    }
    .arquivos-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 1rem;
    }
    .arquivo-card {
      border: 1px solid var(--border-color);
      border-radius: 10px;
      overflow: hidden;
      background: var(--card-bg);
      display: flex;
      flex-direction: column;
    }
    .arq-preview {
      height: 120px;
      background: #0f172a;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: hidden;
    }
    .arq-preview img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .video-preview-icon {
      font-size: 2.5rem;
      color: #fff;
    }
    .arq-info {
      padding: 0.65rem;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .arq-nome {
      font-size: 0.75rem;
      font-weight: 700;
      color: var(--text-primary);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }
    .btn-download-arq {
      font-size: 0.72rem;
      font-weight: 700;
      color: var(--primary);
      text-decoration: none;
      display: flex;
      align-items: center;
      gap: 0.3rem;
    }

    .modal-actions-right {
      display: flex;
      justify-content: flex-end;
      gap: 0.75rem;
      margin-top: 0.5rem;
    }
    .btn-aprov-toggle {
      border: none;
      padding: 0.65rem 1.25rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.84rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }
    .btn-mark-approved {
      background: #10b981;
      color: #fff;
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
    .empty-state-mini {
      text-align: center;
      padding: 1.5rem;
      color: var(--text-secondary);
      font-size: 0.85rem;
    }
  `]
})
export class ProjetosConcluidosComponent implements OnInit {
  private api = inject(ApiService);

  projetos = signal<Tarefa[]>([]);
  projetosFiltrados = signal<Tarefa[]>([]);
  filtro = '';

  modalOpen = signal(false);
  projetoSelecionado = signal<Tarefa | null>(null);
  linkCopiado = false;

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.api.getTarefas().subscribe((todas) => {
      // Filtra tarefas concluídas ou que tenham entregas
      const concluidas = todas.filter(
        (t) => t.status === 'CONCLUIDA' || (t.arquivosFinais && t.arquivosFinais.length > 0)
      );
      this.projetos.set(concluidas);
      this.filtrar();
    });
  }

  filtrar(): void {
    const t = this.filtro.toLowerCase().trim();
    if (!t) {
      this.projetosFiltrados.set(this.projetos());
      return;
    }
    this.projetosFiltrados.set(
      this.projetos().filter(
        (p) =>
          p.titulo.toLowerCase().includes(t) ||
          p.loja.toLowerCase().includes(t) ||
          (p.criadorNome && p.criadorNome.toLowerCase().includes(t))
      )
    );
  }

  isTipoVideo(p: Tarefa): boolean {
    return p.titulo.toLowerCase().includes('video') || p.titulo.toLowerCase().includes('série');
  }

  getThumbUrl(p: Tarefa): string {
    if (p.arquivosFinais && p.arquivosFinais.length > 0) {
      return p.arquivosFinais[0].urlOuBase64;
    }
    return 'https://images.unsplash.com/photo-1578916171728-46686eac8d58?auto=format&fit=crop&w=150&q=80';
  }

  getMediaCount(p: Tarefa): number {
    if (p.arquivosFinais && p.arquivosFinais.length > 0) {
      return p.arquivosFinais.length;
    }
    return 8;
  }

  abrirDetalhes(p: Tarefa): void {
    this.projetoSelecionado.set(p);
    this.linkCopiado = false;
    this.modalOpen.set(true);
  }

  copiarLinkAprovacao(p: Tarefa): void {
    const url = `${window.location.origin}/aprovacao/${p.tokenAprovacao || p.id}`;
    if (navigator.clipboard) {
      navigator.clipboard.writeText(url);
    }
    this.linkCopiado = true;
    setTimeout(() => (this.linkCopiado = false), 2500);
  }

  toggleAprovacao(p: Tarefa): void {
    const novoStatus = p.statusAprovacao === 'APROVADO' ? 'AGUARDANDO_CLIENTE' : 'APROVADO';
    this.api.updateTarefa(p.id!, { statusAprovacao: novoStatus }).subscribe((atualizada) => {
      this.projetoSelecionado.set(atualizada);
      this.carregar();
    });
  }
}
