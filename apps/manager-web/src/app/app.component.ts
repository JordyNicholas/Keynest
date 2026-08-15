import { Component, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { JsonPipe } from '@angular/common';

const API = 'http://localhost:8080/api/v1/tools';

@Component({
  selector: 'app-root',
  imports: [FormsModule, JsonPipe],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  readonly health = signal<unknown>(null);
  readonly generated = signal<string>('');
  readonly validatePassword = signal('');
  readonly validateResult = signal<unknown>(null);
  readonly error = signal<string>('');

  constructor(private readonly http: HttpClient) {
    this.refreshHealth();
  }

  refreshHealth(): void {
    this.http.get(`${API}/health`).subscribe({
      next: (body) => {
        this.health.set(body);
        this.error.set('');
      },
      error: () => this.error.set('API unreachable — start manager-api on :8080'),
    });
  }

  generate(): void {
    this.http.post<{ password: string }>(`${API}/generate`, {}).subscribe({
      next: (body) => {
        this.generated.set(body.password);
        this.error.set('');
      },
      error: () => this.error.set('Generate failed'),
    });
  }

  validate(): void {
    this.http
      .post(`${API}/validate`, { password: this.validatePassword() })
      .subscribe({
        next: (body) => {
          this.validateResult.set(body);
          this.error.set('');
        },
        error: () => this.error.set('Validate failed'),
      });
  }
}
