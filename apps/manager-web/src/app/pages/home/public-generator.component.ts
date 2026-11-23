import { Component, HostListener, OnDestroy, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import defaultPolicy from '../../../../../../libs/keynest-policy/src/main/resources/policies/keynest.default.v1.json';
import { generateBrowserPassword } from '../tools/browser-password-generator';
import { defaultCharacterSelection } from '../tools/password-options';

/** Transient, local-only generation with the canonical packaged default policy. */
@Component({
  selector: 'kn-public-generator',
  imports: [MatButtonModule],
  templateUrl: './public-generator.component.html',
  styleUrl: './public-generator.component.css',
})
export class PublicGeneratorComponent implements OnDestroy {
  readonly generated = signal('');
  readonly status = signal('');

  generate(): void {
    this.clear();
    try {
      this.generated.set(generateBrowserPassword(20, defaultCharacterSelection, '', defaultPolicy.rules));
    } catch {
      this.status.set('Generation is unavailable in this browser. Try again in a secure browser window.');
    }
  }

  async copy(): Promise<void> {
    const password = this.generated();
    if (!password) return;
    try {
      await navigator.clipboard.writeText(password);
      if (this.generated() === password) this.status.set('Password copied.');
    } catch {
      if (this.generated() === password) this.status.set('Select and copy the password manually.');
    }
  }

  clear(): void { this.generated.set('');this.status.set(''); }
  @HostListener('document:visibilitychange')
  onVisibilityChange(): void { if (document.hidden) this.clear(); }
  @HostListener('window:pagehide')
  onPageHide(): void { this.clear(); }
  ngOnDestroy(): void { this.clear(); }
}
