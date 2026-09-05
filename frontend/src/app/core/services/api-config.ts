/**
 * Configuração e gerenciamento dinâmico da URL da API Backend
 */
export function getDefaultApiUrl(): string {
  if (typeof window !== 'undefined') {
    const host = window.location.hostname;
    if (host === 'localhost' || host === '127.0.0.1') {
      return 'http://localhost:8080/api';
    }
    // No Render ou nuvem, o backend costuma ter o subdomínio correspondente ou designart-api
    return 'https://designart-api.onrender.com/api';
  }
  return 'http://localhost:8080/api';
}

export function getApiBaseUrl(): string {
  if (typeof window !== 'undefined') {
    const customUrl = localStorage.getItem('DESIGNART_API_URL');
    if (customUrl && customUrl.trim().length > 0) {
      let cleaned = customUrl.trim().replace(/\/+$/, '');
      if (!cleaned.endsWith('/api')) {
        // Garante que se o usuário digitou https://meu-backend.onrender.com sem /api, adicionamos /api
        cleaned = `${cleaned}/api`;
      }
      return cleaned;
    }

    return getDefaultApiUrl();
  }
  return 'http://localhost:8080/api';
}

export function setApiBaseUrl(url: string): void {
  if (typeof window !== 'undefined') {
    if (!url || !url.trim()) {
      localStorage.removeItem('DESIGNART_API_URL');
      return;
    }
    let cleaned = url.trim().replace(/\/+$/, '');
    if (!cleaned.endsWith('/api')) {
      cleaned = `${cleaned}/api`;
    }
    localStorage.setItem('DESIGNART_API_URL', cleaned);
  }
}

export function resetApiBaseUrl(): void {
  if (typeof window !== 'undefined') {
    localStorage.removeItem('DESIGNART_API_URL');
  }
}

export function isCustomApiUrlSet(): boolean {
  if (typeof window !== 'undefined') {
    const val = localStorage.getItem('DESIGNART_API_URL');
    return !!val && val.trim().length > 0;
  }
  return false;
}
