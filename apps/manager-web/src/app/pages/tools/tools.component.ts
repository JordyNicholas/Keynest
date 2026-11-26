import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatExpansionModule } from '@angular/material/expansion';
import { ActivatedRoute, Router } from '@angular/router';
import { PreserveDisclosureSizeDirective } from '../../ui/preserve-disclosure-size.directive';
import { GeneratePasswordPageComponent } from './generate-password.component';
import { CheckPasswordPageComponent } from './check-password.component';

type Tool = 'generate' | 'check';

@Component({
  selector: 'kn-tools-page',
  imports: [MatExpansionModule, PreserveDisclosureSizeDirective, GeneratePasswordPageComponent, CheckPasswordPageComponent],
  templateUrl: './tools.component.html',
  styleUrls: ['../page-shared.css', './tools.component.css'],
})
export class ToolsPageComponent {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  readonly active = signal<Tool | null>(null);
  private readonly info = this.router.currentNavigation()?.extras.info as { policyExplanation?: unknown } | undefined;
  readonly explanation = typeof this.info?.policyExplanation === 'string' ? this.info.policyExplanation : '';

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(params => {
      const tool = params.get('tool');
      this.active.set(tool === 'generate' || tool === 'check' ? tool : null);
    });
  }

  open(tool: Tool): void {
    if (this.active() === tool) return;
    this.active.set(tool);
    this.navigate(tool);
  }

  close(tool: Tool): void {
    if (this.active() !== tool) return;
    this.active.set(null);
    this.navigate(null);
  }

  private navigate(tool: Tool | null): void {
    void this.router.navigate([], { relativeTo: this.route, queryParams: { tool },
      queryParamsHandling: 'merge', fragment: tool ?? undefined, replaceUrl: true });
  }
}
