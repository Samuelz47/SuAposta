import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login-page.component')
      .then((page) => page.LoginPageComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register-page.component')
      .then((page) => page.RegisterPageComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () => import('./features/dashboard/pages/dashboard-page.component')
      .then((page) => page.DashboardPageComponent)
  },
  {
    path: 'bets',
    loadComponent: () => import('./features/bets/pages/bets-page.component')
      .then((page) => page.BetsPageComponent)
  },
  { path: '**', redirectTo: 'dashboard' }
];
