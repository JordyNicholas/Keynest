import { Component, input, output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { IconComponent } from '../../ui/icon.component';
import { VaultItem } from './vault-item';

/** Explicit item actions; secret lifetime and all requests remain with the Vault page. */
@Component({
  selector: 'kn-vault-item-details',
  imports: [DatePipe, MatButtonModule, RouterLink, IconComponent],
  templateUrl: './vault-item-details.component.html',
  styleUrl: './vault-item-details.component.css',
})
export class VaultItemDetailsComponent {
  readonly item = input.required<VaultItem>();
  readonly secret = input('');
  readonly remaining = input(0);
  readonly revealSeconds = input(15);
  readonly pending = input(false);
  readonly revealing = input(false);
  readonly reveal = output<void>();
  readonly conceal = output<void>();
  readonly remove = output<void>();
}
