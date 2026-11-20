import { Component, effect, inject, input } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'kn-inline-error',
  imports: [MatSnackBarModule],
  styleUrl: './page-shared.css',
  template: `
    @if (message()) {
      <div class="error" role="alert">
        <p>{{ message() }}</p>
        @if (details().length) {
          <ul>
            @for (detail of details(); track $index) {
              <li>{{ detail }}</li>
            }
          </ul>
        }
      </div>
    }
  `,
})
export class InlineErrorComponent {
  private readonly snack = inject(MatSnackBar);
  readonly message = input.required<string>();
  readonly details = input<readonly string[]>([]);

  constructor() {
    effect((cleanup) => {
      const message = this.message();
      if (!message) return;
      const notice = this.snack.open([message, ...this.details()].join(' '), 'Dismiss', {
        duration: 0, verticalPosition: 'bottom', horizontalPosition: 'center',
        politeness: 'off',
      });
      cleanup(() => notice.dismiss());
    });
  }
}
