import { Injectable } from '@angular/core';
import { NativeDateAdapter } from '@angular/material/core';

export type DateOrder = 'locale' | 'dmy' | 'mdy' | 'iso';
type Part = 'day' | 'month' | 'year';

/** Strict numeric parsing; never hand ambiguous dates to Date.parse. */
@Injectable()
export class ExpiryDateAdapter extends NativeDateAdapter {
  private order: DateOrder = 'locale';

  chooseOrder(order: DateOrder): void { this.order = order;this.setLocale(this.locale); }

  private parts(): Part[] {
    if (this.order === 'dmy') return ['day', 'month', 'year'];
    if (this.order === 'mdy') return ['month', 'day', 'year'];
    if (this.order === 'iso') return ['year', 'month', 'day'];
    return this.formatter().formatToParts(new Date(2006, 10, 22))
      .map(part => part.type).filter((part): part is Part => ['day', 'month', 'year'].includes(part));
  }

  private formatter(): Intl.DateTimeFormat {
    return new Intl.DateTimeFormat(this.locale, { day: '2-digit', month: '2-digit', year: 'numeric', calendar: 'gregory', numberingSystem: 'latn' });
  }

  pattern(): string {
    const labels: Record<Part, string> = { day: 'DD', month: 'MM', year: 'YYYY' };
    if (this.order === 'locale') return this.formatter().formatToParts(new Date(2006, 10, 22))
      .map(part => labels[part.type as Part] ?? part.value).join('').replace(/[\u200e\u200f\u061c]/g, '');
    return this.parts().map(part => labels[part]).join(this.order === 'iso' ? '-' : '/');
  }

  override parse(value: unknown): Date | null {
    if (value === null || value === '') return null;
    if (value instanceof Date) return this.isValid(value) ? this.clone(value) : this.invalid();
    if (typeof value !== 'string') return this.invalid();
    const fields = value.trim().replace(/[\u200e\u200f\u061c]/g, '').split(/[/.\-\s]+/);
    if (fields.length !== 3) return this.invalid();
    const parts = this.parts();
    const values = {} as Record<Part, number>;
    for (let index = 0; index < parts.length; index++) {
      if (!(parts[index] === 'year' ? /^\d{4}$/ : /^\d{1,2}$/).test(fields[index])) return this.invalid();
      values[parts[index]] = Number(fields[index]);
    }
    const { year, month, day } = values;
    if (year < 1000 || year > 9999 || month < 1 || month > 12 || day < 1 || day > 31) return this.invalid();
    const date = new Date(year, month - 1, day);
    return date.getFullYear() === year && date.getMonth() === month - 1 && date.getDate() === day ? date : this.invalid();
  }

  override format(date: Date, displayFormat: Object): string {
    if (displayFormat !== 'input') return super.format(date, displayFormat);
    if (!this.isValid(date)) return '';
    if (this.order === 'locale') return this.formatter().format(date);
    const values = { year: String(date.getFullYear()).padStart(4, '0'), month: String(date.getMonth() + 1).padStart(2, '0'), day: String(date.getDate()).padStart(2, '0') };
    return this.parts().map(part => values[part]).join(this.order === 'iso' ? '-' : '/');
  }
}
