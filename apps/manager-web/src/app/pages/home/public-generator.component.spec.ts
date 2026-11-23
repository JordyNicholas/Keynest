import { TestBed } from '@angular/core/testing';
import { PublicGeneratorComponent } from './public-generator.component';
import defaultPolicy from '../../../../../../libs/keynest-policy/src/main/resources/policies/keynest.default.v1.json';

describe('PublicGeneratorComponent', () => {
  it('generates locally under the packaged fixed policy without persisting passwords', () => {
    const fixture = TestBed.createComponent(PublicGeneratorComponent);fixture.detectChanges();
    const storage = [spyOn(localStorage, 'setItem'), spyOn(sessionStorage, 'setItem')];
    for (let index = 0; index < 50; index++) {
      fixture.componentInstance.generate();const value = fixture.componentInstance.generated();
      expect(value.length).toBe(20);expect(value).toMatch(/[a-z]/);expect(value).toMatch(/[A-Z]/);
      expect(value).toMatch(/[2-9]/);expect(value).toMatch(/[^a-zA-Z0-9]/);expect(value).not.toMatch(/[0OIl1\s]/);
      expect(value).not.toMatch(/(.)\1{3,}/);
      for (const word of defaultPolicy.rules.bannedSubstrings) expect(value.toLowerCase()).not.toContain(word);
    }
    storage.forEach(spy => expect(spy).not.toHaveBeenCalled());
    fixture.detectChanges();expect(fixture.nativeElement.querySelector('input, select, form')).toBeNull();
    fixture.destroy();expect(fixture.componentInstance.generated()).toBe('');
  });

  it('copies only on explicit action and clears generated state on page hide', async () => {
    const fixture = TestBed.createComponent(PublicGeneratorComponent);const component = fixture.componentInstance;
    const copy = spyOn(navigator.clipboard, 'writeText').and.resolveTo();
    component.generate();const value = component.generated();expect(copy).not.toHaveBeenCalled();
    await component.copy();expect(copy).toHaveBeenCalledOnceWith(value);expect(component.status()).toBe('Password copied.');
    window.dispatchEvent(new Event('pagehide'));expect(component.generated()).toBe('');expect(component.status()).toBe('');
  });

  it('does not reinstate status after clearing a password while copy is pending', async () => {
    const fixture = TestBed.createComponent(PublicGeneratorComponent);const component = fixture.componentInstance;
    let finish!: () => void;
    spyOn(navigator.clipboard, 'writeText').and.returnValue(new Promise<void>(resolve => finish = resolve));
    component.generate();const pending = component.copy();component.clear();finish();await pending;
    expect(component.status()).toBe('');expect(component.generated()).toBe('');
  });
});
