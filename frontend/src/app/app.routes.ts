import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/landing/landing.component').then((m) => m.LandingComponent),
    title: 'Design Arte | Agência Criativa & Operacional',
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then((m) => m.LoginComponent),
    title: 'Login | Design Arte',
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard.component').then((m) => m.DashboardComponent),
    canActivate: [authGuard],
    title: 'Dashboard | Design Arte',
  },
  {
    path: 'tarefas',
    loadComponent: () => import('./pages/tarefas/tarefas.component').then((m) => m.TarefasComponent),
    canActivate: [authGuard],
    title: 'Tarefas & Demandas | Design Arte',
  },
  {
    path: 'clientes',
    loadComponent: () => import('./pages/clientes/clientes.component').then((m) => m.ClientesComponent),
    canActivate: [authGuard],
    title: 'Clientes & Faturas | Design Arte',
  },
  {
    path: 'servicos',
    loadComponent: () => import('./pages/servicos/servicos.component').then((m) => m.ServicosComponent),
    canActivate: [authGuard],
    title: 'Catálogo de Serviços | Design Arte',
  },
  {
    path: 'equipe',
    loadComponent: () => import('./pages/equipe/equipe.component').then((m) => m.EquipeComponent),
    canActivate: [authGuard],
    title: 'Gestão de Equipe & Salários | Design Arte',
  },
  {
    path: 'roteiros',
    loadComponent: () => import('./pages/roteiros/roteiros.component').then((m) => m.RoteirosComponent),
    canActivate: [authGuard],
    title: 'Roteiros & Gravações | Design Arte',
  },
  {
    path: 'projetos-concluidos',
    loadComponent: () => import('./pages/projetos-concluidos/projetos-concluidos.component').then((m) => m.ProjetosConcluidosComponent),
    canActivate: [authGuard],
    title: 'Projetos Concluídos | Design Arte',
  },
  {
    path: 'loja-fotos',
    loadComponent: () => import('./pages/loja-fotos/loja-fotos.component').then((m) => m.LojaFotosComponent),
    canActivate: [authGuard],
    title: 'Loja de Fotos & Álbuns | Design Arte',
  },
  {
    path: 'calendario',
    loadComponent: () => import('./pages/calendario/calendario.component').then((m) => m.CalendarioComponent),
    canActivate: [authGuard],
    title: 'Calendário de Entregas | Design Arte',
  },
  {
    path: 'financeiro',
    loadComponent: () => import('./pages/financeiro/financeiro.component').then((m) => m.FinanceiroComponent),
    canActivate: [authGuard],
    title: 'Financeiro, Faturas & Fluxo | Design Arte',
  },
  {
    path: 'relatorios',
    loadComponent: () => import('./pages/relatorios/relatorios.component').then((m) => m.RelatoriosComponent),
    canActivate: [authGuard],
    title: 'Relatórios Mensais | Design Arte',
  },
  {
    path: 'historico',
    loadComponent: () => import('./pages/historico/historico.component').then((m) => m.HistoricoComponent),
    canActivate: [authGuard],
    title: 'Histórico de Atividades | Design Arte',
  },
  {
    path: 'configuracoes',
    loadComponent: () => import('./pages/configuracoes/configuracoes.component').then((m) => m.ConfiguracoesComponent),
    canActivate: [authGuard],
    title: 'Configurações & Municípios | Design Arte',
  },
  {
    path: 'logos',
    loadComponent: () => import('./pages/logos/logos.component').then((m) => m.LogosComponent),
    canActivate: [authGuard],
    title: 'Repositório de Logos | Design Arte',
  },
  {
    path: 'eventos',
    loadComponent: () => import('./pages/eventos/eventos.component').then((m) => m.EventosComponent),
    canActivate: [authGuard],
    title: 'Eventos & Cobertura | Design Arte',
  },
  {
    path: 'aprovacao/:id',
    loadComponent: () => import('./pages/aprovacao/aprovacao.component').then((m) => m.AprovacaoComponent),
    title: 'Portal de Aprovação | Design Arte',
  },
  {
    path: 'aprovacao',
    loadComponent: () => import('./pages/aprovacao/aprovacao.component').then((m) => m.AprovacaoComponent),
    title: 'Portal de Aprovação | Design Arte',
  },
  {
    path: '**',
    redirectTo: '',
  },
];
