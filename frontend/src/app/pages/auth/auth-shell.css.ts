/** Estilos compartilhados das páginas públicas de conta (recuperação/convite). */
export const AUTH_PAGE_STYLES = `
  .auth-wrapper { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: var(--bg-app); padding: 2rem 1rem; }
  .auth-card { width: 100%; max-width: 440px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: var(--radius-xl); box-shadow: var(--shadow-xl); padding: 2.25rem 2rem; }
  .auth-card h1 { font-size: 1.5rem; margin: 0 0 0.35rem; }
  .auth-card p.sub { color: var(--text-muted); font-size: 0.875rem; margin: 0 0 1.5rem; line-height: 1.5; }
  .auth-card .form-group { margin-bottom: 1rem; }
  .auth-card .form-label { display: block; font-size: 0.72rem; font-weight: 800; letter-spacing: 0.08em; margin-bottom: 0.35rem; }
  .auth-card .btn-submit { width: 100%; padding: 0.8rem; font-weight: 700; }
  .auth-alert { padding: 0.75rem 1rem; border-radius: var(--radius-md); font-size: 0.85rem; margin-bottom: 1rem; line-height: 1.45; }
  .auth-alert.error { background: var(--color-danger-light); color: var(--color-danger); border: 1px solid rgba(239,68,68,.3); }
  .auth-alert.ok { background: var(--color-success-light); color: var(--color-success); border: 1px solid rgba(16,185,129,.3); }
  .auth-links { text-align: center; margin-top: 1.25rem; font-size: 0.85rem; }
  .auth-links a { color: var(--color-primary); font-weight: 600; }
  .pwd-hint { font-size: 0.75rem; color: var(--text-muted); margin-top: 0.3rem; }
`;
