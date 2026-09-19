import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ThemeService } from '../../core/services/theme.service';
import { ApiService } from '../../core/services/api.service';
import { Avaliacao } from '../../core/models';
import { RevealOnScrollDirective } from '../../shared/directives/reveal-on-scroll.directive';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterModule, RevealOnScrollDirective],
  template: `
    <div class="landing-page">
      <!-- Navbar Pública -->
      <header class="public-nav">
        <div class="nav-container">
          <div class="logo-area">
            <img src="/logo-da.png" alt="Design Arte Logo" class="brand-logo-img" />
            <div class="brand-text">
              <span class="logo-title">DesignArte</span>
              <span class="logo-tag">AGÊNCIA CRIATIVA & OPERACIONAL</span>
            </div>
          </div>

          <div class="nav-right">
            <button class="theme-toggle" (click)="themeService.toggleTheme()">
              <i class="bi" [ngClass]="themeService.isDarkMode() ? 'bi-moon-stars-fill' : 'bi-sun-fill'"></i>
            </button>
            <a routerLink="/login" class="btn-internal-portal">
              <i class="bi bi-shield-lock-fill"></i> Painel Interno
            </a>
          </div>
        </div>
      </header>

      <!-- Hero Section -->
      <section class="hero-section">
        <div class="hero-aurora" aria-hidden="true">
          <span class="aurora-blob blob-1"></span>
          <span class="aurora-blob blob-2"></span>
          <span class="aurora-blob blob-3"></span>
        </div>

        <div class="hero-content">
          <div class="hero-logo-box hero-fade-in">
            <img src="/logo-da.png" alt="Design Arte" class="hero-logo" />
          </div>

          <span class="hero-tag hero-fade-in" style="animation-delay: 0.08s">AGÊNCIA CRIATIVA & OPERACIONAL</span>
          <h1 class="hero-title hero-fade-in" style="animation-delay: 0.16s">Design Arte</h1>
          <p class="hero-description hero-fade-in" style="animation-delay: 0.24s">
            Cada projeto é cuidadosamente elaborado, com detalhes minuciosos e uma mistura de técnicas autênticas.
          </p>

          <div class="hero-cta-group hero-fade-in" style="animation-delay: 0.32s">
            <a href="https://wa.me/5582999999999" target="_blank" class="btn-cta-contact">
              Entrar em contato <i class="bi bi-arrow-right"></i>
            </a>
            <a routerLink="/login" class="btn-cta-panel">
              Painel Interno
            </a>
          </div>
        </div>
      </section>

      <!-- SEÇÃO CASES (Exatamente como em 16701.jpg) -->
      <section class="section-container">
        <span class="section-badge-purple" appReveal>CASES</span>
        <h2 class="section-title-large" appReveal [appRevealDelay]="60">Resultados com acabamento, ritmo e entrega.</h2>

        <div class="cases-grid">
          <div class="case-card" appReveal>
            <div class="case-client-badge">
              <i class="bi bi-check-circle-fill text-purple"></i>
              <span>Aurora Beauty</span>
            </div>
            <h3 class="case-metric">+68% em pedidos via Instagram</h3>
            <p class="case-desc">Reposicionamento visual e social media</p>
          </div>

          <div class="case-card" appReveal [appRevealDelay]="100">
            <div class="case-client-badge">
              <i class="bi bi-check-circle-fill text-purple"></i>
              <span>Studio Forma</span>
            </div>
            <h3 class="case-metric">3x mais leads qualificados</h3>
            <p class="case-desc">Campanhas locais e vídeos curtos</p>
          </div>

          <div class="case-card" appReveal [appRevealDelay]="200">
            <div class="case-client-badge">
              <i class="bi bi-check-circle-fill text-purple"></i>
              <span>Casa Nativa</span>
            </div>
            <h3 class="case-metric">+41% em recorrência mensal</h3>
            <p class="case-desc">Branding, fotos e calendário editorial</p>
          </div>
        </div>
      </section>

      <!-- SEÇÃO SERVIÇOS (Exatamente como em 16702.jpg) -->
      <section class="section-container">
        <span class="section-badge-purple">SERVIÇOS</span>
        <h2 class="section-title-large">Equipe completa para destacar a sua marca.</h2>

        <div class="services-carousel-grid">
          <div class="service-card-item" appReveal>
            <div class="service-icon-box"><i class="bi bi-palette"></i></div>
            <h3 class="service-name">Identidade Visual</h3>
            <p class="service-text">Direção criativa, logotipos, guias de marca e peças para campanhas de impacto.</p>
          </div>

          <div class="service-card-item" appReveal [appRevealDelay]="60">
            <div class="service-icon-box"><i class="bi bi-laptop"></i></div>
            <h3 class="service-name">Redes Sociais</h3>
            <p class="service-text">Calendário editorial estratégico, design premium, copy, publicação e inteligência.</p>
          </div>

          <div class="service-card-item" appReveal [appRevealDelay]="120">
            <div class="service-icon-box"><i class="bi bi-film"></i></div>
            <h3 class="service-name">Vídeos e Roteiros</h3>
            <p class="service-text">Roteiro, captação cinemática, edição dinâmica e entregas otimizadas por plataforma.</p>
          </div>

          <div class="service-card-item" appReveal [appRevealDelay]="180">
            <div class="service-icon-box"><i class="bi bi-camera"></i></div>
            <h3 class="service-name">Fotografia</h3>
            <p class="service-text">Ensaios corporativos, produtos, eventos e materiais visuais para posicionamento.</p>
          </div>

          <div class="service-card-item" appReveal [appRevealDelay]="240">
            <div class="service-icon-box"><i class="bi bi-megaphone"></i></div>
            <h3 class="service-name">Marketing Digital</h3>
            <p class="service-text">Campanhas de tráfego, funis de conversão, criativos de alta performance e escala.</p>
          </div>
        </div>
      </section>

      <!-- SEÇÃO AVALIAÇÕES (Exatamente como em 16700.jpg) -->
      <section class="section-container" *ngIf="avaliacoes().length">
        <span class="section-badge-purple">AVALIAÇÕES</span>
        <h2 class="section-title-large">Quem trabalha conosco, confia e recomenda.</h2>

        <div class="testimonials-grid">
          <div class="testimonial-card" *ngFor="let avaliacao of avaliacoes(); let i = index" appReveal [appRevealDelay]="i * 80">
            <div class="stars-row">
              <i class="bi bi-star-fill" *ngFor="let estrela of getEstrelas(avaliacao.nota)"></i>
            </div>
            <p class="testimonial-text">
              "{{ avaliacao.texto }}"
            </p>
            <div class="testimonial-author">
              <strong>{{ avaliacao.clienteNome }}</strong>
              <span *ngIf="avaliacao.cargoEmpresa">{{ avaliacao.cargoEmpresa }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- SEÇÃO CLIENTES: esteira animada com as marcas reais atendidas -->
      <section class="section-container clients-section">
        <span class="section-badge-purple" appReveal>CLIENTES</span>
        <h2 class="section-title-large" appReveal [appRevealDelay]="60">Marcas que confiam no nosso trabalho.</h2>
      </section>

      <section class="clients-marquee-wrap" appReveal [appRevealDelay]="100">
        <div class="marquee-fade fade-left"></div>
        <div class="marquee-fade fade-right"></div>
        <div class="marquee-track">
          <div class="marquee-group">
            <div class="client-logo-card" *ngFor="let cliente of clientLogos()">
              <img [src]="cliente.src" [alt]="cliente.name" loading="lazy" />
            </div>
          </div>
          <div class="marquee-group" aria-hidden="true">
            <div class="client-logo-card" *ngFor="let cliente of clientLogos()">
              <img [src]="cliente.src" [alt]="cliente.name" loading="lazy" />
            </div>
          </div>
        </div>
      </section>

      <!-- Footer -->
      <footer class="public-footer">
        <div class="footer-container">
          <div class="footer-left">
            <div class="footer-logo">
              <img src="/logo-da.png" alt="Design Arte Logo" />
              <span>DesignArte</span>
            </div>
            <p>Agência Criativa & Operacional especializada em posicionamento, audiovisual e performance de marcas.</p>
          </div>
          <div class="footer-right">
            <a href="https://instagram.com" target="_blank"><i class="bi bi-instagram"></i></a>
            <a href="https://youtube.com" target="_blank"><i class="bi bi-youtube"></i></a>
            <a href="https://wa.me/5582999999999" target="_blank"><i class="bi bi-whatsapp"></i></a>
          </div>
        </div>
        <div class="footer-bottom-copy">
          © 2026 Design Arte • Todos os direitos reservados.
        </div>
      </footer>
    </div>
  `,
  styles: [`
    .landing-page {
      min-height: 100vh;
      background: var(--bg-primary);
      color: var(--text-primary);
      font-family: 'Outfit', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    }
    .public-nav {
      position: sticky;
      top: 0;
      background: var(--card-bg);
      border-bottom: 1px solid var(--border-color);
      z-index: 100;
      backdrop-filter: blur(10px);
    }
    .nav-container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 0.85rem 1.5rem;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .logo-area {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }
    .brand-logo-img {
      width: 42px;
      height: 42px;
      object-fit: contain;
      border-radius: 8px;
    }
    .brand-text {
      display: flex;
      flex-direction: column;
    }
    .logo-title {
      font-size: 1.15rem;
      font-weight: 900;
      color: var(--text-primary);
      line-height: 1.2;
    }
    .logo-tag {
      font-size: 0.65rem;
      font-weight: 800;
      color: var(--primary);
      letter-spacing: 0.08em;
    }
    .nav-right {
      display: flex;
      align-items: center;
      gap: 1rem;
    }
    .theme-toggle {
      background: none;
      border: 1px solid var(--border-color);
      border-radius: 50%;
      width: 36px;
      height: 36px;
      display: flex;
      align-items: center;
      justify-content: center;
      color: var(--text-primary);
      cursor: pointer;
      transition: transform 0.4s cubic-bezier(0.34, 1.56, 0.64, 1), background 0.2s ease;
    }
    .theme-toggle:hover {
      background: var(--bg-surface-elevated);
      transform: rotate(20deg);
    }
    .btn-internal-portal {
      background: #1e293b;
      color: #fff;
      padding: 0.55rem 1.1rem;
      border-radius: 8px;
      font-weight: 800;
      font-size: 0.82rem;
      text-decoration: none;
      display: flex;
      align-items: center;
      gap: 0.4rem;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }
    .btn-internal-portal:hover {
      transform: translateY(-2px);
      box-shadow: 0 6px 16px rgba(30, 41, 59, 0.3);
    }

    /* Hero */
    .hero-section {
      padding: 4rem 1.5rem 3rem 1.5rem;
      text-align: center;
      display: flex;
      justify-content: center;
      position: relative;
      overflow: hidden;
    }
    .hero-content {
      max-width: 700px;
      display: flex;
      flex-direction: column;
      align-items: center;
      position: relative;
      z-index: 1;
    }
    .hero-fade-in {
      opacity: 0;
      animation: heroFadeUp 0.7s cubic-bezier(0.16, 1, 0.3, 1) forwards;
    }
    @keyframes heroFadeUp {
      from { opacity: 0; transform: translateY(18px); }
      to { opacity: 1; transform: translateY(0); }
    }

    /* Fundo decorativo com blobs animados (tema "estúdio criativo") */
    .hero-aurora {
      position: absolute;
      inset: 0;
      overflow: hidden;
      pointer-events: none;
      z-index: 0;
    }
    .aurora-blob {
      position: absolute;
      border-radius: 50%;
      filter: blur(60px);
      opacity: 0.25;
      animation: auroraDrift 14s ease-in-out infinite alternate;
    }
    .blob-1 {
      width: 320px;
      height: 320px;
      background: #7c3aed;
      top: -100px;
      left: -60px;
      animation-duration: 16s;
    }
    .blob-2 {
      width: 280px;
      height: 280px;
      background: #06b6d4;
      top: 20px;
      right: -80px;
      animation-duration: 19s;
      animation-delay: -4s;
    }
    .blob-3 {
      width: 240px;
      height: 240px;
      background: #f59e0b;
      bottom: -120px;
      left: 40%;
      animation-duration: 21s;
      animation-delay: -8s;
    }
    @keyframes auroraDrift {
      from { transform: translate(0, 0) scale(1); }
      to { transform: translate(30px, 20px) scale(1.15); }
    }
    .hero-logo-box {
      margin-bottom: 1.5rem;
    }
    .hero-logo {
      width: 90px;
      height: 90px;
      object-fit: contain;
      border-radius: 18px;
      box-shadow: 0 10px 30px rgba(124, 58, 237, 0.25);
    }
    .hero-tag {
      font-size: 0.78rem;
      font-weight: 800;
      letter-spacing: 0.12em;
      color: #7c3aed;
      text-transform: uppercase;
      margin-bottom: 0.5rem;
    }
    .hero-title {
      font-size: 2.8rem;
      font-weight: 900;
      color: var(--text-primary);
      margin: 0 0 1rem 0;
      letter-spacing: -0.03em;
    }
    .hero-description {
      font-size: 1.05rem;
      color: var(--text-secondary);
      line-height: 1.6;
      margin: 0 0 2rem 0;
    }
    .hero-cta-group {
      display: flex;
      gap: 1rem;
      flex-wrap: wrap;
      justify-content: center;
    }
    .btn-cta-contact {
      background: #7c3aed;
      color: #fff;
      padding: 0.85rem 1.75rem;
      border-radius: 10px;
      font-weight: 800;
      font-size: 0.95rem;
      text-decoration: none;
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      box-shadow: 0 4px 18px rgba(124, 58, 237, 0.4);
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }
    .btn-cta-contact:hover {
      transform: translateY(-3px);
      box-shadow: 0 8px 24px rgba(124, 58, 237, 0.5);
    }
    .btn-cta-contact i {
      transition: transform 0.2s ease;
    }
    .btn-cta-contact:hover i {
      transform: translateX(4px);
    }
    .btn-cta-panel {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      color: var(--text-primary);
      padding: 0.85rem 1.75rem;
      border-radius: 10px;
      font-weight: 800;
      font-size: 0.95rem;
      text-decoration: none;
      transition: transform 0.2s ease, border-color 0.2s ease;
    }
    .btn-cta-panel:hover {
      transform: translateY(-3px);
      border-color: #7c3aed;
    }

    /* Seções Comuns */
    .section-container {
      max-width: 1200px;
      margin: 0 auto;
      padding: 3rem 1.5rem;
    }
    .section-badge-purple {
      font-size: 0.72rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      color: #7c3aed;
      text-transform: uppercase;
      display: block;
      margin-bottom: 0.4rem;
    }
    .section-title-large {
      font-size: 1.85rem;
      font-weight: 900;
      color: var(--text-primary);
      margin: 0 0 2rem 0;
      letter-spacing: -0.02em;
    }

    /* Cases 16701.jpg */
    .cases-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 1.5rem;
    }
    .case-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.75rem;
      box-shadow: 0 4px 16px rgba(0,0,0,0.03);
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
    }
    .case-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 12px 28px rgba(124, 58, 237, 0.12);
      border-color: rgba(124, 58, 237, 0.35);
    }
    .case-client-badge {
      display: flex;
      align-items: center;
      gap: 0.45rem;
      font-size: 0.85rem;
      font-weight: 800;
      color: var(--text-primary);
    }
    .case-metric {
      font-size: 1.35rem;
      font-weight: 900;
      color: #7c3aed;
      margin: 0.25rem 0;
    }
    .case-desc {
      font-size: 0.82rem;
      color: var(--text-secondary);
      margin: 0;
    }

    /* Serviços 16702.jpg */
    .services-carousel-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 1.25rem;
    }
    .service-card-item {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.5rem;
      box-shadow: 0 4px 16px rgba(0,0,0,0.03);
      display: flex;
      flex-direction: column;
      gap: 0.65rem;
      transition: transform 0.25s ease, box-shadow 0.25s ease, border-color 0.25s ease;
    }
    .service-card-item:hover {
      transform: translateY(-4px);
      box-shadow: 0 12px 28px rgba(124, 58, 237, 0.12);
      border-color: rgba(124, 58, 237, 0.35);
    }
    .service-card-item:hover .service-icon-box {
      transform: scale(1.1) rotate(-4deg);
      background: rgba(124, 58, 237, 0.16);
    }
    .service-icon-box {
      width: 44px;
      height: 44px;
      border-radius: 10px;
      background: rgba(124, 58, 237, 0.08);
      color: #7c3aed;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.25rem;
      transition: transform 0.25s ease, background 0.25s ease;
    }
    .service-name {
      font-size: 0.98rem;
      font-weight: 800;
      color: var(--text-primary);
      margin: 0.2rem 0 0 0;
    }
    .service-text {
      font-size: 0.78rem;
      color: var(--text-secondary);
      line-height: 1.45;
      margin: 0;
    }

    /* Avaliações 16700.jpg */
    .testimonials-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 1.5rem;
    }
    .testimonial-card {
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 14px;
      padding: 1.75rem;
      box-shadow: 0 4px 16px rgba(0,0,0,0.03);
      display: flex;
      flex-direction: column;
      gap: 1rem;
      transition: transform 0.25s ease, box-shadow 0.25s ease;
    }
    .testimonial-card:hover {
      transform: translateY(-4px);
      box-shadow: 0 12px 28px rgba(0, 0, 0, 0.08);
    }
    .stars-row {
      color: #f59e0b;
      display: flex;
      gap: 0.25rem;
      font-size: 0.9rem;
    }
    .testimonial-text {
      font-size: 0.85rem;
      color: var(--text-secondary);
      line-height: 1.5;
      margin: 0;
      flex: 1;
    }
    .testimonial-author strong {
      display: block;
      font-size: 0.88rem;
      color: var(--text-primary);
    }
    .testimonial-author span {
      font-size: 0.75rem;
      color: var(--text-secondary);
    }

    /* Clientes: esteira animada */
    .clients-section {
      padding-bottom: 1.5rem;
    }
    .clients-marquee-wrap {
      position: relative;
      width: 100%;
      overflow: hidden;
      padding: 0.5rem 0 3rem 0;
    }
    .marquee-fade {
      position: absolute;
      top: 0;
      bottom: 0;
      width: 90px;
      max-width: 15%;
      z-index: 2;
      pointer-events: none;
    }
    .fade-left {
      left: 0;
      background: linear-gradient(90deg, var(--bg-primary), transparent);
    }
    .fade-right {
      right: 0;
      background: linear-gradient(270deg, var(--bg-primary), transparent);
    }
    .marquee-track {
      display: flex;
      width: max-content;
      animation: marqueeSlide 60s linear infinite;
    }
    .marquee-track:hover {
      animation-play-state: paused;
    }
    .marquee-group {
      display: flex;
      align-items: center;
      gap: 1.25rem;
      padding-right: 1.25rem;
    }
    @keyframes marqueeSlide {
      from { transform: translateX(0); }
      to { transform: translateX(-50%); }
    }
    .client-logo-card {
      flex: 0 0 auto;
      width: 260px;
      height: 150px;
      background: var(--card-bg);
      border: 1px solid var(--border-color);
      border-radius: 16px;
      padding: 1.75rem;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(0,0,0,0.02);
      transition: transform 0.25s ease, filter 0.25s ease, box-shadow 0.25s ease;
      filter: grayscale(0.5);
    }
    .client-logo-card:hover {
      transform: translateY(-3px) scale(1.05);
      filter: grayscale(0);
      box-shadow: 0 10px 22px rgba(124, 58, 237, 0.12);
    }
    .client-logo-card img {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
    }
    @media (max-width: 576px) {
      .client-logo-card {
        width: 190px;
        height: 110px;
        padding: 1.15rem;
      }
      .marquee-fade {
        width: 40px;
      }
    }

    /* Footer */
    .public-footer {
      background: #0f172a;
      color: #94a3b8;
      padding: 3rem 1.5rem 1.5rem 1.5rem;
      border-top: 1px solid #1e293b;
    }
    .footer-container {
      max-width: 1200px;
      margin: 0 auto;
      display: flex;
      justify-content: space-between;
      align-items: center;
      flex-wrap: wrap;
      gap: 2rem;
      margin-bottom: 2rem;
    }
    .footer-logo {
      display: flex;
      align-items: center;
      gap: 0.6rem;
      margin-bottom: 0.5rem;
    }
    .footer-logo img {
      width: 32px;
      height: 32px;
      border-radius: 6px;
    }
    .footer-logo span {
      font-size: 1.1rem;
      font-weight: 800;
      color: #ffffff;
    }
    .footer-left p {
      font-size: 0.82rem;
      max-width: 400px;
      margin: 0;
    }
    .footer-right {
      display: flex;
      gap: 1.25rem;
      font-size: 1.35rem;
    }
    .footer-right a {
      color: #94a3b8;
      transition: color 0.2s;
    }
    .footer-right a:hover {
      color: #ffffff;
    }
    .footer-bottom-copy {
      text-align: center;
      font-size: 0.75rem;
      color: #64748b;
      padding-top: 1.5rem;
      border-top: 1px solid #1e293b;
    }
    .text-purple { color: #7c3aed; }
  `]
})
export class LandingComponent implements OnInit {
  themeService = inject(ThemeService);
  private api = inject(ApiService);

  avaliacoes = signal<Avaliacao[]>([]);

  ngOnInit(): void {
    // Multi-tenant (Fase 3): a API exige login, então visitante anônimo recebe
    // 401 e a seção de depoimentos simplesmente não aparece (landing pública
    // por tenant/domínio fica para uma fase futura).
    this.api.getAvaliacoes(true).subscribe({
      next: (res) => this.avaliacoes.set(res || []),
      error: () => this.avaliacoes.set([]),
    });
    this.carregarClientesReais();
  }

  getEstrelas(nota: number): number[] {
    const total = Math.max(0, Math.min(5, Math.round(nota || 0)));
    return Array(total).fill(0);
  }

  readonly fallbackClientLogos = [
    { src: '/clients/panificacao-pf.png', name: 'Panificação P&F' },
    { src: '/clients/lucas-companheiro.png', name: 'Lucas Companheiro' },
    { src: '/clients/clinica-farmacia.png', name: 'Clínica & Farmácia' },
    { src: '/clients/jm-moda-fitness.png', name: 'JM Moda Fitness' },
    { src: '/clients/supermercado-o-favorito.png', name: 'Supermercado O Favorito' },
    { src: '/clients/sr-junior.png', name: 'Sr. Junior Moda Masculina' },
    { src: '/clients/ki-delicia.png', name: 'Ki-Delícia Lanchonete e Pizzaria' },
    { src: '/clients/stilosa-modas.png', name: 'Stilosa Modas' },
    { src: '/clients/cicero-companheiro.png', name: 'Vereador Cícero Companheiro' },
    { src: '/clients/kf-monograma.png', name: 'KF' },
    { src: '/clients/secretaria-saude-joaquim-gomes.png', name: 'Secretaria de Saúde de Joaquim Gomes' },
    { src: '/clients/fernanda-kelly.png', name: 'Fernanda Kelly' },
    { src: '/clients/af-suplementos.png', name: 'AF Suplementos' },
    { src: '/clients/marcelino-imports.png', name: 'Marcelino Imports' },
    { src: '/clients/academia-titanium.png', name: 'Academia Titanium' },
    { src: '/clients/vivamais.png', name: 'VivaMais Mercado Natural' },
    { src: '/clients/dg-serralheria.png', name: 'DG Serralheria' },
    { src: '/clients/sao-joao-supermercado.png', name: 'Novo São João Supermercado' },
  ];

  clientLogos = signal(this.fallbackClientLogos);

  private carregarClientesReais(): void {
    this.api.getClientes().subscribe({
      next: (clientes) => {
        const comLogo = (clientes || [])
          .filter((c) => !!c.logoUrl)
          .map((c) => ({ src: c.logoUrl as string, name: c.nome }));

        // Mantém a vitrine com os clientes reais cadastrados no painel; se ainda
        // não houver nenhum com logo, mostra a lista de exemplo como fallback.
        if (comLogo.length) {
          this.clientLogos.set(comLogo);
        }
      },
      // Visitante anônimo (401): mantém a lista estática de fallback.
      error: () => {},
    });
  }
}
