import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { InlineErrorComponent } from './inline-error.component';

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
});
