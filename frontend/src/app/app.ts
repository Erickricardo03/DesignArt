import { Component, signal } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SystemStatusBannerComponent } from './shared/components/system-status-banner.component';

@Component({
  imports: [RouterOutlet, SystemStatusBannerComponent],
  selector: 'app-root',
  styleUrl: './app.css',
  templateUrl: './app.html',
})
export class App {
  protected readonly title = signal('frontend-app');
}
