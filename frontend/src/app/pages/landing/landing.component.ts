import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { ThemeService } from '../../core/services/theme.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
    <div class="landing-page">
      <!-- Navbar -->
      <header class="landing-nav">
        <div class="nav-container">
          <div class="brand">
            <div class="brand-badge">DA</div>
            <div class="brand-text">
              <span class="agency-tag">AGÊNCIA CRIATIVA & OPERACIONAL</span>
              <h1>Design Arte</h1>
            </div>
          </div>

          <div class="nav-actions">
            <button class="theme-btn" (click)="themeService.toggleTheme()" [title]="themeService.isDarkMode() ? 'Modo Claro' : 'Modo Escuro'">
              <i class="bi" [ngClass]="themeService.isDarkMode() ? 'bi-moon-stars-fill' : 'bi-sun-fill'"></i>
            </button>
            <a routerLink="/login" class="btn btn-secondary">
              <i class="bi bi-person-circle"></i>
              <span>Painel Interno</span>
            </a>
          </div>
        </div>
      </header>

      <!-- Hero Section -->
      <section class="hero-section">
        <div class="hero-glow"></div>
        <div class="hero-content">
          <div class="badge-pill">
            <i class="bi bi-stars"></i>
            <span>PRODUÇÃO AUDIOVISUAL & ESTRATÉGIA DIGITAL</span>
          </div>

          <h1 class="hero-title">
            Design Arte
          </h1>

          <p class="hero-description">
            Cada projeto é cuidadosamente elaborado, com detalhes minuciosos e uma mistura de técnicas autênticas.
          </p>

          <div class="hero-cta-group">
            <a href="https://wa.me/5582999999999?text=Olá!%20Gostaria%20de%20solicitar%20uma%20proposta%20com%20a%20Design%20Arte" target="_blank" class="btn btn-primary btn-lg">
              <span>Entrar em contato</span>
              <i class="bi bi-arrow-right"></i>
            </a>
            <a routerLink="/login" class="btn btn-secondary btn-lg">
              <i class="bi bi-lock-fill"></i>
              <span>Painel Interno</span>
            </a>
          </div>
        </div>
      </section>

      <!-- Grid de Soluções -->
      <section class="features-section">
        <div class="container">
          <div class="section-heading text-center">
            <span class="sub-badge">NOSSOS SERVIÇOS</span>
            <h2>Soluções completas para potencializar a sua marca</h2>
          </div>

          <div class="features-grid">
            <div class="feature-card">
              <div class="icon-box bg-purple">
                <i class="bi bi-film"></i>
              </div>
              <h3>Produção & Roteiros</h3>
              <p>Roteirização criativa, gravação no set com equipe qualificada e direção artística em 4K.</p>
            </div>

            <div class="feature-card">
              <div class="icon-box bg-teal">
                <i class="bi bi-camera-fill"></i>
              </div>
              <h3>Cobertura de Eventos</h3>
              <p>Fotografia e filmagem de eventos esportivos e corporativos com galeria e entrega rápida.</p>
            </div>

            <div class="feature-card">
              <div class="icon-box bg-orange">
                <i class="bi bi-bullseye"></i>
              </div>
              <h3>Marketing & Conteúdo</h3>
              <p>Gestão de redes sociais, Reels dinâmicos, Stories e planejamento estratégico mensal.</p>
            </div>

            <div class="feature-card">
              <div class="icon-box bg-blue">
                <i class="bi bi-palette-fill"></i>
              </div>
              <h3>Design & Identidade</h3>
              <p>Criação de marcas autênticas, logotipos, artes para feed e materiais promocionais.</p>
            </div>
          </div>
        </div>
      </section>

      <!-- Seção CTA / WhatsApp (Página 8 do PDF) -->
      <section class="contact-banner-section">
        <div class="container">
          <div class="contact-banner-card">
            <div class="banner-text">
              <span class="banner-tag">VAMOS COMEÇAR?</span>
              <h2>Desenhe o próximo movimento da sua marca.</h2>
              <p>
                Conte-nos sobre o momento atual da sua empresa e receba uma proposta personalizada com escopo completo, prazos e prioridades estratégicas de crescimento.
              </p>
              
              <div class="social-links-row">
                <a href="https://instagram.com" target="_blank" class="social-chip">
                  <i class="bi bi-instagram"></i>
                  <span>Instagram</span>
                </a>
                <a href="https://tiktok.com" target="_blank" class="social-chip">
                  <i class="bi bi-tiktok"></i>
                  <span>TikTok</span>
                </a>
                <a href="https://youtube.com" target="_blank" class="social-chip">
                  <i class="bi bi-youtube"></i>
                  <span>YouTube</span>
                </a>
              </div>
            </div>

            <div class="banner-action">
              <a href="https://wa.me/5582999999999?text=Olá!%20Gostaria%20de%20iniciar%20um%20projeto%20com%20a%20Design%20Arte" target="_blank" class="btn btn-whatsapp">
                <i class="bi bi-whatsapp"></i>
                <span>Chamar no WhatsApp</span>
              </a>
            </div>
          </div>
        </div>
      </section>

      <!-- Footer -->
      <footer class="landing-footer">
        <div class="container">
          <div class="footer-bottom">
            <p>&copy; 2026 Design Arte • Agência Criativa & Operacional. Todos os direitos reservados.</p>
            <div class="footer-links">
              <a routerLink="/login">Acesso Restrito</a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  `,
  styles: [`
    .landing-page {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      background-color: var(--bg-app);
      color: var(--text-primary);
      width: 100%;
      max-width: 100vw;
      overflow-x: hidden;
    }

    .container {
      max-width: 1200px;
      width: 100%;
      margin: 0 auto;
      padding: 0 1.25rem;
      box-sizing: border-box;
    }

    .landing-nav {
      position: sticky;
      top: 0;
      z-index: 1000;
      background: var(--glass-bg);
      backdrop-filter: var(--glass-blur);
      border-bottom: 1px solid var(--glass-border);
      padding: 0.85rem 0;
      width: 100%;
      max-width: 100vw;
      box-sizing: border-box;
    }

    .nav-container {
      max-width: 1200px;
      width: 100%;
      margin: 0 auto;
      padding: 0 1.25rem;
      display: flex;
      align-items: center;
      justify-content: space-between;
      box-sizing: border-box;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 0.85rem;
    }

    .brand-badge {
      width: 42px;
      height: 42px;
      border-radius: var(--radius-md);
      background: var(--color-primary-gradient);
      color: white;
      font-weight: 900;
      font-size: 1.2rem;
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 12px rgba(99, 102, 241, 0.35);
      flex-shrink: 0;
    }

    .agency-tag {
      font-size: 0.65rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      color: var(--color-primary);
      text-transform: uppercase;
    }

    .brand-text h1 {
      font-size: 1.25rem;
      margin: 0;
      line-height: 1.1;
    }

    .nav-actions {
      display: flex;
      align-items: center;
      gap: 0.75rem;
    }

    .theme-btn {
      width: 40px;
      height: 40px;
      border-radius: var(--radius-md);
      border: 1px solid var(--border-color);
      background: var(--bg-surface-elevated);
      color: var(--text-primary);
      cursor: pointer;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.1rem;
      transition: all 0.2s;
      flex-shrink: 0;
    }

    .theme-btn:hover {
      background: var(--bg-surface-hover);
    }

    /* Hero */
    .hero-section {
      position: relative;
      padding: 5rem 1.25rem 4rem;
      text-align: center;
      display: flex;
      justify-content: center;
      overflow: hidden;
      width: 100%;
      max-width: 100vw;
      box-sizing: border-box;
    }

    .hero-glow {
      position: absolute;
      top: -100px;
      left: 50%;
      transform: translateX(-50%);
      width: 500px;
      max-width: 100vw;
      height: 500px;
      background: radial-gradient(circle, rgba(99, 102, 241, 0.25) 0%, transparent 70%);
      filter: blur(50px);
      pointer-events: none;
      z-index: 0;
    }

    .hero-content {
      position: relative;
      z-index: 1;
      max-width: 800px;
      width: 100%;
      display: flex;
      flex-direction: column;
      align-items: center;
      box-sizing: border-box;
    }

    .badge-pill {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.4rem 0.85rem;
      background: var(--color-primary-light);
      border: 1px solid rgba(99, 102, 241, 0.3);
      border-radius: var(--radius-full);
      color: var(--color-primary);
      font-size: 0.725rem;
      font-weight: 700;
      letter-spacing: 0.04em;
      margin-bottom: 1.25rem;
      text-align: center;
      max-width: 100%;
    }

    .hero-title {
      font-size: clamp(2.2rem, 8vw, 4rem);
      font-weight: 900;
      line-height: 1.08;
      letter-spacing: -0.03em;
      margin-bottom: 1.25rem;
      background: linear-gradient(135deg, var(--text-primary) 0%, var(--text-secondary) 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      word-break: break-word;
    }

    .hero-description {
      font-size: clamp(1rem, 3vw, 1.25rem);
      color: var(--text-secondary);
      line-height: 1.6;
      margin-bottom: 2rem;
      max-width: 650px;
      word-break: break-word;
    }

    .hero-cta-group {
      display: flex;
      align-items: center;
      gap: 1rem;
      flex-wrap: wrap;
      justify-content: center;
    }

    .btn-lg {
      padding: 0.85rem 1.75rem;
      font-size: 1rem;
      border-radius: var(--radius-md);
    }

    /* Features */
    .features-section {
      padding: 5rem 0;
      background: var(--bg-surface);
      border-top: 1px solid var(--border-color);
      border-bottom: 1px solid var(--border-color);
    }

    .section-heading {
      margin-bottom: 3.5rem;
      text-align: center;
    }

    .sub-badge {
      font-size: 0.75rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      color: var(--color-primary);
      display: block;
      margin-bottom: 0.5rem;
    }

    .section-heading h2 {
      font-size: 2.25rem;
      margin: 0;
    }

    .features-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
      gap: 1.75rem;
    }

    .feature-card {
      background: var(--bg-app);
      border: 1px solid var(--border-color);
      border-radius: var(--radius-lg);
      padding: 2rem;
      transition: all 0.25s ease;
    }

    .feature-card:hover {
      transform: translateY(-5px);
      border-color: var(--color-primary);
      box-shadow: var(--shadow-lg);
    }

    .icon-box {
      width: 52px;
      height: 52px;
      border-radius: var(--radius-md);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.5rem;
      margin-bottom: 1.25rem;
    }

    .bg-purple { background: rgba(99, 102, 241, 0.15); color: #818CF8; }
    .bg-teal { background: rgba(20, 184, 166, 0.15); color: #14B8A6; }
    .bg-orange { background: rgba(245, 158, 11, 0.15); color: #F59E0B; }
    .bg-blue { background: rgba(59, 130, 246, 0.15); color: #3B82F6; }

    .feature-card h3 {
      font-size: 1.2rem;
      margin-bottom: 0.5rem;
    }

    .feature-card p {
      color: var(--text-secondary);
      font-size: 0.9rem;
      line-height: 1.6;
      margin: 0;
    }

    /* Contact Banner */
    .contact-banner-section {
      padding: 5rem 0;
    }

    .contact-banner-card {
      background: var(--color-primary-gradient);
      border-radius: var(--radius-xl);
      padding: 3.5rem 3rem;
      color: #FFFFFF;
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 3rem;
      box-shadow: 0 20px 40px rgba(99, 102, 241, 0.35);
    }

    .banner-tag {
      font-size: 0.75rem;
      font-weight: 800;
      letter-spacing: 0.1em;
      background: rgba(255, 255, 255, 0.2);
      padding: 0.3rem 0.75rem;
      border-radius: var(--radius-full);
      display: inline-block;
      margin-bottom: 1rem;
    }

    .banner-text h2 {
      color: #FFFFFF;
      font-size: 2.2rem;
      margin-bottom: 1rem;
      line-height: 1.2;
    }

    .banner-text p {
      color: rgba(255, 255, 255, 0.9);
      font-size: 1.05rem;
      max-width: 620px;
      line-height: 1.6;
      margin-bottom: 1.5rem;
    }

    .social-links-row {
      display: flex;
      gap: 0.75rem;
      flex-wrap: wrap;
    }

    .social-chip {
      display: inline-flex;
      align-items: center;
      gap: 0.4rem;
      padding: 0.4rem 0.85rem;
      background: rgba(255, 255, 255, 0.15);
      border-radius: var(--radius-full);
      color: #FFFFFF;
      font-size: 0.825rem;
      font-weight: 600;
      transition: background 0.2s;
    }

    .social-chip:hover {
      background: rgba(255, 255, 255, 0.3);
    }

    .btn-whatsapp {
      background: #25D366;
      color: #FFFFFF;
      font-size: 1.1rem;
      padding: 1rem 2rem;
      border-radius: var(--radius-md);
      box-shadow: 0 6px 20px rgba(37, 211, 102, 0.4);
      white-space: nowrap;
    }

    .btn-whatsapp:hover {
      background: #20BA5A;
      box-shadow: 0 8px 25px rgba(37, 211, 102, 0.5);
    }

    /* Footer */
    .landing-footer {
      padding: 2.5rem 0;
      background: var(--bg-surface);
      border-top: 1px solid var(--border-color);
      margin-top: auto;
    }

    .footer-bottom {
      display: flex;
      align-items: center;
      justify-content: space-between;
      color: var(--text-muted);
      font-size: 0.85rem;
    }

    .footer-links a {
      color: var(--text-secondary);
      font-weight: 600;
    }

    @media (max-width: 900px) {
      .hero-title { font-size: 2.75rem; }
      .contact-banner-card { flex-direction: column; text-align: center; padding: 2.5rem 1.5rem; }
      .social-links-row { justify-content: center; }
      .footer-bottom { flex-direction: column; gap: 1rem; text-align: center; }
    }
  `]
})
export class LandingComponent {
  themeService = inject(ThemeService);
  authService = inject(AuthService);
}
