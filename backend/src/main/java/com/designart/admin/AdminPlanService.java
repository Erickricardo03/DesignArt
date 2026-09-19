package com.designart.admin;

import com.designart.admin.dto.AdminDtos.*;
import com.designart.audit.*;
import com.designart.billing.*;
import com.designart.exception.ConflictException;
import com.designart.exception.InvalidRequestException;
import com.designart.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Planos e suas features (plano de controle, SUPER_ADMIN). O que cada plano inclui é DADO no banco. */
@Service
@RequiredArgsConstructor
public class AdminPlanService {

    private final PlanRepository planRepository;
    private final FeatureRepository featureRepository;
    private final PlanFeatureRepository planFeatureRepository;
    private final AuditService auditService;
    private final AuditActors auditActors;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PlanDto> listPlans() {
        return planRepository.findAll().stream().sorted(Comparator.comparing(Plan::getId)).map(AdminPlanService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public PlanDto getPlan(Long id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<FeatureDto> listFeatures() {
        return featureRepository.findAll().stream().sorted(Comparator.comparing(Feature::getId))
                .map(f -> new FeatureDto(f.getId(), f.getCode(), f.getName(), f.getDescription(), f.getKind(), f.isActive())).toList();
    }

    @Transactional
    public PlanDto createPlan(PlanRequest req) {
        String code = AdminRules.code(req.code());
        if (planRepository.existsByCode(code)) {
            throw new ConflictException("Já existe um plano com este código.");
        }
        Plan plan = new Plan();
        plan.setCode(code);
        plan.setName(AdminRules.name(req.name()));
        plan.setDescription(AdminRules.optionalText(req.description(), 500));
        plan.setActive(req.active() == null || req.active());
        LocalDateTime now = LocalDateTime.now(clock);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        try {
            plan = planRepository.saveAndFlush(plan);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Já existe um plano com este código.");
        }
        auditService.success(AuditAction.PLAN_CREATED, auditActors.current(),
                AuditTarget.entity(null, AuditEntityType.PLAN, plan.getId()), AuditMetadata.EMPTY);
        return toDto(plan);
    }

    @Transactional
    public PlanDto updatePlan(Long id, PlanRequest req) {
        Plan plan = find(id);
        if (req.code() != null && !req.code().trim().equals(plan.getCode())) {
            throw new InvalidRequestException("O código do plano é imutável.");
        }
        Set<AuditField> changed = EnumSet.noneOf(AuditField.class);
        String name = AdminRules.name(req.name());
        if (!name.equals(plan.getName())) {
            plan.setName(name);
            changed.add(AuditField.NOME);
        }
        String description = AdminRules.optionalText(req.description(), 500);
        if (!Objects.equals(description, plan.getDescription())) {
            plan.setDescription(description);
            changed.add(AuditField.DESCRIPTION);
        }
        if (req.active() != null && req.active() != plan.isActive()) {
            plan.setActive(req.active());
            changed.add(AuditField.ACTIVE);
        }
        if (!changed.isEmpty()) {
            plan.setUpdatedAt(LocalDateTime.now(clock));
            planRepository.save(plan);
            auditService.success(AuditAction.PLAN_UPDATED, auditActors.current(),
                    AuditTarget.entity(null, AuditEntityType.PLAN, plan.getId()),
                    AuditMetadata.builder().fieldsChanged(changed).build());
        }
        return toDto(plan);
    }

    @Transactional(readOnly = true)
    public List<PlanFeatureDto> getPlanFeatures(Long planId) {
        find(planId);
        Map<Long, Feature> byId = featureRepository.findAll().stream().collect(Collectors.toMap(Feature::getId, f -> f));
        return planFeatureRepository.findByPlanId(planId).stream()
                .filter(pf -> byId.containsKey(pf.getFeatureId()))
                .sorted(Comparator.comparing(pf -> byId.get(pf.getFeatureId()).getCode()))
                .map(pf -> new PlanFeatureDto(byId.get(pf.getFeatureId()).getCode(), byId.get(pf.getFeatureId()).getKind(),
                        pf.isEnabled(), pf.getLimitValue()))
                .toList();
    }

    /** Substitui o conjunto de features do plano (PUT). BOOLEAN não tem limite; LIMIT habilitado exige limite. */
    @Transactional
    public List<PlanFeatureDto> replacePlanFeatures(Long planId, PlanFeaturesRequest req) {
        find(planId);
        if (req == null || req.features() == null) {
            throw new InvalidRequestException("Informe a lista de funcionalidades do plano.");
        }
        Set<String> vistos = new HashSet<>();
        List<PlanFeature> novos = new ArrayList<>();
        for (PlanFeatureRequest item : req.features()) {
            String code = AdminRules.code(item == null ? null : item.featureCode());
            if (!vistos.add(code)) {
                throw new InvalidRequestException("Funcionalidade repetida na lista.");
            }
            Feature feature = featureRepository.findByCode(code)
                    .filter(Feature::isActive)
                    .orElseThrow(() -> new InvalidRequestException("Funcionalidade inexistente ou inativa."));
            boolean enabled = item.enabled() == null || item.enabled();
            Long limit = AdminRules.limit(item.limit());
            if (feature.getKind() == FeatureKind.BOOLEAN && limit != null) {
                throw new InvalidRequestException("Esta funcionalidade não aceita limite numérico.");
            }
            if (feature.getKind() == FeatureKind.LIMIT && enabled && limit == null) {
                throw new InvalidRequestException("Informe o limite numérico desta funcionalidade.");
            }
            if (!enabled) {
                limit = null;
            }
            PlanFeature pf = new PlanFeature();
            pf.setPlanId(planId);
            pf.setFeatureId(feature.getId());
            pf.setEnabled(enabled);
            pf.setLimitValue(limit);
            novos.add(pf);
        }
        planFeatureRepository.deleteByPlanId(planId);
        planFeatureRepository.saveAll(novos);
        planFeatureRepository.flush();
        auditService.success(AuditAction.PLAN_FEATURES_CHANGED, auditActors.current(),
                AuditTarget.entity(null, AuditEntityType.PLAN, planId),
                AuditMetadata.builder().itemCount(novos.size()).build());
        return getPlanFeatures(planId);
    }

    private Plan find(Long id) {
        return planRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado."));
    }

    static PlanDto toDto(Plan p) {
        return new PlanDto(p.getId(), p.getCode(), p.getName(), p.getDescription(), p.isActive());
    }
}
