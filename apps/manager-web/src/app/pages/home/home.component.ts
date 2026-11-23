import { Component, DestroyRef, inject, viewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { PublicToolsComponent } from './public-tools.component';
import { HomePreviewComponent } from './home-preview.component';
import { PublicHeaderComponent } from './public-header.component';
import { AuthDialogComponent } from '../../auth/auth-dialog.component';
import { authReturnUrl } from '../../auth/auth-return-url';

/** Public product information; account entry is explicit and no account data loads on entry. */
@Component({
  selector: 'kn-home-page',
  imports: [RouterLink, MatButtonModule, HomePreviewComponent, PublicHeaderComponent, PublicToolsComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomePageComponent {
  private readonly dialog = inject(MatDialog);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly tools = viewChild(PublicToolsComponent);
  openGenerator(): void { this.tools()?.open('generate', true); }

  private activeDialog?: MatDialogRef<AuthDialogComponent, boolean>;

  constructor() {
    this.destroyRef.onDestroy(() => {
      const dialog = this.activeDialog;this.activeDialog = undefined;dialog?.close();
    });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const mode = params.get('auth');
      if (mode !== 'signin' && mode !== 'signup') { this.activeDialog?.close();return; }
      if (this.activeDialog) { this.activeDialog.componentInstance.mode.set(mode);return; }
      const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      const dialog = this.dialog.open<AuthDialogComponent, { mode: 'signin' | 'signup' }, boolean>(AuthDialogComponent, {
        data: { mode }, width: '30rem', maxWidth: 'calc(100vw - 2rem)', maxHeight: 'calc(100dvh - 2rem)',
        backdropClass: 'kn-auth-backdrop', autoFocus: 'first-header', ariaLabelledBy: 'auth-dialog-title', restoreFocus: true,
        enterAnimationDuration: reduced ? 0 : 180, exitAnimationDuration: reduced ? 0 : 160,
      });
      this.activeDialog = dialog;
      dialog.componentInstance.modeChanged.subscribe(mode => {
        void this.router.navigate([], { relativeTo: this.route, queryParams: { auth: mode }, queryParamsHandling: 'merge', replaceUrl: true });
      });
      dialog.afterClosed().subscribe(success => {
        if (this.activeDialog !== dialog) return;
        this.activeDialog = undefined;
        if (success) {
          void this.router.navigateByUrl(authReturnUrl(this.router, this.route.snapshot.queryParamMap.get('returnTo')));
        } else if (this.route.snapshot.queryParamMap.has('auth')) {
          void this.router.navigate([], { relativeTo: this.route, queryParams: { auth: null }, queryParamsHandling: 'merge', replaceUrl: true });
        }
      });
    });
  }
}
