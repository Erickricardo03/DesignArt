import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { Municipio, ConfiguracaoLoja, FaixaDesconto, Avaliacao, User, UsuarioRequest, ModuloAcesso } from '../../core/models';
import { HeaderComponent } from '../../shared/components/header.component';
import { SidebarComponent } from '../../shared/components/sidebar.component';

@Component({
  selector: 'app-configuracoes',
  standalone: true,
  imports: [CommonModule, FormsModule, HeaderComponent, SidebarComponent],
  template: `
    <div class="app-container">
      <app-sidebar></app-sidebar>
      <div class="main-content">
        <app-header 
          title="Configurações & Parâmetros" 
          subtitle="Municípios atendidos, regras do e-commerce e preferências do sistema"
          (refreshAction)="carregar()"
        ></app-header>

        <main class="page-body">
          <div class="page-top-bar">
            <div>
              <span class="badge-subtitle">PREFERÊNCIAS DO SISTEMA</span>
              <h1 class="page-title">Configurações Gerais & Parâmetros</h1>
            </div>
          </div>

          <div class="config-grid-2">
            <!-- Card: Municípios Atendidos (16746.jpg) -->
            <div class="config-card">
              <div class="config-card-header">
                <div>
                  <h2 class="config-card-title"><i class="bi bi-geo-alt-fill text-purple"></i> Municípios de Atuação</h2>
                  <p class="config-card-subtitle">Cadastre os municípios onde a agência presta serviços de captação e design.</p>
                </div>
              </div>

              <div class="add-municipio-box">
                <div class="form-grid-muni">
                  <input 
                    type="text" 
                    [(ngModel)]="novoMunicipioNome" 
                    placeholder="Nome da cidade (ex: São Miguel dos Campos)" 
                    class="form-control"
                  />
                  <input 
                    type="text" 
                    [(ngModel)]="novoMunicipioUf" 
                    placeholder="UF (AL)" 
                    class="form-control uf-input"
                    maxlength="2"
                  />
                  <button class="btn-add-muni" (click)="adicionarMunicipio()">
                    <i class="bi bi-plus-lg"></i> Adicionar
                  </button>
                </div>
              </div>

              <div class="municipios-list stagger-grid">
                <div class="municipio-item hover-lift" *ngFor="let m of municipios()">
                  <div class="muni-info">
                    <i class="bi bi-pin-map-fill text-purple"></i>
                    <span class="muni-nome">{{ m.nome }}</span>
                    <span class="muni-uf">{{ m.uf }}</span>
                  </div>
                  <button class="btn-del-muni" (click)="removerMunicipio(m.id!)" title="Remover Município">
                    <i class="bi bi-trash"></i>
                  </button>
                </div>
              </div>
            </div>

            <!-- Card: Loja de Fotos - Retenção e Descontos (16716.jpg) -->
            <div class="config-card">
              <div class="config-card-header">
                <div>
                  <h2 class="config-card-title"><i class="bi bi-images text-purple"></i> Loja de Fotos — Retenção & Descontos</h2>
                  <p class="config-card-subtitle">Defina o tempo de retenção de fotos compradas e regras de desconto progressivo.</p>
                </div>
              </div>

              <div class="form-group mb-4">
                <label class="form-label-bold">Dias de retenção após exclusão (fotos já compradas)</label>
                <input type="number" [(ngModel)]="configLoja.diasRetencao" class="form-control" min="1" max="365" />
              </div>

              <div class="faixas-wrapper">
                <label class="form-label-bold">Faixas de desconto progressivo</label>
                
                <div class="faixas-chips-list">
                  <div class="faixa-chip" *ngFor="let f of configLoja.faixasDesconto; let i = index">
                    <span>A partir de <strong>{{ f.qtdMinima }} fotos</strong>: {{ f.percentualDesconto }}% OFF</span>
                    <button class="chip-remove" (click)="removerFaixa(i)"><i class="bi bi-x"></i></button>
                  </div>
                </div>

                <div class="add-faixa-form" *ngIf="addFaixaOpen">
                  <input type="number" [(ngModel)]="novaFaixa.qtdMinima" placeholder="Mínimo fotos" class="form-control" />
                  <input type="number" [(ngModel)]="novaFaixa.percentualDesconto" placeholder="% desconto" class="form-control" />
                  <button class="btn-save-sm" (click)="confirmarFaixa()">OK</button>
                  <button class="btn-cancel-sm" (click)="addFaixaOpen = false">Cancelar</button>
                </div>

                <button class="btn-text-add" *ngIf="!addFaixaOpen" (click)="addFaixaOpen = true">
                  <i class="bi bi-plus"></i> Adicionar
                </button>
              </div>

              <div class="save-config-btn-wrap">
                <button class="btn-save-global" (click)="salvarRegrasLoja()">
                  <i class="bi bi-check2"></i> Salvar Parâmetros da Loja
                </button>
              </div>
            </div>
          </div>

          <!-- Card: Avaliações de Clientes (Depoimentos exibidos na Landing Page) -->
          <div class="config-card avaliacoes-card">
            <div class="config-card-header">
              <div>
                <h2 class="config-card-title"><i class="bi bi-star-fill text-purple"></i> Avaliações de Clientes</h2>
                <p class="config-card-subtitle">Gerencie os depoimentos exibidos publicamente na página inicial do site.</p>
              </div>
              <button class="btn-add-muni" (click)="abrirNovaAvaliacao()" *ngIf="!avaliacaoFormOpen">
                <i class="bi bi-plus-lg"></i> Nova Avaliação
              </button>
            </div>

            <div class="avaliacao-form-box" *ngIf="avaliacaoFormOpen">
              <div class="form-grid-avaliacao">
                <input type="text" [(ngModel)]="avaliacaoForm.clienteNome" placeholder="Nome do cliente" class="form-control" />
                <input type="text" [(ngModel)]="avaliacaoForm.cargoEmpresa" placeholder="Cargo / Empresa (ex: CEO, Ateliê da Ysa)" class="form-control" />
              </div>
              <textarea [(ngModel)]="avaliacaoForm.texto" placeholder="Texto do depoimento..." class="form-control avaliacao-textarea" rows="3"></textarea>
              <div class="form-grid-avaliacao">
                <div class="nota-select-wrap">
                  <label class="form-label-bold">Nota</label>
                  <select [(ngModel)]="avaliacaoForm.nota" class="form-select">
                    <option [ngValue]="5">5 estrelas</option>
                    <option [ngValue]="4">4 estrelas</option>
                    <option [ngValue]="3">3 estrelas</option>
                    <option [ngValue]="2">2 estrelas</option>
                    <option [ngValue]="1">1 estrela</option>
                  </select>
                </div>
                <label class="ativo-check">
                  <input type="checkbox" [(ngModel)]="avaliacaoForm.ativo" />
                  Exibir publicamente no site
                </label>
              </div>
              <div class="avaliacao-form-actions">
                <button class="btn-save-sm" (click)="salvarAvaliacao()">
                  <i class="bi bi-check2"></i> {{ avaliacaoEditandoId ? 'Salvar Alterações' : 'Cadastrar Avaliação' }}
                </button>
                <button class="btn-cancel-sm" (click)="cancelarAvaliacao()">Cancelar</button>
              </div>
            </div>

            <div class="avaliacoes-list stagger-grid">
              <div class="avaliacao-item hover-lift" *ngFor="let a of avaliacoes()">
                <div class="avaliacao-item-header">
                  <div>
                    <span class="muni-nome">{{ a.clienteNome }}</span>
                    <span class="avaliacao-cargo" *ngIf="a.cargoEmpresa">{{ a.cargoEmpresa }}</span>
                  </div>
                  <span class="avaliacao-status" [class.inativo]="!a.ativo">{{ a.ativo ? 'VISÍVEL' : 'OCULTA' }}</span>
                </div>
                <div class="stars-row-config">
                  <i class="bi bi-star-fill" *ngFor="let s of contarEstrelas(a.nota)"></i>
                </div>
                <p class="avaliacao-texto-preview">{{ a.texto }}</p>
                <div class="avaliacao-item-actions">
                  <button class="btn-icon-edit" (click)="editarAvaliacao(a)" title="Editar"><i class="bi bi-pencil"></i></button>
                  <button class="btn-del-muni" (click)="removerAvaliacao(a.id!)" title="Excluir"><i class="bi bi-trash"></i></button>
                </div>
              </div>

              <p class="empty-hint" *ngIf="!avaliacoes().length">Nenhuma avaliação cadastrada ainda.</p>
            </div>
          </div>

          <!-- Card: Gestão de Usuários & Permissões (somente ADMIN) -->
          <div class="config-card usuarios-card" *ngIf="isAdmin()">
            <div class="config-card-header">
              <div>
                <h2 class="config-card-title"><i class="bi bi-person-lock text-purple"></i> Usuários & Permissões de Acesso</h2>
                <p class="config-card-subtitle">Cadastre os acessos dos seus funcionários e defina exatamente quais áreas do sistema cada um pode ver. Todo login segue o padrão <strong>usuario&#64;nexusdevelopment.tech</strong>.</p>
              </div>
              <button class="btn-add-muni" (click)="abrirNovoUsuario()" *ngIf="!usuarioFormOpen">
                <i class="bi bi-person-plus"></i> Novo Usuário
              </button>
            </div>

            <div class="usuario-form-box" *ngIf="usuarioFormOpen">
              <div class="form-grid-avaliacao">
                <input type="text" [(ngModel)]="usuarioForm.nomeCompleto" placeholder="Nome completo do funcionário" class="form-control" />
                <input type="text" [(ngModel)]="usuarioForm.cargo" placeholder="Cargo (ex: Social Media)" class="form-control" />
              </div>
              <div class="form-grid-avaliacao">
                <div>
                  <input
                    type="text"
                    [(ngModel)]="usuarioForm.username"
                    placeholder="Nome de acesso (ex: joao.silva)"
                    class="form-control"
                    [disabled]="!!usuarioEditandoId"
                  />
                  <span class="email-preview" *ngIf="usuarioForm.username">
                    <i class="bi bi-envelope-check"></i> {{ emailGerado() }}
                  </span>
                </div>
                <input
                  type="password"
                  [(ngModel)]="usuarioForm.password"
                  [placeholder]="usuarioEditandoId ? 'Nova senha (deixe em branco para manter)' : 'Senha de acesso'"
                  class="form-control"
                />
              </div>

              <div class="permissoes-box">
                <label class="form-label-bold">Nível de acesso</label>
                <div class="role-select-row">
                  <label class="role-radio">
                    <input type="radio" name="role" value="COLABORADOR" [(ngModel)]="usuarioForm.role" />
                    Colaborador (acesso restrito, definido abaixo)
                  </label>
                  <label class="role-radio">
                    <input type="radio" name="role" value="ADMIN" [(ngModel)]="usuarioForm.role" />
                    Administrador (acesso total ao sistema)
                  </label>
                </div>

                <div class="permissoes-checks" *ngIf="usuarioForm.role === 'COLABORADOR'">
                  <label class="form-label-bold">Módulos liberados para este colaborador</label>
                  <label class="perm-check">
                    <input type="checkbox" [checked]="temPermissao('FINANCEIRO')" (change)="togglePermissao('FINANCEIRO')" />
                    Financeiro (despesas, faturas e fluxo de caixa)
                  </label>
                  <label class="perm-check">
                    <input type="checkbox" [checked]="temPermissao('EQUIPE')" (change)="togglePermissao('EQUIPE')" />
                    Equipe (dados e salários da equipe)
                  </label>
                  <label class="perm-check">
                    <input type="checkbox" [checked]="temPermissao('CONFIGURACOES')" (change)="togglePermissao('CONFIGURACOES')" />
                    Configurações (municípios, loja de fotos e avaliações)
                  </label>
                </div>
              </div>

              <div class="avaliacao-form-actions">
                <button class="btn-save-sm" (click)="salvarUsuario()">
                  <i class="bi bi-check2"></i> {{ usuarioEditandoId ? 'Salvar Alterações' : 'Cadastrar Usuário' }}
                </button>
                <button class="btn-cancel-sm" (click)="cancelarUsuario()">Cancelar</button>
              </div>
            </div>

            <div class="usuarios-list stagger-grid">
              <div class="usuario-item hover-lift" *ngFor="let u of usuarios()">
                <div class="avaliacao-item-header">
                  <div>
                    <span class="muni-nome">{{ u.nomeCompleto }}</span>
                    <span class="avaliacao-cargo">{{ u.cargo || 'Sem cargo definido' }} &middot; {{ u.email }}</span>
                  </div>
                  <span class="avaliacao-status" [class.inativo]="u.role !== 'ADMIN' && !u.permissoes?.length">
                    {{ u.role === 'ADMIN' ? 'ADMIN' : 'COLABORADOR' }}
                  </span>
                </div>
                <div class="permissoes-chips" *ngIf="u.role !== 'ADMIN'">
                  <span class="perm-chip" *ngFor="let p of u.permissoes">{{ p }}</span>
                  <span class="perm-chip perm-chip-empty" *ngIf="!u.permissoes?.length">Sem acesso a módulos restritos</span>
                </div>
                <div class="avaliacao-item-actions">
                  <button class="btn-icon-edit" (click)="editarUsuario(u)" title="Editar"><i class="bi bi-pencil"></i></button>
                  <button class="btn-del-muni" (click)="removerUsuario(u)" title="Excluir"><i class="bi bi-trash"></i></button>
                </div>
              </div>

              <p class="empty-hint" *ngIf="!usuarios().length">Nenhum usuário cadastrado ainda.</p>
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
    .page-top-bar {
      margin-bottom: 1.75rem;
    }
    .badge-subtitle {
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.08em;
      color: var(--primary);
      text-transform: uppercase;
    }
    .page-title {
      font-size: 1.45rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0.2rem 0 0 0;
    }

    .config-grid-2 {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 1.75rem;
    }
    @media (max-width: 960px) {
      .config-grid-2 {
        grid-template-columns: 1fr;
      }
    }

    .config-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.5rem;
      box-shadow: 0 4px 14px rgba(0,0,0,0.03);
      display: flex;
      flex-direction: column;
      gap: 1.25rem;
    }
    .config-card-header {
      padding-bottom: 0.75rem;
      border-bottom: 1px solid var(--border-color);
    }
    .config-card-title {
      font-size: 1.1rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0 0 0.25rem 0;
      display: flex;
      align-items: center;
      gap: 0.5rem;
    }
    .text-purple { color: var(--primary); }
    .config-card-subtitle {
      font-size: 0.8rem;
      color: var(--text-secondary);
      margin: 0;
    }

    .form-grid-muni {
      display: grid;
      grid-template-columns: 1fr 70px auto;
      gap: 0.65rem;
    }
    .form-control {
      padding: 0.65rem 0.85rem;
      border: 1px solid var(--border-color);
      border-radius: 8px;
      background: var(--card-bg);
      color: var(--text-primary);
      font-size: 0.88rem;
      outline: none;
    }
    .uf-input {
      text-transform: uppercase;
      text-align: center;
    }
    .btn-add-muni {
      background: #1e293b;
      color: #fff;
      border: none;
      padding: 0 1rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.82rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.35rem;
    }

    .municipios-list {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      max-height: 280px;
      overflow-y: auto;
    }
    .municipio-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0.65rem 0.85rem;
      background: rgba(124, 58, 237, 0.03);
      border: 1px solid var(--border-color);
      border-radius: 8px;
    }
    .muni-info {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.85rem;
    }
    .muni-nome {
      font-weight: 700;
      color: var(--text-primary);
    }
    .muni-uf {
      font-size: 0.72rem;
      font-weight: 800;
      background: var(--primary);
      color: #fff;
      padding: 0.1rem 0.4rem;
      border-radius: 4px;
    }
    .btn-del-muni {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      font-size: 0.95rem;
    }

    .form-label-bold {
      font-size: 0.85rem;
      font-weight: 700;
      color: var(--text-primary);
      display: block;
      margin-bottom: 0.45rem;
    }
    .mb-4 { margin-bottom: 1rem; }

    .faixas-chips-list {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }
    .faixa-chip {
      background: rgba(124, 58, 237, 0.08);
      border: 1px solid rgba(124, 58, 237, 0.2);
      border-radius: 8px;
      padding: 0.35rem 0.7rem;
      font-size: 0.8rem;
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }
    .chip-remove {
      background: none;
      border: none;
      color: #ef4444;
      cursor: pointer;
      font-size: 1rem;
      padding: 0;
      line-height: 1;
    }
    .add-faixa-form {
      display: grid;
      grid-template-columns: 1fr 1fr auto auto;
      gap: 0.5rem;
      margin-bottom: 0.75rem;
    }
    .btn-save-sm {
      background: var(--primary);
      color: #fff;
      border: none;
      padding: 0 0.85rem;
      border-radius: 6px;
      font-weight: 700;
      cursor: pointer;
    }
    .btn-cancel-sm {
      background: none;
      border: 1px solid var(--border-color);
      color: var(--text-secondary);
      padding: 0 0.65rem;
      border-radius: 6px;
      cursor: pointer;
    }
    .btn-text-add {
      background: none;
      border: none;
      color: #7c3aed;
      font-size: 0.85rem;
      font-weight: 800;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      padding: 0.35rem 0;
    }

    .save-config-btn-wrap {
      margin-top: auto;
      padding-top: 1rem;
      border-top: 1px solid var(--border-color);
      display: flex;
      justify-content: flex-end;
    }
    .btn-save-global {
      background: var(--primary);
      color: #fff;
      border: none;
      padding: 0.65rem 1.35rem;
      border-radius: 8px;
      font-weight: 700;
      font-size: 0.85rem;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 0.45rem;
    }

    /* Avaliações de Clientes */
    .avaliacoes-card .config-card-header {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: 1rem;
      flex-wrap: wrap;
    }
    .avaliacao-form-box {
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      background: rgba(124, 58, 237, 0.04);
      border: 1px solid var(--border-color);
      border-radius: 10px;
      padding: 1rem;
    }
    .form-grid-avaliacao {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 0.65rem;
    }
    @media (max-width: 560px) {
      .form-grid-avaliacao { grid-template-columns: 1fr; }
    }
    .avaliacao-textarea {
      resize: vertical;
      font-family: inherit;
    }
    .nota-select-wrap {
      display: flex;
      flex-direction: column;
    }
    .ativo-check {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.85rem;
      font-weight: 600;
      color: var(--text-primary);
      align-self: flex-end;
    }
    .avaliacao-form-actions {
      display: flex;
      gap: 0.5rem;
      justify-content: flex-end;
    }

    .avaliacoes-list {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
      gap: 0.85rem;
    }
    .avaliacao-item {
      background: rgba(124, 58, 237, 0.03);
      border: 1px solid var(--border-color);
      border-radius: 10px;
      padding: 0.9rem 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .avaliacao-item-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 0.5rem;
    }
    .avaliacao-cargo {
      display: block;
      font-size: 0.72rem;
      color: var(--text-muted);
    }
    .avaliacao-status {
      font-size: 0.65rem;
      font-weight: 800;
      letter-spacing: 0.03em;
      color: var(--color-success);
      background: var(--color-success-light);
      padding: 0.15rem 0.5rem;
      border-radius: 9999px;
      white-space: nowrap;
    }
    .avaliacao-status.inativo {
      color: var(--text-muted);
      background: var(--bg-surface-elevated);
    }
    .stars-row-config {
      color: #f59e0b;
      font-size: 0.8rem;
      display: flex;
      gap: 0.15rem;
    }
    .avaliacao-texto-preview {
      font-size: 0.82rem;
      color: var(--text-secondary);
      margin: 0;
      display: -webkit-box;
      -webkit-line-clamp: 3;
      -webkit-box-orient: vertical;
      overflow: hidden;
    }
    .avaliacao-item-actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.6rem;
      padding-top: 0.35rem;
      border-top: 1px solid var(--border-color);
    }
    .btn-icon-edit {
      background: none;
      border: none;
      color: var(--primary);
      cursor: pointer;
      font-size: 0.95rem;
    }
    .empty-hint {
      font-size: 0.85rem;
      color: var(--text-muted);
      text-align: center;
      padding: 1rem 0;
      grid-column: 1 / -1;
    }

    /* Gestão de Usuários & Permissões */
    .usuario-form-box {
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      background: rgba(124, 58, 237, 0.04);
      border: 1px solid var(--border-color);
      border-radius: 10px;
      padding: 1rem;
    }
    .email-preview {
      display: flex;
      align-items: center;
      gap: 0.35rem;
      font-size: 0.72rem;
      color: var(--text-muted);
      margin-top: 0.3rem;
      padding-left: 0.1rem;
    }
    .permissoes-box {
      border-top: 1px dashed var(--border-color);
      padding-top: 0.65rem;
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    .role-select-row {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
    }
    .role-radio, .perm-check {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      font-size: 0.83rem;
      color: var(--text-primary);
      font-weight: 600;
      cursor: pointer;
    }
    .permissoes-checks {
      display: flex;
      flex-direction: column;
      gap: 0.4rem;
      padding: 0.6rem 0.75rem;
      background: var(--bg-surface-elevated);
      border-radius: 8px;
    }

    .usuarios-list {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
      gap: 0.85rem;
    }
    .usuario-item {
      background: rgba(124, 58, 237, 0.03);
      border: 1px solid var(--border-color);
      border-radius: 10px;
      padding: 0.9rem 1rem;
      display: flex;
      flex-direction: column;
      gap: 0.55rem;
    }
    .permissoes-chips {
      display: flex;
      flex-wrap: wrap;
      gap: 0.35rem;
    }
    .perm-chip {
      font-size: 0.68rem;
      font-weight: 700;
      background: var(--color-primary-light);
      color: var(--primary);
      padding: 0.15rem 0.5rem;
      border-radius: 9999px;
    }
    .perm-chip-empty {
      background: var(--bg-surface-elevated);
      color: var(--text-muted);
    }
  `]
})
export class ConfiguracoesComponent implements OnInit {
  private api = inject(ApiService);
  private authService = inject(AuthService);

  municipios = signal<Municipio[]>([]);
  novoMunicipioNome = '';
  novoMunicipioUf = 'AL';

  configLoja: ConfiguracaoLoja = {
    diasRetencao: 10,
    faixasDesconto: [
      { id: 1, qtdMinima: 5, percentualDesconto: 10 },
      { id: 2, qtdMinima: 10, percentualDesconto: 20 },
    ],
  };

  addFaixaOpen = false;
  novaFaixa: FaixaDesconto = { qtdMinima: 5, percentualDesconto: 10 };

  avaliacoes = signal<Avaliacao[]>([]);
  avaliacaoFormOpen = false;
  avaliacaoEditandoId: number | null = null;
  avaliacaoForm: Avaliacao = this.avaliacaoFormVazio();

  usuarios = signal<User[]>([]);
  usuarioFormOpen = false;
  usuarioEditandoId: number | null = null;
  usuarioForm: UsuarioRequest = this.usuarioFormVazio();

  ngOnInit(): void {
    this.carregar();
  }

  isAdmin(): boolean {
    return this.authService.currentUser()?.role === 'ADMIN';
  }

  carregar(): void {
    this.api.getMunicipios().subscribe((res) => {
      this.municipios.set(res);
    });

    this.api.getConfigLoja().subscribe((cfg) => {
      if (cfg) {
        this.configLoja = { ...cfg };
      }
    });

    this.api.getAvaliacoes().subscribe((res) => {
      this.avaliacoes.set(res || []);
    });

    if (this.isAdmin()) {
      this.api.getUsuarios().subscribe({
        next: (res) => this.usuarios.set(res || []),
        error: () => this.usuarios.set([]),
      });
    }
  }

  private avaliacaoFormVazio(): Avaliacao {
    return { clienteNome: '', cargoEmpresa: '', texto: '', nota: 5, ativo: true };
  }

  abrirNovaAvaliacao(): void {
    this.avaliacaoEditandoId = null;
    this.avaliacaoForm = this.avaliacaoFormVazio();
    this.avaliacaoFormOpen = true;
  }

  editarAvaliacao(a: Avaliacao): void {
    this.avaliacaoEditandoId = a.id ?? null;
    this.avaliacaoForm = { ...a };
    this.avaliacaoFormOpen = true;
  }

  cancelarAvaliacao(): void {
    this.avaliacaoFormOpen = false;
    this.avaliacaoEditandoId = null;
    this.avaliacaoForm = this.avaliacaoFormVazio();
  }

  salvarAvaliacao(): void {
    if (!this.avaliacaoForm.clienteNome.trim() || !this.avaliacaoForm.texto.trim()) {
      alert('Preencha ao menos o nome do cliente e o texto do depoimento.');
      return;
    }

    const acao = this.avaliacaoEditandoId
      ? this.api.updateAvaliacao(this.avaliacaoEditandoId, this.avaliacaoForm)
      : this.api.createAvaliacao(this.avaliacaoForm);

    acao.subscribe(() => {
      this.cancelarAvaliacao();
      this.carregar();
    });
  }

  removerAvaliacao(id: number): void {
    if (confirm('Deseja excluir esta avaliação? Ela deixará de aparecer no site.')) {
      this.api.deleteAvaliacao(id).subscribe(() => {
        this.carregar();
      });
    }
  }

  contarEstrelas(nota: number): number[] {
    return Array(Math.max(0, Math.min(5, Math.round(nota || 0)))).fill(0);
  }

  adicionarMunicipio(): void {
    if (!this.novoMunicipioNome.trim()) return;
    this.api.createMunicipio({ nome: this.novoMunicipioNome.trim(), uf: this.novoMunicipioUf.toUpperCase() }).subscribe(() => {
      this.novoMunicipioNome = '';
      this.carregar();
    });
  }

  removerMunicipio(id: number): void {
    if (confirm('Deseja remover este município?')) {
      this.api.deleteMunicipio(id).subscribe(() => {
        this.carregar();
      });
    }
  }

  confirmarFaixa(): void {
    if (this.novaFaixa.qtdMinima > 0 && this.novaFaixa.percentualDesconto > 0) {
      this.configLoja.faixasDesconto.push({ ...this.novaFaixa, id: Date.now() });
      this.addFaixaOpen = false;
      this.novaFaixa = { qtdMinima: 5, percentualDesconto: 10 };
    }
  }

  removerFaixa(i: number): void {
    this.configLoja.faixasDesconto.splice(i, 1);
  }

  salvarRegrasLoja(): void {
    this.api.updateConfigLoja(this.configLoja).subscribe(() => {
      alert('Parâmetros salvos com sucesso!');
    });
  }

  // === USUÁRIOS & PERMISSÕES ===
  private usuarioFormVazio(): UsuarioRequest {
    return { username: '', password: '', nomeCompleto: '', cargo: '', role: 'COLABORADOR', permissoes: [] };
  }

  emailGerado(): string {
    const username = (this.usuarioForm.username || '').trim().toLowerCase().replace(/[^a-z0-9._-]/g, '');
    return username ? `${username}@nexusdevelopment.tech` : '';
  }

  temPermissao(modulo: ModuloAcesso): boolean {
    return this.usuarioForm.permissoes.includes(modulo);
  }

  togglePermissao(modulo: ModuloAcesso): void {
    const idx = this.usuarioForm.permissoes.indexOf(modulo);
    if (idx === -1) {
      this.usuarioForm.permissoes.push(modulo);
    } else {
      this.usuarioForm.permissoes.splice(idx, 1);
    }
  }

  abrirNovoUsuario(): void {
    this.usuarioEditandoId = null;
    this.usuarioForm = this.usuarioFormVazio();
    this.usuarioFormOpen = true;
  }

  editarUsuario(u: User): void {
    this.usuarioEditandoId = u.id ?? null;
    this.usuarioForm = {
      username: u.username,
      password: '',
      nomeCompleto: u.nomeCompleto,
      cargo: u.cargo || '',
      role: (u.role as 'ADMIN' | 'COLABORADOR') || 'COLABORADOR',
      permissoes: [...(u.permissoes || [])],
      ativo: u.ativo,
    };
    this.usuarioFormOpen = true;
  }

  cancelarUsuario(): void {
    this.usuarioFormOpen = false;
    this.usuarioEditandoId = null;
    this.usuarioForm = this.usuarioFormVazio();
  }

  salvarUsuario(): void {
    if (!this.usuarioForm.nomeCompleto.trim() || !this.usuarioForm.username.trim()) {
      alert('Preencha ao menos o nome completo e o nome de usuário.');
      return;
    }
    if (!this.usuarioEditandoId && !this.usuarioForm.password?.trim()) {
      alert('Defina uma senha para o novo usuário.');
      return;
    }

    const acao = this.usuarioEditandoId
      ? this.api.updateUsuario(this.usuarioEditandoId, this.usuarioForm)
      : this.api.createUsuario(this.usuarioForm);

    acao.subscribe({
      next: () => {
        this.cancelarUsuario();
        this.carregar();
      },
      error: (err) => {
        alert(err?.error?.message || 'Não foi possível salvar o usuário. Verifique a conexão com o servidor.');
      },
    });
  }

  removerUsuario(u: User): void {
    if (u.username === this.authService.currentUser()?.username) {
      alert('Você não pode excluir o seu próprio usuário.');
      return;
    }
    if (confirm(`Deseja excluir o acesso de "${u.nomeCompleto}"? Ele não conseguirá mais fazer login.`)) {
      this.api.deleteUsuario(u.id!).subscribe({
        next: () => this.carregar(),
        error: (err) => alert(err?.error?.message || 'Não foi possível excluir este usuário.'),
      });
    }
  }
}
