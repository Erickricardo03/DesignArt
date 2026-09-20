package com.designart.support;

import java.lang.annotation.*;

/**
 * Marca explicitamente um handler como habilitado para Support Session e declara QUAL operação da allowlist ele
 * representa. O {@link SupportAccessInterceptor} nega (403) qualquer handler de suporte sem esta anotação.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SupportAllowed {
    SupportOperation value();
}
