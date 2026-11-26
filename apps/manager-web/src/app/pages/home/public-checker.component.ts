import { Component, ElementRef, HostListener, OnDestroy, signal, viewChild } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import defaultPolicy from '../../../../../../libs/keynest-policy/src/main/resources/policies/keynest.default.v1.json';
import { PolicyCheck, previewBuiltInPolicy } from '../tools/password-policy-checks';

/** Raw samples live only in the input; derived rule booleans never retain the sample. */
@Component({
  selector: 'kn-public-checker',
  imports: [MatButtonModule],
  template: `
    <p id="public-check-help">Check Keynest’s fixed default rules in this browser. Your sample is never sent or saved.</p>
    <label for="public-check-sample">Sample password</label>
    <div class="sample-control">
      <input #sample id="public-check-sample" [type]="visible() ? 'text' : 'password'" autocomplete="off" autocapitalize="off" spellcheck="false"
        maxlength="128" aria-describedby="public-check-help" (input)="check(sample.value)" />
      <button mat-stroked-button type="button" aria-controls="public-check-sample"
        (click)="visible.set(!visible())">{{ visible() ? 'Hide password' : 'Show password' }}</button>
    </div>
    <button mat-stroked-button type="button" (click)="clear()">Clear sample</button>
    @if (checks(); as checks) {
      <p role="status">{{ status() }}</p>
      <ul aria-label="Default password rules">
        @for (check of checks; track check.id) {
          <li><span>{{ check.label }}</span><strong [attr.data-state]="check.met">{{ check.met === null ? 'Unconfirmed locally' : check.met ? 'Met' : 'Not met' }}</strong></li>
        }
      </ul>
    }
    <p class="note">This is policy feedback, not a password-strength guarantee. Username matching and final Vault validation happen when you save an item.</p>
  `,
  styles: [`
    :host { display: block; }
    p, label, .note { color: var(--kn-color-muted); }
    label { display: block; margin-block-end: 0.5rem; }
    .sample-control { display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; margin-block-end: 1rem; }
    input { box-sizing: border-box; flex: 1 1 15rem; min-inline-size: 0; inline-size: 100%; padding: 0.85rem; border: 1px solid var(--kn-color-outline); border-radius: 0.75rem; background: var(--kn-color-surface-raised); color: var(--kn-color-ink); font: inherit; }
    input:focus-visible { outline: 2px solid var(--kn-color-accent); outline-offset: 2px; }
    ul { list-style: none; padding: 0; }
    li { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.5rem 1rem; padding-block: 0.75rem; border-block-start: 1px solid var(--kn-color-outline); }
    strong { font-size: 0.875rem; color: var(--kn-color-muted); }
    strong[data-state=true] { color: var(--kn-color-accent); }
    strong[data-state=false] { color: var(--kn-color-danger); }
    .note { font-size: 0.875rem; }
  `],
})
export class PublicCheckerComponent implements OnDestroy {
  private readonly sample = viewChild<ElementRef<HTMLInputElement>>('sample');
  readonly checks = signal<PolicyCheck[] | null>(null);
  readonly status = signal('');
  readonly visible = signal(false);

  check(sample: string): void {
    sample = sample.slice(0, 128);
    if (!sample) { this.checks.set(null);this.status.set('');return; }
    const checks = previewBuiltInPolicy(sample.slice(0, 128), defaultPolicy.rules).map(check => ({ ...check,
      met: check.id === 'password.banned_regex'
        ? defaultPolicy.rules.bannedRegexes.every((pattern) => pattern === '(.)\\1{3,}') ? !/(.)\1{3,}/.test(sample) : null
        : check.met,
    }));
    this.checks.set(checks);
    this.status.set(checks.some(check => check.met === false) ? 'Some default rules are unmet.'
      : checks.some(check => check.met === null) ? 'Some rules cannot be confirmed locally.' : 'Meets the locally checked default rules.');
  }
  clear(): void { const field = this.sample();if (field) { field.nativeElement.value = '';field.nativeElement.type = 'password'; }this.visible.set(false);this.checks.set(null);this.status.set(''); }
  @HostListener('document:visibilitychange') onVisibilityChange(): void { if (document.hidden) this.clear(); }
  @HostListener('window:pagehide') onPageHide(): void { this.clear(); }
  ngOnDestroy(): void { this.clear(); }
}
