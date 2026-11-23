import { Component, computed, input } from '@angular/core';

const paths = {
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z',
  conceal: 'm3 3 18 18 M10.5 5.1A12 12 0 0 1 12 5c6.5 0 10 7 10 7a19 19 0 0 1-3 3.9 M6.2 6.2A21 21 0 0 0 2 12s3.5 7 10 7a12 12 0 0 0 5.8-1.5 M9.9 9.9a3 3 0 0 0 4.2 4.2',
  edit: 'm15 5 4 4 M4 20l4-1 12-12a2.8 2.8 0 0 0-4-4L4 15l-1 5Z',
  rotate: 'M20 7v5h-5 M4 17v-5h5 M5.5 7a7 7 0 0 1 11.6-2L20 8 M4 16l2.9 3A7 7 0 0 0 18.5 17',
  delete: 'M3 6h18 M9 6V3h6v3 M5 6l1 15h12l1-15 M10 10v7 M14 10v7',
  previous: 'm15 5-7 7 7 7',
  next: 'm9 5 7 7-7 7',
  alert: 'M12 3 2 21h20L12 3Z M12 9v5 M12 17h.01',
  clock: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z M12 6v6l4 2',
  check: 'M22 12a10 10 0 1 1-20 0 10 10 0 0 1 20 0Z m-15 0 3 3 7-7',
  minus: 'M5 12h14',
  list: 'M8 6h13 M8 12h13 M8 18h13 M3 6h.01 M3 12h.01 M3 18h.01',
} as const;
export type IconName = keyof typeof paths;

/** Small local SVGs; no font download or third-party icon service. Labels live on controls. */
@Component({
  selector: 'kn-icon',
  host: { 'aria-hidden': 'true' },
  template: '<svg viewBox="0 0 24 24" focusable="false" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path [attr.d]="path()" /></svg>',
  styles: [':host { display: inline-flex; inline-size: 1.125rem; block-size: 1.125rem; flex: 0 0 auto; } svg { inline-size: 100%; block-size: 100%; }'],
})
export class IconComponent {
  readonly name = input.required<IconName>();
  readonly path = computed(() => paths[this.name()]);
}
