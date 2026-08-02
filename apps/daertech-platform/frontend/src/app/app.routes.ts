import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth.guard';
import { AdminShellComponent } from './layout/admin-shell.component';

export const appRoutes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./auth/login.component').then(m => m.LoginComponent)
  },
  {
    path: '',
    component: AdminShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', loadComponent: () => import('./dashboard/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'monitoring', loadComponent: () => import('./monitoring/monitoring-center.component').then(m => m.MonitoringCenterComponent) },
      { path: 'legacy', loadComponent: () => import('./legacy/legacy-admin.component').then(m => m.LegacyAdminComponent) }
    ]
  },
  { path: '**', redirectTo: '' }
];
