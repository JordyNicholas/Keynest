import { Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { VaultOverview } from '../vault/vault-metadata';

@Component({
  selector: 'kn-overview-metrics',
  imports: [RouterLink],
  templateUrl: './overview-metrics.component.html',
  styleUrl: './overview-metrics.component.css',
})
export class OverviewMetricsComponent {
  readonly overview = input.required<VaultOverview>();
  readonly nextDays = computed(() => {
    const data = this.overview();
    const remaining = data.nextExpiry?.expiresAt ? Date.parse(data.nextExpiry.expiresAt) - Date.parse(data.asOf) : 0;
    const days = Math.floor(remaining / 86_400_000);
    return { value: days < 1 ? '<1' : days.toLocaleString(), unit: days <= 1 ? 'day' : 'days' };
  });
}
