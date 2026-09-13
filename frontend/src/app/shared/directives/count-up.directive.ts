import { Directive, ElementRef, Input, OnChanges, OnDestroy, SimpleChanges, inject } from '@angular/core';

/**
 * Anima um número contando do valor anterior até o novo valor.
 * Uso: <span [appCountUp]="stats()?.aFazer || 0"></span>
 */
@Directive({
  selector: '[appCountUp]',
  standalone: true,
})
export class CountUpDirective implements OnChanges, OnDestroy {
  @Input('appCountUp') value = 0;
  @Input() countUpDuration = 800;

  private el = inject(ElementRef<HTMLElement>);
  private rafId?: number;
  private isFirst = true;
  private displayed = 0;

  ngOnChanges(changes: SimpleChanges): void {
    if (!changes['value']) {
      return;
    }

    const to = Number(this.value) || 0;
    const from = this.isFirst ? 0 : this.displayed;
    this.isFirst = false;

    const reduceMotion =
      typeof window !== 'undefined' &&
      window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;

    if (reduceMotion || from === to) {
      this.render(to);
      return;
    }

    this.animate(from, to);
  }

  ngOnDestroy(): void {
    if (this.rafId) {
      cancelAnimationFrame(this.rafId);
    }
  }

  private animate(from: number, to: number): void {
    if (this.rafId) {
      cancelAnimationFrame(this.rafId);
    }
    const duration = this.countUpDuration;
    const start = performance.now();

    const step = (now: number) => {
      const progress = Math.min(1, (now - start) / duration);
      const eased = 1 - Math.pow(1 - progress, 3);
      const current = Math.round(from + (to - from) * eased);
      this.render(current);
      if (progress < 1) {
        this.rafId = requestAnimationFrame(step);
      }
    };
    this.rafId = requestAnimationFrame(step);
  }

  private render(n: number): void {
    this.displayed = n;
    this.el.nativeElement.textContent = String(n);
  }
}
