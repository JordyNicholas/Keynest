import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { VaultItemDetailsComponent } from './vault-item-details.component';

describe('Vault item actions', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(VaultItemDetailsComponent);
    fixture.componentRef.setInput('item', { id: 'sample-id', label: 'Synthetic account', policyId: 'keynest.default.v1' });fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, host: fixture.nativeElement as HTMLElement };
  }

  it('uses descriptive labels and local icons, and preserves explicit reveal/conceal/delete actions', () => {
    const { fixture, component, host } = setup();
    const reveal = jasmine.createSpy('reveal'), conceal = jasmine.createSpy('conceal'), remove = jasmine.createSpy('remove');
    component.reveal.subscribe(reveal);component.conceal.subscribe(conceal);component.remove.subscribe(remove);
    expect(host.querySelector('.vault-actions')?.getAttribute('role')).toBe('group');
    expect(host.querySelectorAll('kn-icon[aria-hidden=true]').length).toBe(4);
    expect(host.querySelector('[aria-label^="Reveal password"]')?.getAttribute('aria-label')).toContain('15 seconds');
    host.querySelector<HTMLButtonElement>('.reveal-action')!.click();expect(reveal).toHaveBeenCalledTimes(1);
    fixture.componentRef.setInput('secret', 'synthetic secret');fixture.componentRef.setInput('remaining', 12);fixture.detectChanges();
    expect(host.textContent).toContain('Concealing in 12 seconds');
    host.querySelector<HTMLButtonElement>('.reveal-action')!.click();expect(conceal).toHaveBeenCalledTimes(1);
    fixture.componentRef.setInput('pending', true);fixture.detectChanges();host.querySelector<HTMLButtonElement>('.delete-button')!.click();expect(remove).not.toHaveBeenCalled();
    fixture.componentRef.setInput('pending', false);fixture.detectChanges();host.querySelector<HTMLButtonElement>('.delete-button')!.click();expect(remove).toHaveBeenCalledTimes(1);
    expect(Array.from(host.querySelectorAll('.vault-actions a'), link => link.getAttribute('href'))).toEqual(['/vault/edit/sample-id', '/vault/rotate/sample-id']);
  });

  it('keeps text on one line and aligns actions at compact and desktop container widths', async () => {
    const { fixture, host } = setup();
    for (const width of [240, 280, 720]) {
      host.style.inlineSize = `${width}px`;fixture.detectChanges();await fixture.whenStable();
      for (const action of host.querySelectorAll<HTMLElement>('.vault-actions > *')) {
        expect(action.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
        expect(action.scrollWidth).toBeLessThanOrEqual(action.clientWidth + 1);
        expect(getComputedStyle(action.querySelector('.action-label')!).whiteSpace).toBe('nowrap');
      }
      const actions = Array.from(host.querySelectorAll<HTMLElement>('.vault-actions > *'));
      if (width === 720) expect(Math.abs(actions[0].getBoundingClientRect().top - actions[3].getBoundingClientRect().top)).toBeLessThan(1);
      if (width === 280) expect(actions[2].getBoundingClientRect().top).toBeGreaterThan(actions[0].getBoundingClientRect().top);
    }
  });
});
