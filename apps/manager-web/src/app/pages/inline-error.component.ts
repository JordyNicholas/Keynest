import { Component, input } from '@angular/core';

@Component({
  selector: 'kn-inline-error',
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
  readonly message = input.required<string>();
  readonly details = input<readonly string[]>([]);
}
