import { Component, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { JsonPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { API_BASE_URL } from '../../injectionTokens';

@Component({
  selector: 'kn-tools-page',
  imports: [JsonPipe, MatButtonModule],
  templateUrl: './tools.component.html',
  styleUrls: ['../page-shared.css', './tools.component.css'],
})
export class ToolsPageComponent {
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly http = inject(HttpClient);

  readonly health = signal<unknown>(null);
  readonly generated = signal('');
  readonly error = signal('');

  constructor() {
    this.refreshHealth();
  }

  refreshHealth(): void {
    this.http.get(`${this.apiBaseUrl}/tools/health`).subscribe({
      next: (body) => {
        this.health.set(body);
        this.error.set('');
      },
      error: () => this.error.set('API unreachable — start manager-api on :8080'),
    });
  }

  generate(): void {
    // Clear first so @if destroys the output and kn-leave can run; then enter on success.
    this.generated.set('');
    this.http.post<{ password: string }>(`${this.apiBaseUrl}/tools/generate`, {}).subscribe({
      next: (body) => {
        this.generated.set(body.password);
        this.error.set('');
      },
      error: () => this.error.set('Generate failed'),
    });
  }

  clearGenerated(): void {
    this.generated.set('');
  }
}
