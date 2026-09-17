import { Routes } from '@angular/router';

import { authenticatedGuard, publicOnlyGuard } from './core/auth/auth.guards';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    canActivate: [publicOnlyGuard],
    loadComponent: () => import('./features/auth/login/login-page.component')
      .then((page) => page.LoginPageComponent)
  },
  {
    path: 'register',
    canActivate: [publicOnlyGuard],
    loadComponent: () => import('./features/auth/register/register-page.component')
      .then((page) => page.RegisterPageComponent)
  },
  {
    path: 'dashboard',
    canActivate: [authenticatedGuard],
    loadComponent: () => import('./features/dashboard/pages/dashboard-page.component')
      .then((page) => page.DashboardPageComponent)
  },
  {
    path: 'bets',
    canActivate: [authenticatedGuard],
    loadComponent: () => import('./features/bets/pages/bets-page.component')
      .then((page) => page.BetsPageComponent)
  },
  { path: '**', redirectTo: 'dashboard' }
];
