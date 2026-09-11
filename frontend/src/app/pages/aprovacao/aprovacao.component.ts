import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { ApiService } from '../../core/services/api.service';
import { Tarefa } from '../../core/models';

@Component({
  selector: 'app-aprovacao',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  template: `
    <div class="portal-container">
      <!-- Header do Portal de Aprovação (Exatamente como em 16742.jpg) -->
      <header class="portal-header">
        <div class="portal-header-content">
          <div class="portal-brand">
            <div class="da-brand-badge">
              <span class="da-logo-text">DA</span>
              <span class="da-brand-name">DesignArte</span>
            </div>
          </div>

          <div class="portal-tag">
            PORTAL DE APROVAÇÃO
          </div>
        </div>
      </header>

      <main class="portal-body" *ngIf="tarefa(); else loadingOrError">
        <!-- Card de Informações da Demanda (16742.jpg) -->
        <div class="projeto-info-card">
          <span class="cliente-tag">{{ tarefa()?.loja }}</span>
          <h1 class="projeto-title-main">{{ tarefa()?.titulo }}</h1>
          <p class="projeto-desc-text">{{ tarefa()?.descricao || tarefa()?.briefing || 'Sem descrição adicional cadastrada.' }}</p>
          
          <div class="aprovacao-current-status" [ngClass]="tarefa()?.statusAprovacao?.toLowerCase() || 'aguardando_cliente'">
            <i class="bi" [ngClass]="tarefa()?.statusAprovacao === 'APROVADO' ? 'bi-check-circle-fill' : 'bi-clock-history'"></i>
            <span>Status Atual: <strong>{{ tarefa()?.statusAprovacao === 'APROVADO' ? 'PROJETO APROVADO' : 'AGUARDANDO SUA APROVAÇÃO' }}</strong></span>
          </div>
        </div>

        <!-- Seção: ARQUIVOS FINAIS PARA APROVAÇÃO (Exatamente como em 16742.jpg) -->
        <div class="arquivos-aprovacao-wrapper">
          <div class="arquivos-header-bar">
            <div class="arquivos-title-box">
              <i class="bi bi-eye"></i>
              <h2>ARQUIVOS FINAIS PARA APROVAÇÃO</h2>
            </div>
            <span class="total-arquivos-badge">
              Total: {{ tarefa()?.arquivosFinais?.length || 0 }} arquivo(s)
            </span>
          </div>

          <!-- Lista de Arquivos com Botão Baixar Arquivo Original (16742.jpg) -->
          <div class="arquivos-feed-list" *ngIf="(tarefa()?.arquivosFinais?.length || 0) > 0; else semArquivos">
            <div class="arquivo-aprovacao-card" *ngFor="let arq of tarefa()?.arquivosFinais">
              <div class="arquivo-card-top-bar">
                <span class="arq-file-name">{{ arq.nome }}</span>
                <a [href]="arq.urlOuBase64" target="_blank" download [attr.download]="arq.nome" class="btn-baixar-original">
                  <i class="bi bi-download"></i> Baixar Arquivo Original
                </a>
              </div>

              <div class="arquivo-preview-container">
                <img *ngIf="arq.tipo !== 'VIDEO'" [src]="arq.urlOuBase64" [alt]="arq.nome" />
                <div *ngIf="arq.tipo === 'VIDEO'" class="video-preview-block">
                  <i class="bi bi-play-circle-fill"></i>
                  <p>Pré-visualização de Vídeo</p>
                </div>
              </div>
            </div>
          </div>

          <ng-template #semArquivos>
            <div class="empty-portal-box">
              <i class="bi bi-folder2-open"></i>
              <p>Nenhum arquivo final foi anexado para aprovação no momento.</p>
            </div>
          </ng-template>
        </div>

        <!-- Barra de Decisão / Ações do Cliente -->
        <div class="decisao-cliente-card" *ngIf="tarefa()?.statusAprovacao !== 'APROVADO'">
          <div class="decisao-text">
            <h3>Tudo certo com o material?</h3>
            <p>Revise os arquivos acima. Você pode aprovar a entrega ou solicitar ajustes à nossa equipe.</p>
          </div>
          <div class="decisao-botoes">
            <button class="btn-solicitar-ajustes" (click)="openAjustesModal()">
              <i class="bi bi-pencil-square"></i> Solicitar Alterações
            </button>
            <button class="btn-aprovar-projeto" (click)="aprovarProjeto()">
              <i class="bi bi-check2-circle"></i> APROVAR PROJETO
            </button>
          </div>
        </div>

        <div class="aprovado-sucesso-banner" *ngIf="tarefa()?.statusAprovacao === 'APROVADO'">
          <i class="bi bi-patch-check-fill"></i>
          <div>
            <h3>Projeto Aprovado!</h3>
            <p>A equipe da DesignArte já foi notificada da sua aprovação.</p>
          </div>
        </div>
      </main>

      <ng-template #loadingOrError>
        <div class="portal-loading">
          <i class="bi bi-hourglass-split"></i>
          <p>Carregando arquivos de aprovação...</p>
        </div>
      </ng-template>
    </div>

    <!-- Modal Solicitar Ajustes -->
    <div class="modal-backdrop" *ngIf="modalAjustesOpen">
      <div class="modal-card animate-fade-in">
        <div class="modal-header">
          <h3 class="modal-title">Descreva as alterações necessárias</h3>
          <button class="close-btn" (click)="modalAjustesOpen = false"><i class="bi bi-x-lg"></i></button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>O QUE PRECISA SER AJUSTADO?</label>
            <textarea 
              rows="4" 
              [(ngModel)]="feedbackAjuste" 
              class="form-control" 
              placeholder="Ex: Trocar o preço do produto X, mudar a cor do fundo, ajustar o texto do story..."
            ></textarea>
          </div>

          <div class="modal-actions-right">
            <button class="btn-cancel" (click)="modalAjustesOpen = false">Cancelar</button>
            <button class="btn-enviar-ajuste" (click)="enviarAjustes()">Enviar Ajustes</button>
          </div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .portal-container {
      min-height: 100vh;
      background: #f8fafc;
      font-family: 'Outfit', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }
    .portal-header {
      background: #ffffff;
      border-bottom: 1px solid #e2e8f0;
      padding: 1rem 2rem;
    }
    .portal-header-content {
      max-width: 1000px;
      margin: 0 auto;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .da-brand-badge {
      display: flex;
      align-items: center;
      gap: 0.6rem;
    }
    .da-logo-text {
      font-size: 1.4rem;
      font-weight: 900;
      color: #7c3aed;
      letter-spacing: -0.05em;
    }
    .da-brand-name {
      font-size: 1.15rem;
      font-weight: 800;
      color: #0f172a;
    }
    .portal-tag {
      font-size: 0.72rem;
      font-weight: 800;
      color: #64748b;
      letter-spacing: 0.08em;
      background: #f1f5f9;
      padding: 0.4rem 0.85rem;
      border-radius: 6px;
      border: 1px solid #e2e8f0;
    }

    .portal-body {
      max-width: 1000px;
      margin: 2rem auto;
      padding: 0 1rem 4rem 1rem;
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
    }

    /* Card Demanda 16742.jpg */
    .projeto-info-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 1.5rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
    }
    .cliente-tag {
      font-size: 0.75rem;
      font-weight: 800;
      color: #7c3aed;
      text-transform: uppercase;
      letter-spacing: 0.06em;
      display: block;
      margin-bottom: 0.25rem;
    }
    .projeto-title-main {
      font-size: 1.5rem;
      font-weight: 800;
      color: #0f172a;
      margin: 0 0 0.5rem 0;
    }
    .projeto-desc-text {
      font-size: 0.9rem;
      color: #475569;
      line-height: 1.5;
      margin: 0 0 1rem 0;
    }
    .aprovacao-current-status {
      display: inline-flex;
      align-items: center;
      gap: 0.45rem;
      font-size: 0.82rem;
      padding: 0.4rem 0.85rem;
      border-radius: 8px;
      font-weight: 600;
    }
    .aprovacao-current-status.aprovado {
      background: #dcfce7;
      color: #166534;
    }
    .aprovacao-current-status.aguardando_cliente {
      background: #fef3c7;
      color: #92400e;
    }

    /* Seção de Arquivos 16742.jpg */
    .arquivos-aprovacao-wrapper {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 1.5rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
    }
    .arquivos-header-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 1.5rem;
      padding-bottom: 0.85rem;
      border-bottom: 1px solid #f1f5f9;
    }
    .arquivos-title-box {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      color: #7c3aed;
    }
    .arquivos-title-box h2 {
      font-size: 0.92rem;
      font-weight: 800;
      color: #0f172a;
      margin: 0;
      letter-spacing: 0.04em;
    }
    .total-arquivos-badge {
      font-size: 0.78rem;
      color: #64748b;
      font-weight: 600;
    }

    /* Card Arquivo 16742.jpg com [Baixar Arquivo Original] */
    .arquivos-feed-list {
      display: flex;
      flex-direction: column;
      gap: 1.75rem;
    }
    .arquivo-aprovacao-card {
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      overflow: hidden;
      background: #ffffff;
    }
    .arquivo-card-top-bar {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.85rem 1.25rem;
      border-bottom: 1px solid #e2e8f0;
      background: #f8fafc;
    }
    .arq-file-name {
      font-size: 0.92rem;
      font-weight: 800;
      color: #0f172a;
    }
    .btn-baixar-original {
      border: 1px solid #cbd5e1;
      background: #ffffff;
      color: #0f172a;
      padding: 0.45rem 0.9rem;
      border-radius: 6px;
      font-size: 0.8rem;
      font-weight: 700;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      transition: all 0.2s;
    }
    .btn-baixar-original:hover {
      background: #f1f5f9;
      border-color: #94a3b8;
    }
    .arquivo-preview-container {
      background: #f1f5f9;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 1.5rem;
      min-height: 400px;
    }
    .arquivo-preview-container img {
      max-width: 100%;
      max-height: 700px;
      object-fit: contain;
      border-radius: 8px;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
    }
    .video-preview-block {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.5rem;
      color: #7c3aed;
      font-size: 3rem;
    }
    .video-preview-block p {
      font-size: 0.9rem;
      font-weight: 700;
      color: #64748b;
      margin: 0;
    }

    /* Decisão / Aprovação */
    .decisao-cliente-card {
      background: #ffffff;
      border: 1px solid #e2e8f0;
      border-radius: 12px;
      padding: 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 1.5rem;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
    }
    .decisao-text h3 {
      font-size: 1.05rem;
      font-weight: 800;
      color: #0f172a;
      margin: 0 0 0.25rem 0;
    }
    .decisao-text p {
      font-size: 0.85rem;
      color: #64748b;
      margin: 0;
    }
    .decisao-botoes {
      display: flex;
      gap: 0.85rem;
    }
    .btn-solicitar-ajustes {
      background: #ffffff;
      border: 1px solid #cbd5e1;
      color: #475569;
      padding: 0.75rem 1.25rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.88rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }
    .btn-aprovar-projeto {
      background: #10b981;
      color: #ffffff;
      border: none;
      padding: 0.75rem 1.5rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.92rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.45rem;
      box-shadow: 0 4px 14px rgba(16, 185, 129, 0.3);
    }
    .btn-aprovar-projeto:hover {
      background: #059669;
    }

    .aprovado-sucesso-banner {
      background: #dcfce7;
      border: 1px solid #86efac;
      border-radius: 12px;
      padding: 1.5rem;
      display: flex;
      align-items: center;
      gap: 1rem;
      color: #166534;
    }
    .aprovado-sucesso-banner i {
      font-size: 2.2rem;
    }
    .aprovado-sucesso-banner h3 {
      margin: 0 0 0.2rem 0;
      font-size: 1.15rem;
      font-weight: 800;
    }
    .aprovado-sucesso-banner p {
      margin: 0;
      font-size: 0.85rem;
    }

    .portal-loading {
      text-align: center;
      padding: 5rem 1rem;
      color: #64748b;
    }
    .portal-loading i {
      font-size: 2.5rem;
      color: #7c3aed;
      margin-bottom: 1rem;
      display: block;
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
      border: 1.5px solid var(--border-color);
      border-radius: 16px;
      width: 100%;
      max-width: 540px;
      box-shadow: 0 25px 60px -15px rgba(0,0,0,0.5);
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
      color: var(--text-primary);
      margin: 0;
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
    .btn-enviar-ajuste {
      background: var(--color-warning);
      color: #fff;
      border: none;
      padding: 0.7rem 1.5rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.88rem;
      cursor: pointer;
      box-shadow: 0 4px 14px rgba(217, 119, 6, 0.35);
      transition: all 0.2s;
    }
    .btn-enviar-ajuste:hover {
      box-shadow: 0 6px 20px rgba(217, 119, 6, 0.5);
      filter: brightness(1.06);
      transform: translateY(-1px);
    }
  `]
})
export class AprovacaoComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private api = inject(ApiService);

  tarefa = signal<Tarefa | null>(null);
  modalAjustesOpen = false;
  feedbackAjuste = '';

  ngOnInit(): void {
    this.route.params.subscribe((params) => {
      const idOrToken = params['id'] || params['token'] || '1';
      if (!isNaN(Number(idOrToken))) {
        this.api.getTarefaById(Number(idOrToken)).subscribe((res) => {
          this.tarefa.set(res);
        });
      } else {
        this.api.getTarefaByToken(idOrToken).subscribe((res) => {
          this.tarefa.set(res);
        });
      }
    });
  }

  aprovarProjeto(): void {
    const t = this.tarefa();
    if (!t) return;
    this.api.responderAprovacao(t.id!, 'APROVADO').subscribe((atualizada) => {
      this.tarefa.set(atualizada);
      alert('🎉 Parabéns! Projeto aprovado com sucesso.');
    });
  }

  openAjustesModal(): void {
    this.feedbackAjuste = '';
    this.modalAjustesOpen = true;
  }

  enviarAjustes(): void {
    const t = this.tarefa();
    if (!t || !this.feedbackAjuste.trim()) return;

    this.api.responderAprovacao(t.id!, 'SOLICITOU_AJUSTE', this.feedbackAjuste).subscribe((atualizada) => {
      this.tarefa.set(atualizada);
      this.modalAjustesOpen = false;
      alert('Solicitação de alterações enviada com sucesso para a equipe DesignArte.');
    });
  }
}
