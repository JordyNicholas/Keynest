import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ExpiryCounts } from '../vault/vault-metadata';

type Bucket = 'expired' | 'soon' | 'upcoming' | 'later' | 'unset';
@Component({
  selector: 'kn-expiry-distribution',
  imports: [RouterLink],
  template: `
    <div class="distribution">
      <div class="ring" [style.background]="gradient()" aria-hidden="true"><span><strong>{{ counts().total.toLocaleString() }}</strong><span>items</span></span></div>
      <ul class="legend" aria-label="Expiry groups and counts">
        @for (part of parts; track part.id) {
          <li><a routerLink="/vault" [queryParams]="{ expiry: part.id }"><span class="legend-dot" [style.background]="part.color" aria-hidden="true"></span><span>{{ part.label }}</span><strong>{{ counts()[part.id].toLocaleString() }}</strong></a></li>
        }
      </ul>
    </div>
  `,
  styleUrl: './expiry-distribution.component.css',
})
export class ExpiryDistributionComponent {
  readonly counts = input.required<ExpiryCounts>();
  readonly parts: { id: Bucket; label: string; color: string }[] = [
    { id: 'expired', label: 'Expired', color: 'var(--kn-color-danger)' },
    { id: 'soon', label: 'Less than 14 days', color: 'color-mix(in srgb, var(--kn-color-danger) 65%, var(--kn-color-canvas-mid))' },
    { id: 'upcoming', label: '14–29 days', color: 'var(--kn-color-warning)' },
    { id: 'later', label: '30+ days', color: 'var(--kn-color-success)' },
    { id: 'unset', label: 'No expiry', color: 'var(--kn-color-subtle)' },
  ];
  readonly gradient = computed(() => {
    const counts = this.counts();if (!counts.total) return 'var(--kn-color-outline)';
    let start = 0;
    const slices = this.parts.map(part => {
      const end = start + counts[part.id] / counts.total * 100;
      const slice = `${part.color} ${start}% ${end}%`;start = end;return slice;
    });
    return `conic-gradient(${slices.join(', ')})`;
  });
}
