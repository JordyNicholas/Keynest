import { OverlayContainer } from '@angular/cdk/overlay';
import { Component, signal } from '@angular/core';
import { fakeAsync, tick, TestBed } from '@angular/core/testing';
import { InlineErrorComponent } from './inline-error.component';

@Component({
  imports: [InlineErrorComponent],
  template: '<kn-inline-error [(message)]="error" /><input aria-label="Email" />',
})
class NoticeHost {
  readonly error = signal('Could not save this item.');
}

describe('Viewport error feedback', () => {
  it('shows a dismissible Material notification and removes it when the error clears', async () => {
    const fixture = TestBed.createComponent(InlineErrorComponent);
    fixture.componentRef.setInput('message', 'Could not save this item.');
    fixture.componentRef.setInput('details', ['Use a longer password.']);
    fixture.detectChanges();
    await fixture.whenStable();
    const overlay = TestBed.inject(OverlayContainer).getContainerElement();
    expect(overlay.textContent).toContain('Could not save this item.');
    expect(overlay.textContent).toContain('Use a longer password.');
    expect(overlay.textContent).toContain('Dismiss');
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    fixture.componentRef.setInput('message', '');
    fixture.detectChanges();
    await fixture.whenStable();
    expect(overlay.textContent).not.toContain('Could not save this item.');
    fixture.destroy();
  });
  it('expires after five seconds, clears the parent and can show the same error on a new attempt', fakeAsync(() => {
    const fixture = TestBed.createComponent(NoticeHost);
    fixture.detectChanges();
    tick(4999);
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    tick(1);
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toBe('');
    expect(fixture.nativeElement.querySelector('[role=alert]')).toBeNull();
    fixture.componentInstance.error.set('Could not save this item.');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role=alert]')).not.toBeNull();
    fixture.destroy();
    tick(5000);
  }));

  it('pauses while reading or focusing, then restarts a full display interval', fakeAsync(() => {
    const fixture = TestBed.createComponent(NoticeHost);
    fixture.detectChanges();
    const notice: HTMLElement = fixture.nativeElement.querySelector('[role=alert]');
    tick(3000);
    notice.dispatchEvent(new MouseEvent('mouseenter'));
    tick(6000);
    expect(fixture.componentInstance.error()).not.toBe('');
    notice.dispatchEvent(new MouseEvent('mouseleave'));
    notice.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    tick(6000);
    expect(fixture.componentInstance.error()).not.toBe('');
    notice.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: fixture.nativeElement.querySelector('input') }));
    tick(4999);
    expect(fixture.componentInstance.error()).not.toBe('');
    tick(1);
    expect(fixture.componentInstance.error()).toBe('');
    fixture.destroy();
  }));

  it('supports keyboard dismissal with focus return and a close target that fits narrow screens', () => {
    const fixture = TestBed.createComponent(NoticeHost);
    fixture.detectChanges();
    const host: HTMLElement = fixture.nativeElement;
    host.style.display = 'block';
    host.style.width = '240px';
    const notice = host.querySelector<HTMLElement>('[role=alert]')!;
    const close = notice.querySelector<HTMLButtonElement>('button[aria-label="Dismiss error"]')!;
    expect(close.getBoundingClientRect().width).toBeGreaterThanOrEqual(44);
    expect(close.getBoundingClientRect().right).toBeLessThanOrEqual(notice.getBoundingClientRect().right);
    expect(getComputedStyle(notice).borderRadius).not.toBe('0px');
    close.focus();
    close.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.error()).toBe('');
    expect(host.ownerDocument.activeElement).toBe(host.querySelector('input'));
    expect(host.querySelector('[role=alert]')).toBeNull();
    fixture.destroy();
  });

});
