import { GeneratePasswordPageComponent } from './pages/tools/generate-password.component';
import { inject } from '@angular/core';
import { AuthService } from './auth/auth.service';
import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { ToolsPageComponent } from './pages/tools/tools.component';
import { VaultCreatePageComponent } from './pages/vault/vault-create.component';
import { VaultUpdatePageComponent } from './pages/vault/vault-update.component';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        redirectTo: () => inject(AuthService).signedIn() ? '/vault' : '/tools/generate',
      },
      { path: 'tools', component: ToolsPageComponent },
      { path: 'tools/generate', component: GeneratePasswordPageComponent },
      { path: 'tools/check', loadComponent: () => import('./pages/tools/check-password.component').then((page) => page.CheckPasswordPageComponent) },
      { path: 'status', loadComponent: () => import('./pages/tools/api-health.component').then((page) => page.ApiHealthPageComponent) },
      { path: 'vault/new', component: VaultCreatePageComponent, canActivate: [authGuard] },
      { path: 'vault/edit/:itemId', component: VaultUpdatePageComponent, canActivate: [authGuard] },
      { path: 'vault/rotate/:itemId', component: VaultUpdatePageComponent, canActivate: [authGuard], data: { rotate: true } },
      {
        path: 'vault',
        loadComponent: () => import('./pages/vault/vault.component').then((page) => page.VaultPageComponent),
        canActivate: [authGuard],
      },
      { path: 'policy-explanation', pathMatch: 'full', redirectTo: '/tools/check#why-these-rules' },
      {
        path: 'account',
        loadComponent: () => import('./pages/account/account.component').then((page) => page.AccountPageComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'tools' },
];
