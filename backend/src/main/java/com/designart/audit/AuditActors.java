package com.designart.audit;

import com.designart.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Resolve o ator da requisição corrente a partir do usuário AUTENTICADO (o
 * principal é o ID validado pelo filtro JWT), lendo o estado atual no banco para
 * o snapshot. Sem autenticação real: ator anônimo.
 */
@Component
@RequiredArgsConstructor
public class AuditActors {

    private final UserRepository userRepository;

    public AuditActor current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return AuditActor.anonymous();
        }
        try {
            return userRepository.findById(Long.valueOf(auth.getName()))
                    .map(AuditActor::of)
                    .orElseGet(AuditActor::anonymous);
        } catch (NumberFormatException e) {
            return AuditActor.anonymous();
        }
    }
}
