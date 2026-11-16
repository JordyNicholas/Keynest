import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../auth/auth.service';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';

@Component({
  selector: 'kn-generate-password-page',
  imports: [InlineErrorComponent, MatButtonModule, RouterLink],
  templateUrl: './generate-password.component.html',
  styleUrl: '../page-shared.css',
})
export class GeneratePasswordPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  readonly signedIn = inject(AuthService).signedIn;
  readonly copyStatus = signal('');
  readonly generated = signal('');
  readonly generating = signal(false);
  readonly error = signal('');

  generate(): void {
    if (this.generating()) return;
    this.clearGenerated();
    this.error.set('');
    this.generating.set(true);
    this.http.post<{ password: string }>(`${this.apiBaseUrl}/tools/generate`, {})
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.generating.set(false)))
      .subscribe({
        next: ({ password }) => this.generated.set(password),
        error: () => this.error.set('Could not generate a password. Try again.'),
      });
  }

  async copyGenerated(): Promise<void> {
    const password = this.generated();
    if (!password) return;
    try {
      await navigator.clipboard.writeText(password);
      if (this.generated() === password) this.copyStatus.set('Password copied.');
    } catch {
      if (this.generated() === password) this.copyStatus.set('Copy unavailable. Select and copy the password manually.');
    }
  }

  clearGenerated(): void {
    this.generated.set('');
    this.copyStatus.set('');
  }
}
