import { Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { logoutSession } from '../../auth/logout-session';

@Component({
  selector: 'kn-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatButtonModule],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  readonly signedIn = this.auth.signedIn;
  private readonly http = inject(HttpClient);
  private readonly api = inject(API_BASE_URL);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly wide = toSignal(this.router.events.pipe(
    filter(event => event instanceof NavigationEnd), map(() => this.isWidePage()),
  ), { initialValue: this.isWidePage() });
  readonly loggingOut = signal(false);
  private transition?: Animation;

  constructor() {
    this.destroyRef.onDestroy(() => this.transition?.cancel());
    effect(() => { if (!this.signedIn()) void this.router.navigateByUrl('/', { replaceUrl: true }); });
  }

  keepActiveLinkVisible(link: HTMLElement, active: boolean): void {
    const nav = link.parentElement;
    if (!active || !nav || nav.scrollWidth <= nav.clientWidth) return;
    const bounds = nav.getBoundingClientRect(), item = link.getBoundingClientRect();
    nav.scrollBy({ left: Math.min(0, item.left - bounds.left - 4) + Math.max(0, item.right - bounds.right + 4) });
  }

  activatePage(page: HTMLElement): void {
    this.transition?.cancel();
    page.focus({ preventScroll: true });
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
    this.transition = page.animate(
      [{ opacity: 0.55, transform: 'translateY(4px)' }, { opacity: 1, transform: 'translateY(0)' }],
      { duration: 180, easing: 'ease-out' },
    );
  }

  skipToContent(event: Event, page: HTMLElement): void { event.preventDefault();page.focus(); }

  private isWidePage(): boolean {
    let route = this.route.snapshot;
    while (route.firstChild) route = route.firstChild;
    return route.data['wide'] === true;
  }

  logout(): void {
    if (this.loggingOut()) return;
    this.loggingOut.set(true);
    // Revocation can finish after leaving the view; the helper only clears its captured session.
    logoutSession(this.http, this.api, this.auth).subscribe(() => {
      if (this.destroyRef.destroyed) return;
      this.loggingOut.set(false);
      if (!this.auth.signedIn()) void this.router.navigateByUrl('/');
    });
  }
}
