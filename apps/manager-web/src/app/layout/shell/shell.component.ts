import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../auth/auth.service';

@Component({
  selector: 'kn-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  readonly signedIn = inject(AuthService).signedIn;
  private transition?: Animation;

  animatePage(page: HTMLElement): void {
    this.transition?.cancel();
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    this.transition = page.animate(
      [{ opacity: 0.55, transform: 'translateY(4px)' }, { opacity: 1, transform: 'translateY(0)' }],
      { duration: 180, easing: 'ease-out' },
    );
  }
}
