export function getApiBaseUrl(): string {
  if (typeof window !== 'undefined') {
    const customUrl = localStorage.getItem('DESIGNART_API_URL');
    if (customUrl) {
      return customUrl.replace(/\/+$/, '');
    }

    const host = window.location.hostname;
    if (host === 'localhost' || host === '127.0.0.1') {
      return 'http://localhost:8080/api';
    }

    // Se estiver rodando no Render ou outro provedor na nuvem
    // Se estiver em um subdomínio .onrender.com, tenta conectar no serviço backend padrão
    return 'https://designart-api.onrender.com/api';
  }
  return 'http://localhost:8080/api';
}
