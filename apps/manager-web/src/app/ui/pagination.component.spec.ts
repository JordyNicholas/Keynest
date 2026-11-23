import { TestBed } from '@angular/core/testing';
import { PaginationComponent, pageTokens } from './pagination.component';

describe('Numbered Vault pagination', () => {
  it('keeps a bounded window, both edges, the current page and three mobile neighbors', () => {
    for (const total of [0, 1, 2, 5, 6, 30, 100001]) {
      for (const current of [0, 1, 4, 15, total - 1]) {
        const tokens = pageTokens(current, total);
        expect(tokens.length).toBeLessThanOrEqual(7);
        const pages = tokens.filter(token => token.page !== null).map(token => token.page!);
        if (!total) { expect(pages).toEqual([]);continue; }
        const selected = Math.min(Math.max(0, current), total - 1);
        expect(pages).toContain(0);expect(pages).toContain(total - 1);expect(pages).toContain(selected);
        expect(new Set(pages).size).toBe(pages.length);
        expect(tokens.filter(token => token.nearby).length).toBe(Math.min(3, total));
        expect(tokens.find(token => token.page === selected)?.nearby).toBeTrue();
      }
    }
  });

  function setup(page = 0, total = 30) {
    const fixture = TestBed.createComponent(PaginationComponent);
    fixture.componentRef.setInput('page', page);fixture.componentRef.setInput('totalPages', total);fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, host: fixture.nativeElement as HTMLElement };
  }

  it('marks the current page, prevents repeated/out-of-range requests and honors busy state', () => {
    const { fixture, component, host } = setup(14);const move = jasmine.createSpy('pageChange');component.pageChange.subscribe(move);
    expect(host.querySelector('[aria-current=page]')?.textContent?.trim()).toBe('15');
    component.move(-1);component.move(30);component.move(14);component.move(NaN);expect(move).not.toHaveBeenCalled();
    host.querySelector<HTMLButtonElement>('[aria-label="Page 16"]')!.click();expect(move).toHaveBeenCalledOnceWith(15);
    fixture.componentRef.setInput('busy', true);fixture.detectChanges();component.move(16);expect(move).toHaveBeenCalledTimes(1);
    expect(Array.from(host.querySelectorAll('button')).every(button => button.disabled)).toBeTrue();
  });

  it('keeps boundaries and page-size choices accessible without the old four disabled text buttons', () => {
    const { fixture, component, host } = setup(0, 1);const resize = jasmine.createSpy('sizeChange');component.sizeChange.subscribe(resize);
    expect(host.querySelector<HTMLButtonElement>('[aria-label="Previous page"]')!.disabled).toBeTrue();
    expect(host.querySelector<HTMLButtonElement>('[aria-label="Next page"]')!.disabled).toBeTrue();
    expect(host.querySelectorAll('button').length).toBe(3);
    const select = host.querySelector<HTMLSelectElement>('select')!;select.value = '25';select.dispatchEvent(new Event('change'));
    expect(resize).toHaveBeenCalledOnceWith(25);component.resize(100);component.resize(10);expect(resize).toHaveBeenCalledTimes(1);
    fixture.componentRef.setInput('totalPages', 0);fixture.detectChanges();expect(host.textContent).toContain('No matching pages');
  });

  it('reflows to three nearby numbers at a narrow container width without horizontal overflow', async () => {
    const { fixture, host } = setup(14, 30);host.style.inlineSize = '280px';fixture.detectChanges();await fixture.whenStable();
    const visible = Array.from(host.querySelectorAll<HTMLButtonElement>('.page-number')).filter(button => getComputedStyle(button).display !== 'none');
    expect(visible.length).toBe(3);expect(visible.some(button => button.getAttribute('aria-current') === 'page')).toBeTrue();
    expect(host.querySelector<HTMLElement>('.page-controls')!.getBoundingClientRect().width).toBeLessThanOrEqual(280);
  });
});
