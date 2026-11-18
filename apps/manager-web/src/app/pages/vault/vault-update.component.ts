import { NgTemplateOutlet } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, computed, DestroyRef, effect, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, EMPTY, finalize, switchMap } from 'rxjs';
import { AuthService } from '../../auth/auth.service';
import { API_BASE_URL } from '../../injectionTokens';
import { confirmVaultChange } from '../confirm-vault-change';
import { InlineErrorComponent } from '../inline-error.component';
import { passwordViolationMessages } from '../password-violation-messages';
import { generateBrowserPassword } from '../tools/browser-password-generator';
import { BuiltInPolicy, generatorOptionsForPolicy } from '../tools/built-in-policy';
import { PasswordGeneratorOptionsComponent } from '../tools/password-generator-options.component';
import { defaultCharacterSelection, defaultPasswordLength } from '../tools/password-options';
import { VaultItem } from './vault-item';

@Component({
  selector: 'kn-vault-update-page',
  imports: [FormsModule, NgTemplateOutlet, InlineErrorComponent, MatButtonModule, PasswordGeneratorOptionsComponent, RouterLink],
  templateUrl: './vault-update.component.html',
  styleUrls: ['../page-shared.css', './vault-create.component.css', './vault-update.component.css'],
})
export class VaultUpdatePageComponent {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly apiBaseUrl = inject(API_BASE_URL);
  private readonly session = this.auth.token();
  private readonly policy = signal<BuiltInPolicy | null>(null);
  private readonly generatorOptions = viewChild(PasswordGeneratorOptionsComponent);
  private readonly replacementInput = viewChild<ElementRef<HTMLInputElement>>('replacement');

  readonly rotating = this.route.snapshot.data['rotate'] === true;
  readonly item = signal<VaultItem | null>(null);
  readonly loading = signal(true);
  readonly pending = signal(false);
  readonly label = signal('');
  readonly username = signal('');
  readonly url = signal('');
  readonly expiryDays = signal<number | null>(null);
  readonly noticeDays = signal<number | null>(null);
  readonly error = signal('');
  readonly details = signal<string[]>([]);
  readonly hasReplacement = signal(false);
  readonly copyStatus = signal('');
  readonly generatorOpen = signal(false);
  readonly generationMode = signal<'policy' | 'custom'>('policy');
  readonly loadingPolicy = signal(false);
  readonly policyRules = computed(() => {
    const rules = this.policy()?.rules;
    if (!rules) return null;
    const settings = generatorOptionsForPolicy(rules);
    return {
      ...settings,
      defaultLength: defaultPasswordLength(settings.minLength, settings.maxLength),
    };
  });

  constructor() {
    effect(() => {
      if (this.auth.token() !== this.session) {
        this.item.set(null);
        void this.router.navigateByUrl('/account');
      }
    });
    this.route.paramMap.pipe(
      switchMap((params) => {
        const input = this.replacementInput()?.nativeElement;
        if (input) input.value = '';
        this.hasReplacement.set(false);
        this.copyStatus.set('');
        this.generatorOpen.set(false);
        this.generationMode.set('policy');
        this.loadingPolicy.set(false);
        this.policy.set(null);
        this.item.set(null);
        this.error.set('');
        this.details.set([]);
        const id = params.get('itemId');
        if (!id || !this.sessionActive()) {
          this.loading.set(false);
          return EMPTY;
        }
        this.loading.set(true);
        return this.http.get<VaultItem>(this.itemUrl(id), { headers: this.auth.authHeaders() }).pipe(
          catchError(() => {
            if (this.sessionActive()) this.error.set('Could not open this vault item.');
            return EMPTY;
          }),
          finalize(() => this.loading.set(false)),
        );
      }),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((item) => {
      if (!this.sessionActive()) return;
      this.label.set(item.label);
      this.username.set(item.username ?? '');
      this.url.set(item.url ?? '');
      this.expiryDays.set(null);
      this.noticeDays.set(item.notifyBeforeDays ?? null);
      this.item.set(item);
    });
  }

  setGeneratorOpen(open: boolean): void {
    this.generatorOpen.set(open);
    if (open) {
      this.loadPolicy();
    } else {
      this.generationMode.set('policy');
    }
  }

  loadPolicy(): void {
    const item = this.item();
    if (!this.rotating || !item || this.policy() || this.loadingPolicy() || !this.sessionActive()) return;
    this.loadingPolicy.set(true);
    this.http.get<BuiltInPolicy>(`${this.apiBaseUrl}/tools/policy`, { params: { policyId: item.policyId } })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (this.item() === item) this.loadingPolicy.set(false);
      }))
      .subscribe({
        next: (policy) => {
          if (this.sessionActive() && this.item() === item && policy.id === item.policyId) this.policy.set(policy);
        },
        error: () => {
          if (this.item() === item) this.policy.set(null);
        },
      });
  }

  generateReplacement(input: HTMLInputElement): void {
    const rules = this.policyRules();
    if (!this.rotating || !this.item() || !rules || this.pending() || !this.sessionActive()) return;

    const options = this.generatorOptions();
    const custom = this.generationMode() === 'custom';
    if (custom && !options) return;
    this.copyStatus.set('');
    this.error.set('');
    this.details.set([]);
    try {
      input.value = custom && options
        ? options.generate()
        : generateBrowserPassword(rules.defaultLength, defaultCharacterSelection, '', rules);
      this.replacementChanged(input);
      this.copyStatus.set('Replacement generated.');
    } catch (failure) {
      this.error.set(failure instanceof Error ? failure.message : 'Could not generate a replacement password.');
    }
  }

  replacementChanged(input: HTMLInputElement): void {
    this.hasReplacement.set(Boolean(input.value));
    this.copyStatus.set('');
  }

  async copyReplacement(input: HTMLInputElement): Promise<void> {
    if (!this.sessionActive() || !input.value || this.pending()) return;
    const password = input.value;
    let message: string;
    try {
      await navigator.clipboard.writeText(password);
      message = 'Replacement copied.';
    } catch {
      message = 'Could not copy the replacement. Check your browser’s clipboard permissions.';
    }
    if (this.sessionActive() && input.isConnected && input.value === password) this.copyStatus.set(message);
  }

  save(event: Event, input?: HTMLInputElement): void {
    event.preventDefault();
    const item = this.item();
    if (!item || this.pending() || !this.sessionActive()) return;
    this.error.set('');
    this.details.set([]);

    let changes: { password?: string; label?: string; username?: string; url?: string };
    if (this.rotating) {
      const password = input?.value;
      if (!password?.trim()) {
        this.error.set('Enter or generate a replacement password.');
        return;
      }
      if (!confirmVaultChange('replace-password')) return;
      changes = { password };
    } else {
      const label = this.label().trim();
      if (!label) {
        this.error.set('Enter a label for the vault item.');
        return;
      }
      const username = this.username().trim();
      const url = this.url().trim();
      changes = {};
      if (label !== item.label) changes.label = label;
      if (username !== (item.username ?? '')) changes.username = username;
      if (url !== (item.url ?? '')) changes.url = url;
    }

    const expiresInDays = this.expiryDays();
    const notifyBeforeDays = this.noticeDays();
    if (!Object.keys(changes).length && expiresInDays === null && notifyBeforeDays === (item.notifyBeforeDays ?? null)) {
      void this.router.navigateByUrl('/vault');
      return;
    }
    if (input) {
      input.value = '';
      this.replacementChanged(input);
    }
    this.pending.set(true);
    this.http.put(this.itemUrl(item.id), { ...changes, expiresInDays, notifyBeforeDays }, {
      headers: this.auth.authHeaders(),
    }).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.pending.set(false)))
      .subscribe({
        next: () => {
          if (this.sessionActive() && this.item() === item) void this.router.navigateByUrl('/vault');
        },
        error: ({ error }) => {
          if (!this.sessionActive() || this.item() !== item) return;
          if (error?.code === 'VAULT_PASSWORD_POLICY') {
            this.details.set(passwordViolationMessages(error.violations));
            this.error.set('The replacement password does not meet the vault policy.');
          } else {
            this.error.set('Could not save the vault item. Try again.');
          }
        },
      });
  }

  private itemUrl(id: string): string {
    return `${this.apiBaseUrl}/vault/items/${encodeURIComponent(id)}`;
  }

  private sessionActive(): boolean {
    return Boolean(this.session) && this.auth.token() === this.session;
  }
}
