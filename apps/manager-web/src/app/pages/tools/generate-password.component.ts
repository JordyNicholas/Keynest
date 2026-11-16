import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpClient } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { finalize } from 'rxjs';
import { API_BASE_URL } from '../../injectionTokens';
import { InlineErrorComponent } from '../inline-error.component';

@Component({
  selector: 'kn-generate-password-page',
  imports: [InlineErrorComponent, MatButtonModule],
  templateUrl: './generate-password.component.html',
  styleUrl: '../page-shared.css',
})
export class GeneratePasswordPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);
  private readonly destroyRef = inject(DestroyRef);

  readonly generated = signal('');
  readonly generating = signal(false);
  readonly error = signal('');

  generate(): void {
    if (this.generating()) return;
    this.generated.set('');
    this.error.set('');
    this.generating.set(true);
    this.http.post<{ password: string }>(`${this.apiBaseUrl}/tools/generate`, {})
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.generating.set(false)))
      .subscribe({
        next: ({ password }) => this.generated.set(password),
        error: () => this.error.set('Could not generate a password. Try again.'),
      });
  }

  clearGenerated(): void {
    this.generated.set('');
  }
}
