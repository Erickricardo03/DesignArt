import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pages/landing/landing.component').then(m => m.LandingComponent),
    title: 'Design Arte | Agência Criativa & Operacional'
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent),
    title: 'Login | Design Arte'
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [authGuard],
    title: 'Dashboard | Design Arte'
  },
  {
    path: 'tarefas',
    loadComponent: () => import('./pages/tarefas/tarefas.component').then(m => m.TarefasComponent),
    canActivate: [authGuard],
    title: 'Tarefas & Demandas | Design Arte'
  },
  {
    path: 'roteiros',
    loadComponent: () => import('./pages/roteiros/roteiros.component').then(m => m.RoteirosComponent),
    canActivate: [authGuard],
    title: 'Roteiros & Set | Design Arte'
  },
  {
    path: 'logos',
    loadComponent: () => import('./pages/logos/logos.component').then(m => m.LogosComponent),
    canActivate: [authGuard],
    title: 'Repositório de Logos | Design Arte'
  },
  {
    path: 'eventos',
    loadComponent: () => import('./pages/eventos/eventos.component').then(m => m.EventosComponent),
    canActivate: [authGuard],
    title: 'Cobertura de Eventos & Fotos | Design Arte'
  },
  {
    path: 'financeiro',
    loadComponent: () => import('./pages/financeiro/financeiro.component').then(m => m.FinanceiroComponent),
    canActivate: [authGuard],
    title: 'Financeiro & Fluxo de Caixa | Design Arte'
  },
  {
    path: 'relatorios',
    loadComponent: () => import('./pages/relatorios/relatorios.component').then(m => m.RelatoriosComponent),
    canActivate: [authGuard],
    title: 'Relatórios Mensais | Design Arte'
  },
  {
    path: '**',
    redirectTo: ''
  }
];
