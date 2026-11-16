import { Directive, signal } from '@angular/core';

@Directive({
  selector: 'input[knPasswordVisibility]',
  exportAs: 'knPasswordVisibility',
  host: { '[type]': "visible() ? 'text' : 'password'" },
})
export class PasswordVisibilityDirective {
  private readonly revealed = signal(false);
  readonly visible = this.revealed.asReadonly();

  toggle(): void {
    this.revealed.update((visible) => !visible);
  }

  hide(): void {
    this.revealed.set(false);
  }
}
