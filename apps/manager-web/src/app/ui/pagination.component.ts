import { Component, computed, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { IconComponent } from './icon.component';

export type PageToken = { key: string; page: number | null; nearby: boolean };

/** Bounded window: at most five page buttons and two gaps, independent of total page count. */
export function pageTokens(page: number, total: number): PageToken[] {
  if (!Number.isInteger(total) || total < 1) return [];
  const current = Math.min(Math.max(0, Number.isInteger(page) ? page : 0), total - 1);
  const mobileStart = Math.max(0, Math.min(current - 1, total - 3));
  const token = (value: number): PageToken => ({ key: String(value), page: value, nearby: value >= mobileStart && value < mobileStart + 3 });
  if (total <= 5) return Array.from({ length: total }, (_, index) => token(index));
  const start = Math.max(1, Math.min(current - 1, total - 4));
  const result = [token(0)];
  if (start > 1) result.push({ key: 'before', page: null, nearby: false });
  for (let index = start; index <= Math.min(total - 2, start + 2); index++) result.push(token(index));
  if (start + 2 < total - 2) result.push({ key: 'after', page: null, nearby: false });
  result.push(token(total - 1));
  return result;
}

@Component({
  selector: 'kn-pagination',
  imports: [MatButtonModule, IconComponent],
  templateUrl: './pagination.component.html',
  styleUrl: './pagination.component.css',
})
export class PaginationComponent {
  readonly page = input(0);
  readonly totalPages = input(0);
  readonly size = input(10);
  readonly busy = input(false);
  readonly pageChange = output<number>();
  readonly sizeChange = output<number>();
  readonly tokens = computed(() => pageTokens(this.page(), this.totalPages()));

  move(page: number): void {
    if (!this.busy() && Number.isInteger(page) && page >= 0 && page < this.totalPages() && page !== this.page()) this.pageChange.emit(page);
  }
  resize(size: number): void {
    if (!this.busy() && [10, 25, 30, 50].includes(size) && size !== this.size()) this.sizeChange.emit(size);
  }
}
