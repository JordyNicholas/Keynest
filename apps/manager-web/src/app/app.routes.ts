import { Routes } from '@angular/router';
import { ShellComponent } from './layout/shell/shell.component';
import { ToolsPageComponent } from './pages/tools/tools.component';
import { VaultPageComponent } from './pages/vault/vault.component';
import { AccountPageComponent } from './pages/account/account.component';
import { authGuard } from './auth/auth.guard';

export const routes: Routes = [
  {
    path: '',
    component: ShellComponent,
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'tools' },
      { path: 'tools', component: ToolsPageComponent },
      { path: 'vault', component: VaultPageComponent, canActivate: [authGuard] },
      { path: 'account', component: AccountPageComponent },
    ],
  },
  { path: '**', redirectTo: 'tools' },
];
