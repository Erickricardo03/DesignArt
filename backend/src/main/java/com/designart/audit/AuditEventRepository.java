package com.designart.audit;

import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Acesso à trilha de auditoria: SOMENTE inserir e consultar. De propósito NÃO
 * estende Jpa/CrudRepository — não existe delete/update. Quem consulta (futuras
 * telas administrativas) deve estar autorizado; a inserção só acontece via
 * {@link AuditService}.
 */
public interface AuditEventRepository extends Repository<AuditEvent, Long> {

    <S extends AuditEvent> S save(S event);

    long count();

    List<AuditEvent> findAllByOrderByIdAsc();

    List<AuditEvent> findByTargetUserIdOrderByIdAsc(Long targetUserId);

    List<AuditEvent> findByActorUserIdOrderByIdAsc(Long actorUserId);

    List<AuditEvent> findByTargetTenantIdOrderByIdAsc(Long targetTenantId);
}
