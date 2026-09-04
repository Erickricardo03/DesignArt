import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class NavigationService {
  mobileSidebarOpen = signal<boolean>(false);

  toggleMobileSidebar(): void {
    this.mobileSidebarOpen.update((open) => !open);
  }

  closeMobileSidebar(): void {
    this.mobileSidebarOpen.set(false);
  }

  openMobileSidebar(): void {
    this.mobileSidebarOpen.set(true);
  }
}
