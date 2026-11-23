import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MatDatepicker } from '@angular/material/datepicker';
import { DateAdapter, MAT_DATE_LOCALE } from '@angular/material/core';
import { ExpiryDateAdapter } from './expiry-date-adapter';
import { expiryInstant, localDateAfterDays } from './expiry-date';
import { ExpiryDatePickerComponent } from './expiry-date-picker.component';

describe('Localized expiry date parsing', () => {
  function adapter(locale: string) {
    TestBed.configureTestingModule({ providers: [{ provide: MAT_DATE_LOCALE, useValue: locale }, { provide: DateAdapter, useClass: ExpiryDateAdapter }] });
    return TestBed.inject(DateAdapter) as ExpiryDateAdapter;
  }

  it('uses day-first input in Brazil and validates leap days without rolling dates over', () => {
    const dates = adapter('pt-BR');
    expect(dates.parse('29/02/2028')?.getMonth()).toBe(1);
    expect(dates.parse('29/02/2028')?.getDate()).toBe(29);
    expect(dates.isValid(dates.parse('29/02/2027')!)).toBeFalse();
    expect(dates.isValid(dates.parse('31/04/2028')!)).toBeFalse();
    expect(dates.isValid(dates.parse('04/05/28')!)).toBeFalse();
    expect(dates.parse('04/05/2028')?.getMonth()).toBe(4);
  });

  it('uses month-first input in the US and allows explicit day-first or ISO entry', () => {
    const dates = adapter('en-US');expect(dates.parse('04/05/2028')?.getMonth()).toBe(3);
    dates.chooseOrder('dmy');expect(dates.parse('04/05/2028')?.getMonth()).toBe(4);
    expect(dates.format(new Date(2028, 4, 4), 'input')).toBe('04/05/2028');
    dates.chooseOrder('iso');expect(dates.parse('2028-05-04')?.getMonth()).toBe(4);
    expect(dates.format(new Date(2028, 4, 4), 'input')).toBe('2028-05-04');
    expect(dates.isValid(dates.parse('05/04/2028')!)).toBeFalse();
  });

  it('recognizes dotted localized entry and rejects arbitrary or incomplete dates', () => {
    const dates = adapter('de-DE');expect(dates.pattern()).toBe('DD.MM.YYYY');
    expect(dates.parse('31.12.2028')?.getDate()).toBe(31);
    for (const value of ['tomorrow', '12/2028', '2028-12-31T00:00:00Z', '00/12/2028', '31/13/2028']) expect(dates.isValid(dates.parse(value)!)).toBeFalse();
    expect(dates.parse('')).toBeNull();
  });

  it('uses local calendar arithmetic and converts the selected local day end to an exact instant', () => {
    const next = localDateAfterDays(1, new Date(2028, 2, 11, 22));
    expect(next.getDate()).toBe(12);expect(next.getHours()).toBe(0);
    const value = new Date(expiryInstant(next)!);
    expect(value.getFullYear()).toBe(2028);expect(value.getMonth()).toBe(2);expect(value.getDate()).toBe(12);
    expect(value.getHours()).toBe(23);expect(value.getMinutes()).toBe(59);expect(value.getMilliseconds()).toBe(999);
    expect(expiryInstant(null)).toBeNull();
  });
});

describe('Expiry date picker', () => {
  it('offers a Material calendar, keyboard input, format selection and invalid-date feedback', async () => {
    const fixture = TestBed.createComponent(ExpiryDatePickerComponent);fixture.detectChanges();await fixture.whenStable();
    const component = fixture.componentInstance;component.changeFormat('dmy');fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('input') as HTMLInputElement;
    input.value = '31/02/2032';input.dispatchEvent(new Event('input'));input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();await fixture.whenStable();expect(component.valid()).toBeFalse();
    expect(fixture.nativeElement.querySelector('mat-error').textContent).toContain('valid date');
    input.value = '29/02/2032';input.dispatchEvent(new Event('input'));input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();await fixture.whenStable();expect(component.valid()).toBeTrue();expect(component.value()?.getDate()).toBe(29);
    const calendar = fixture.debugElement.query(By.directive(MatDatepicker)).componentInstance as MatDatepicker<Date>;
    fixture.nativeElement.querySelector('mat-datepicker-toggle button').click();fixture.detectChanges();await fixture.whenStable();
    expect(calendar.opened).toBeTrue();expect(document.querySelector('mat-calendar')).not.toBeNull();
    calendar.select(new Date(2032, 3, 15));calendar.close();fixture.detectChanges();await fixture.whenStable();
    expect(component.value()?.getMonth()).toBe(3);expect(component.value()?.getDate()).toBe(15);
    component.changeFormat('iso');fixture.detectChanges();expect(input.value).toBe('2032-04-15');
  });
});
