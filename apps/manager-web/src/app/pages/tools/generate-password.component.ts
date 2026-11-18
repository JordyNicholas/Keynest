import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../auth/auth.service';
import { InlineErrorComponent } from '../inline-error.component';
import { generateBrowserPassword } from './browser-password-generator';
import { characterGroups, CharacterSelection, maxPasswordLength, minPasswordLength } from './password-options';

@Component({
  selector: 'kn-generate-password-page',
  imports: [InlineErrorComponent, FormsModule, MatButtonModule, RouterLink],
  templateUrl: './generate-password.component.html',
  styleUrl: '../page-shared.css',
})
export class GeneratePasswordPageComponent {
  readonly signedIn = inject(AuthService).signedIn;
  readonly copyStatus = signal('');
  readonly generated = signal('');
  readonly error = signal('');
  readonly length = signal<number | null>(20);
  readonly selection: CharacterSelection = { lower: true, upper: true, digits: true, symbols: true };
  readonly characterGroups = characterGroups;
  readonly minLength = minPasswordLength;
  readonly maxLength = maxPasswordLength;

  generate(event: Event, keyword: HTMLInputElement): void {
    event.preventDefault();
    this.clearGenerated();
    this.error.set('');
    try {
      this.generated.set(generateBrowserPassword(this.length(), this.selection, keyword.value.trim()));
    } catch (failure) {
      this.error.set(failure instanceof Error ? failure.message : 'Could not generate a password.');
    }
  }

  async copyGenerated(): Promise<void> {
    const password = this.generated();
    if (!password) return;
    try {
      await navigator.clipboard.writeText(password);
      if (this.generated() === password) this.copyStatus.set('Password copied.');
    } catch {
      if (this.generated() === password) this.copyStatus.set('Copy unavailable. Select and copy the password manually.');
    }
  }

  clearGenerated(): void {
    this.generated.set('');
    this.copyStatus.set('');
  }
}
