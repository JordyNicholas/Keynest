import { Component, inject, output, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { AuthFormComponent, AuthMode } from './auth-form.component';

@Component({
  selector: 'kn-auth-dialog',
  imports: [AuthFormComponent, MatButtonModule, MatDialogModule],
  template: `
    <h2 mat-dialog-title id="auth-dialog-title">{{ mode() === 'signup' ? 'Create your account' : 'Welcome back' }}</h2>
    <mat-dialog-content>
      <p>Use your vault on this Keynest instance.</p>
      <kn-auth-form [mode]="mode()" (modeChange)="changeMode($event)" (authenticated)="dialog.close(true)" />
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-stroked-button type="button" mat-dialog-close>Close</button>
    </mat-dialog-actions>
  `,
  styles: [`
    :host { display: block; }
    h2 { font-family: var(--kn-font-display); font-size: 2rem; line-height: 1.2; }
    p { color: var(--kn-color-muted); font-size: 1rem; line-height: 1.6; }
    mat-dialog-content { max-block-size: 65dvh; }
    mat-dialog-actions { padding: 1rem 1.5rem; }
  `],
})
export class AuthDialogComponent {
  readonly dialog = inject<MatDialogRef<AuthDialogComponent, boolean>>(MatDialogRef);
  readonly mode = signal(inject<{ mode: AuthMode }>(MAT_DIALOG_DATA).mode);
  readonly modeChanged = output<AuthMode>();
  readonly form = viewChild(AuthFormComponent);

  constructor() {
    // Cancel before the closing animation, not just when the component is destroyed.
    this.dialog.beforeClosed().subscribe(() => this.form()?.cancel());
  }

  changeMode(mode: AuthMode): void {
    this.mode.set(mode);this.modeChanged.emit(mode);
  }
}
