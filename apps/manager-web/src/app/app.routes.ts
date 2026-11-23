import { inject } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { Router, Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { VaultCreatePageComponent } from './pages/vault/vault-create.component';
import { VaultUpdatePageComponent } from './pages/vault/vault-update.component';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./pages/home/home.component').then((page) => page.HomePageComponent),
    canActivate: [() => inject(AuthService).signedIn() ? inject(Router).createUrlTree(['/overview']) : true],
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    children: [
      { path: 'overview', loadComponent: () => import('./pages/overview/overview.component').then(page => page.OverviewPageComponent), data: { wide: true } },
      { path: 'tools', loadComponent: () => import('./pages/tools/tools.component').then(page => page.ToolsPageComponent) },
      { path: 'vault/new', component: VaultCreatePageComponent },
      { path: 'vault/edit/:itemId', component: VaultUpdatePageComponent },
      { path: 'vault/rotate/:itemId', component: VaultUpdatePageComponent, data: { rotate: true } },
      {
        path: 'vault',
        data: { wide: true },
        loadComponent: () => import('./pages/vault/vault.component').then((page) => page.VaultPageComponent),
      },
      {
        path: 'account',
        loadComponent: () => import('./pages/account/account.component').then((page) => page.AccountPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
