import { Directive, ElementRef, inject, Input } from '@angular/core';

@Directive({ selector: '[knPreserveDisclosureSize]' })
export class PreserveDisclosureSizeDirective {
  private readonly element = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private expanded = false;

  @Input({ required: true })
  set knPreserveDisclosureSize(expanded: boolean) {
    if (this.expanded === expanded) return;
    this.expanded = expanded;

    // Measure before Angular removes the tool or conceals its values.
    this.element.style.minBlockSize = expanded ? '' : `${this.element.offsetHeight}px`;
  }
}
