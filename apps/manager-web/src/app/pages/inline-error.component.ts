import { Component, effect, ElementRef, inject, input, model, NgZone } from '@angular/core';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';

@Component({
  selector: 'kn-inline-error',
  imports: [MatSnackBarModule],
  styleUrl: './inline-error.component.css',
  template: `
    @if (message()) {
      <div class="error-notice" role="alert" (mouseenter)="pausePointer()" (mouseleave)="resumePointer()"
           (focusin)="pauseFocus()" (focusout)="resumeFocus($event)">
        <span class="notice-icon" aria-hidden="true">!</span>
        <div class="notice-content">
          <p>{{ message() }}</p>
          @if (details().length) {
            <ul>
              @for (detail of details(); track $index) { <li>{{ detail }}</li> }
            </ul>
          }
        </div>
        <button class="notice-close" type="button" aria-label="Dismiss error" (click)="dismiss()">
          <span aria-hidden="true">×</span>
        </button>
      </div>
    }
  `,
})
export class InlineErrorComponent {
  private readonly snack = inject(MatSnackBar);
  private readonly zone = inject(NgZone);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private timer: ReturnType<typeof setTimeout> | undefined;
  private pointerInside = false;
  private focusInside = false;
  readonly message = model.required<string>();
  readonly details = input<readonly string[]>([]);

  constructor() {
    effect((cleanup) => {
      const message = this.message();
      if (!message) {
        this.pointerInside = false;
        this.focusInside = false;
        return;
      }
      const notice = this.snack.open([message, ...this.details()].join(' '), 'Dismiss', {
        duration: 0, verticalPosition: 'bottom', horizontalPosition: 'center', politeness: 'off',
      });
      const action = notice.onAction().subscribe(() => this.dismiss());
      this.scheduleDismissal();
      cleanup(() => {
        this.cancelTimer();
        action.unsubscribe();
        notice.dismiss();
      });
    });
  }

  dismiss(): void {
    const host = this.host.nativeElement;
    const returnFocus = host.contains(host.ownerDocument.activeElement);
    this.cancelTimer();
    this.pointerInside = false;
    this.focusInside = false;
    this.message.set('');
    if (returnFocus) {
      Array.from(host.parentElement?.querySelectorAll<HTMLElement>('input:not([disabled]), button:not([disabled]), a[href]') ?? [])
        .find(element => !host.contains(element))?.focus();
    }
  }

  pausePointer(): void { this.pointerInside = true; this.cancelTimer(); }
  resumePointer(): void { this.pointerInside = false; this.scheduleDismissal(); }
  pauseFocus(): void { this.focusInside = true; this.cancelTimer(); }
  resumeFocus(event: FocusEvent): void {
    if (event.relatedTarget instanceof Node && this.host.nativeElement.contains(event.relatedTarget)) return;
    this.focusInside = false;
    this.scheduleDismissal();
  }

  private scheduleDismissal(): void {
    this.cancelTimer();
    if (this.pointerInside || this.focusInside || !this.message()) return;
    this.zone.runOutsideAngular(() => {
      this.timer = setTimeout(() => this.zone.run(() => this.dismiss()), 5000);
    });
  }

  private cancelTimer(): void {
    if (this.timer !== undefined) clearTimeout(this.timer);
    this.timer = undefined;
  }
}
