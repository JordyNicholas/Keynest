import { Component, DestroyRef, ElementRef, HostListener, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatExpansionModule } from '@angular/material/expansion';
import { PreserveDisclosureSizeDirective } from '../../ui/preserve-disclosure-size.directive';
import { PublicGeneratorComponent } from './public-generator.component';
import { PublicCheckerComponent } from './public-checker.component';

type Tool = 'generate' | 'check';
@Component({
  selector: 'kn-public-tools',
  imports: [MatExpansionModule, PreserveDisclosureSizeDirective, PublicGeneratorComponent, PublicCheckerComponent],
  template: `
    <h2 id="public-tools-heading">A password tool when you need it.</h2>
    <p>Open a tool to get started. No account needed. Both use fixed default rules in this browser.</p>
    <mat-accordion [multi]="false">
      <mat-expansion-panel id="generator" [expanded]="active() === 'generate'" (opened)="open('generate')" (closed)="close('generate')">
        <mat-expansion-panel-header><mat-panel-title>Generate a password</mat-panel-title><mat-panel-description>Create and copy a fresh password</mat-panel-description></mat-expansion-panel-header>
        <div [knPreserveDisclosureSize]="active() === 'generate'">
          @if (active() === 'generate') { <kn-public-generator /> }
        </div>
      </mat-expansion-panel>
      <mat-expansion-panel id="check" [expanded]="active() === 'check'" (opened)="open('check')" (closed)="close('check')">
        <mat-expansion-panel-header><mat-panel-title>Check a password</mat-panel-title><mat-panel-description>Review the default rules locally</mat-panel-description></mat-expansion-panel-header>
        <div [knPreserveDisclosureSize]="active() === 'check'">
          @if (active() === 'check') { <kn-public-checker /> }
        </div>
      </mat-expansion-panel>
    </mat-accordion>
    <a class="back-top" href="#home-content" (click)="backToTop()">Back to top ↑</a>
  `,
  styles: [`
    :host { display: block; }
    h2 { margin: 0 0 1rem; font: 500 clamp(2rem, 4vw, 2.875rem)/1.2 var(--kn-font-display); }
    p { color: var(--kn-color-muted); }
    mat-expansion-panel { margin-block: 0.75rem; border: 1px solid var(--kn-color-outline); border-radius: 1rem !important; box-shadow: none !important; background: var(--kn-color-surface); }
    mat-expansion-panel-header { min-block-size: 5rem; height: auto; }
    mat-panel-title { color: var(--kn-color-ink); font-size: 1.125rem; }
    mat-panel-description { color: var(--kn-color-muted); }
    #generator, #check { scroll-margin-block-start: 1rem; }
    .back-top { display: inline-flex; align-items: center; min-block-size: 2.75rem; margin-block-start: 1rem; color: var(--kn-color-accent); text-underline-offset: 0.25em; }
    @media (max-width: 40rem) { mat-panel-description { display: none; } }
  `],
})
export class PublicToolsComponent {
  readonly active = signal<Tool | null>(null);
  private readonly generator = viewChild(PublicGeneratorComponent);
  private readonly checker = viewChild(PublicCheckerComponent);
  private readonly host: ElementRef<HTMLElement> = inject(ElementRef);
  private readonly destroy = inject(DestroyRef);
  private frame?: number;

  constructor() {
    inject(ActivatedRoute).fragment.pipe(takeUntilDestroyed()).subscribe(fragment => {
      if (fragment === 'generator') this.open('generate', true);
      else if (fragment === 'check') this.open('check', true);
    });
    this.destroy.onDestroy(() => { if (this.frame !== undefined) cancelAnimationFrame(this.frame);this.clearValues(); });
  }
  open(tool: Tool, scroll = false): void {
    if (this.active() !== tool) { this.clearValues();this.active.set(tool); }
    if (scroll) {
      if (this.frame !== undefined) cancelAnimationFrame(this.frame);
      this.frame = requestAnimationFrame(() => {
        if (this.destroy.destroyed || this.active() !== tool) return;
        const header = this.host.nativeElement.querySelector<HTMLElement>(tool === 'generate' ? '#generator mat-expansion-panel-header' : '#check mat-expansion-panel-header');
        header?.scrollIntoView({ block: 'start', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
        header?.focus({ preventScroll: true });
      });
    }
  }
  close(tool: Tool): void { if (this.active() === tool) { this.clearValues();this.active.set(null); } }
  backToTop(): void { this.clearValues();this.active.set(null); }
  private clearValues(): void { this.generator()?.clear();this.checker()?.clear(); }
  @HostListener('document:visibilitychange') onVisibilityChange(): void { if (document.hidden) this.backToTop(); }
  @HostListener('window:pagehide') onPageHide(): void { this.backToTop(); }
}
