import { Component, ElementRef, input, linkedSignal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { generateBrowserPassword } from './browser-password-generator';
import { characterGroups, CharacterSelection, defaultCharacterSelection, defaultPasswordLength, maxPasswordLength, minPasswordLength } from './password-options';

@Component({
  selector: 'kn-password-generator-options',
  imports: [FormsModule],
  templateUrl: './password-generator-options.component.html',
  styleUrls: ['../page-shared.css', './password-generator-options.component.css'],
})
export class PasswordGeneratorOptionsComponent {
  private readonly keyword = viewChild.required<ElementRef<HTMLInputElement>>('keyword');
  readonly minLength = input(minPasswordLength);
  readonly maxLength = input(maxPasswordLength);
  readonly requiredGroups = input<CharacterSelection>();
  readonly minCategories = input(1);
  readonly excludeAmbiguous = input(false);
  readonly length = linkedSignal<number | null>(() => defaultPasswordLength(this.minLength(), this.maxLength()));
  readonly selection: CharacterSelection = { ...defaultCharacterSelection };
  readonly characterGroups = characterGroups;
  readonly minimumRandomLength = minPasswordLength;

  generate(): string {
    const selection = { ...this.selection };
    for (const { id } of characterGroups) {
      if (this.requiredGroups()?.[id]) selection[id] = true;
    }
    if (this.minCategories() > 1 && Object.values(selection).filter(Boolean).length < this.minCategories()) {
      throw new Error(`Choose at least ${this.minCategories()} character types for the Vault policy.`);
    }

    return generateBrowserPassword(this.length(), selection, this.keyword().nativeElement.value.trim(), {
      minLength: this.minLength(), maxLength: this.maxLength(), excludeAmbiguous: this.excludeAmbiguous(),
    });
  }
}
