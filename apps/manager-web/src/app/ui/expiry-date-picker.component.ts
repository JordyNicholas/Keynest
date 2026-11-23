import { Component, computed, inject, input, model, viewChild } from '@angular/core';
import { FormsModule, NgModel } from '@angular/forms';
import { DateAdapter, MAT_DATE_FORMATS, MAT_DATE_LOCALE, MAT_NATIVE_DATE_FORMATS } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { DateOrder, ExpiryDateAdapter } from './expiry-date-adapter';

@Component({
  selector: 'kn-expiry-date-picker',
  imports: [FormsModule, MatDatepickerModule, MatFormFieldModule, MatInputModule],
  providers: [
    { provide: MAT_DATE_LOCALE, useFactory: () => navigator.language || 'en-GB' },
    { provide: DateAdapter, useClass: ExpiryDateAdapter },
    { provide: MAT_DATE_FORMATS, useValue: { ...MAT_NATIVE_DATE_FORMATS, parse: { dateInput: 'input' }, display: { ...MAT_NATIVE_DATE_FORMATS.display, dateInput: 'input' } } },
  ],
  template: `
    <label>Date format
      <select aria-label="Expiry date format" [disabled]="disabled()" (change)="changeFormat($any($event.target).value)">
        <option value="locale">Browser locale</option><option value="dmy">DD/MM/YYYY</option>
        <option value="mdy">MM/DD/YYYY</option><option value="iso">YYYY-MM-DD</option>
      </select>
    </label>
    @if (existingDate(); as existing) { <p>Current expiry: {{ existing.toLocaleString() }}.</p> }
    <mat-form-field appearance="outline">
      <mat-label>{{ label() }}</mat-label>
      <input matInput #dateModel="ngModel" [matDatepicker]="calendar" [min]="minimum" [max]="maximum"
        [ngModel]="value()" (ngModelChange)="value.set($event)" [ngModelOptions]="{standalone: true}"
        [disabled]="disabled()" [placeholder]="adapter.pattern()" autocomplete="off" />
      <mat-datepicker-toggle matIconSuffix [for]="calendar" />
      <mat-datepicker #calendar [startAt]="value() ?? existingDate()" />
      <mat-hint>{{ adapter.pattern() }} · Expires at the end of this date in your local timezone.</mat-hint>
      <mat-error>Enter a valid date from today through 31 December 9999 using {{ adapter.pattern() }}.</mat-error>
    </mat-form-field>
  `,
  styles: [`
    :host { display: block; margin-block-end: 1.25rem; }
    label { display: grid; gap: 0.4rem; margin-block-end: 0.75rem; color: var(--kn-color-muted); }
    select { min-block-size: 2.75rem; padding: 0.5rem; font: inherit; border: 1px solid var(--kn-color-outline); border-radius: 0.5rem; color: var(--kn-color-ink); background: var(--kn-color-surface); }
    mat-form-field { inline-size: 100%; }
  `],
})
export class ExpiryDatePickerComponent {
  readonly adapter = inject(DateAdapter) as ExpiryDateAdapter;
  readonly value = model<Date | null>(null);
  readonly disabled = input(false);
  readonly existingExpiry = input<string>();
  readonly existingDate = computed(() => { const value = this.existingExpiry();return value ? new Date(value) : null; });
  readonly label = input('Expiry date (optional)');
  readonly minimum = new Date(new Date().setHours(0, 0, 0, 0));
  readonly maximum = new Date(9999, 11, 31);
  private readonly field = viewChild(NgModel);

  valid(): boolean { return this.field()?.valid !== false; }
  changeFormat(order: DateOrder): void { this.adapter.chooseOrder(order); }
}
