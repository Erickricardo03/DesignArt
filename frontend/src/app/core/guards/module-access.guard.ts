import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { ModuloAcesso } from '../models';

/**
 * Libera a rota para ADMIN (sempre tem acesso total) ou para um COLABORADOR
 * que tenha recebido a permissão granular deste módulo especificamente
 * (definida pelo admin ao cadastrar o usuário em Configurações > Usuários).
 */
export function moduleAccessGuard(modulo: ModuloAcesso): CanActivateFn {
  return () => {
    const authService = inject(AuthService);
    const router = inject(Router);

    if (!authService.isLoggedIn()) {
      router.navigate(['/login']);
      return false;
    }

    const user = authService.currentUser();
    // TENANT_ADMIN tem as permissões do tenant implicitamente; USER só as atribuídas;
    // SUPER_ADMIN não opera módulos de tenant. (Conveniência de navegação: a
    // autorização real é feita pelo backend.)
    if (user?.role === 'TENANT_ADMIN' || (user?.role === 'USER' && user.permissoes?.includes(modulo))) {
      return true;
    }

    router.navigate(['/dashboard']);
    return false;
  };
}
