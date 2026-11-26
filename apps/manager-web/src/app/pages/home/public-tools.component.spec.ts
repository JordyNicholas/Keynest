import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { PublicToolsComponent } from './public-tools.component';
import { PublicGeneratorComponent } from './public-generator.component';
import { PublicCheckerComponent } from './public-checker.component';

describe('Public homepage tools', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] }));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('starts collapsed, mounts one tool, and clears generated values immediately on switching', async () => {
    const fixture = TestBed.createComponent(PublicToolsComponent);fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    expect(host.querySelector('kn-public-generator, kn-public-checker')).toBeNull();
    expect(host.querySelectorAll('mat-expansion-panel-header[aria-expanded=false]').length).toBe(2);
    host.querySelectorAll<HTMLElement>('mat-expansion-panel-header')[0].click();fixture.detectChanges();
    const generator = fixture.debugElement.query(By.directive(PublicGeneratorComponent)).componentInstance as PublicGeneratorComponent;
    generator.generate();expect(generator.generated()).not.toBe('');
    host.querySelectorAll<HTMLElement>('mat-expansion-panel-header')[1].click();fixture.detectChanges();
    expect(generator.generated()).toBe('');expect(host.querySelector('kn-public-generator')).toBeNull();
    expect(host.querySelector('kn-public-checker')).not.toBeNull();
    expect(host.querySelectorAll('mat-expansion-panel-header[aria-expanded=true]').length).toBe(1);
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('checks samples locally without persistence and clears input on close', () => {
    const fixture = TestBed.createComponent(PublicToolsComponent);fixture.detectChanges();fixture.componentInstance.open('check');fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;const input = host.querySelector<HTMLInputElement>('#public-check-sample')!;
    const persistent = spyOn(localStorage, 'setItem'), tab = spyOn(sessionStorage, 'setItem');
    input.value = 'aB2#mangoC3$deF4%';input.dispatchEvent(new Event('input'));fixture.detectChanges();
    expect(host.textContent).toContain('Meets the locally checked default rules');
    input.value = 'aB2#mangoooooC3$deF4%';input.dispatchEvent(new Event('input'));fixture.detectChanges();
    expect(host.textContent).toContain('Some default rules are unmet');
    fixture.componentInstance.close('check');fixture.detectChanges();expect(input.value).toBe('');
    expect(host.querySelector('#public-check-sample')).toBeNull();expect(persistent).not.toHaveBeenCalled();expect(tab).not.toHaveBeenCalled();
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('keeps the closing panel size after its tool has been destroyed and releases it on reopen', () => {
    const fixture = TestBed.createComponent(PublicToolsComponent);
    fixture.detectChanges();
    fixture.componentInstance.open('generate');
    fixture.detectChanges();
    const content = fixture.nativeElement.querySelector('kn-public-generator').parentElement as HTMLElement;
    const expandedHeight = content.offsetHeight;
    expect(expandedHeight).toBeGreaterThan(0);

    fixture.componentInstance.close('generate');
    fixture.detectChanges();
    expect(content.querySelector('kn-public-generator')).toBeNull();
    expect(content.style.minBlockSize).toBe(`${expandedHeight}px`);

    fixture.componentInstance.open('generate');
    fixture.detectChanges();
    expect(content.querySelector('kn-public-generator')).not.toBeNull();
    expect(content.style.minBlockSize).toBe('');
  });

  it('supports keyboard expansion and clears samples on page hide', () => {
    const fixture = TestBed.createComponent(PublicToolsComponent);fixture.detectChanges();
    const header = fixture.nativeElement.querySelectorAll('mat-expansion-panel-header')[1] as HTMLElement;
    header.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', keyCode: 13, bubbles: true }));fixture.detectChanges();
    expect(header.getAttribute('aria-expanded')).toBe('true');
    const input = fixture.nativeElement.querySelector('#public-check-sample') as HTMLInputElement;input.value = 'Synthetic temporary sample';
    window.dispatchEvent(new Event('pagehide'));fixture.detectChanges();expect(input.value).toBe('');expect(fixture.componentInstance.active()).toBeNull();
  });

  it('reveals only on request without changing the sample or sending or persisting it, then remasks on clear', () => {
    const fixture = TestBed.createComponent(PublicCheckerComponent);fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    const input = host.querySelector<HTMLInputElement>('#public-check-sample')!;
    const toggle = host.querySelector<HTMLButtonElement>('button[aria-controls="public-check-sample"]')!;
    const persistent = spyOn(localStorage, 'setItem'), tab = spyOn(sessionStorage, 'setItem');
    expect(input.type).toBe('password');expect(toggle.textContent).toContain('Show password');
    input.value = 'Synthetic visibility sample';input.dispatchEvent(new Event('input'));fixture.detectChanges();
    const feedback = fixture.componentInstance.checks();
    toggle.click();fixture.detectChanges();
    expect(input.type).toBe('text');expect(input.value).toBe('Synthetic visibility sample');expect(fixture.componentInstance.checks()).toBe(feedback);
    expect(toggle.textContent).toContain('Hide password');
    toggle.click();fixture.detectChanges();expect(input.type).toBe('password');expect(input.value).toBe('Synthetic visibility sample');
    toggle.click();fixture.detectChanges();
    fixture.componentInstance.clear();expect(input.type).toBe('password');expect(input.value).toBe('');
    fixture.detectChanges();
    expect(persistent).not.toHaveBeenCalled();expect(tab).not.toHaveBeenCalled();TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('remasks and clears a visible sample immediately on close and page hide', () => {
    const fixture = TestBed.createComponent(PublicToolsComponent);fixture.detectChanges();fixture.componentInstance.open('check');fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    const input = host.querySelector<HTMLInputElement>('#public-check-sample')!;
    const toggle = host.querySelector<HTMLButtonElement>('button[aria-controls="public-check-sample"]')!;
    input.value = 'Synthetic visibility sample';toggle.click();fixture.detectChanges();expect(input.type).toBe('text');
    fixture.componentInstance.close('check');expect(input.value).toBe('');expect(input.type).toBe('password');
    fixture.detectChanges();fixture.componentInstance.open('check');fixture.detectChanges();
    const reopened = host.querySelector<HTMLInputElement>('#public-check-sample')!;
    expect(reopened.type).toBe('password');expect(reopened.value).toBe('');
    reopened.value = 'Synthetic next sample';host.querySelector<HTMLButtonElement>('button[aria-controls="public-check-sample"]')!.click();fixture.detectChanges();
    window.dispatchEvent(new Event('pagehide'));expect(reopened.value).toBe('');expect(reopened.type).toBe('password');
  });
});
