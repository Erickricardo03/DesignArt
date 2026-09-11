import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class ThemeService {
  isDarkMode = signal<boolean>(false);

  constructor() {
    const savedTheme = localStorage.getItem('designart_theme');
    if (savedTheme) {
      this.isDarkMode.set(savedTheme === 'dark');
    } else {
      this.isDarkMode.set(false); // Padrão White / Light
    }
    this.applyTheme();
  }

  toggleTheme(): void {
    this.isDarkMode.update((dark) => !dark);
    localStorage.setItem('designart_theme', this.isDarkMode() ? 'dark' : 'light');
    this.applyTheme();
  }

  private applyTheme(): void {
    if (this.isDarkMode()) {
      document.body.classList.add('dark-theme');
      document.documentElement.setAttribute('data-theme', 'dark');
    } else {
      document.body.classList.remove('dark-theme');
      document.documentElement.setAttribute('data-theme', 'light');
    }
  }
}
