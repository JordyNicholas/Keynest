import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../auth/auth.service';
import { InlineErrorComponent } from '../inline-error.component';
import { PasswordGeneratorOptionsComponent } from './password-generator-options.component';

@Component({
  selector: 'kn-generate-password-page',
  imports: [InlineErrorComponent, PasswordGeneratorOptionsComponent, MatButtonModule, RouterLink],
  templateUrl: './generate-password.component.html',
  styleUrl: '../page-shared.css',
})
export class GeneratePasswordPageComponent {
  readonly signedIn = inject(AuthService).signedIn;
  readonly copyStatus = signal('');
  readonly generated = signal('');
  readonly error = signal('');

  generate(event: Event, options: PasswordGeneratorOptionsComponent): void {
    event.preventDefault();
    this.clearGenerated();
    this.error.set('');
    try {
      this.generated.set(options.generate());
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
