import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { HomePreviewComponent } from './home-preview.component';
import { PublicHeaderComponent } from './public-header.component';

/** Public product information only; never loads account data or secrets. */
@Component({
  selector: 'kn-home-page',
  imports: [RouterLink, MatButtonModule, HomePreviewComponent, PublicHeaderComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomePageComponent {}
