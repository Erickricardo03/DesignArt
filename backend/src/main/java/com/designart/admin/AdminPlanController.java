package com.designart.admin;

import com.designart.admin.dto.AdminDtos.*;
import com.designart.security.SuperAdminOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Planos e catálogo de features (global). Somente SUPER_ADMIN. Não há exclusão de plano (só desativação). */
@RestController
@RequestMapping("/api/admin")
@SuperAdminOnly
@RequiredArgsConstructor
public class AdminPlanController {

    private final AdminPlanService plans;

    @GetMapping("/plans")
    public List<PlanDto> list() {
        return plans.listPlans();
    }

    @GetMapping("/plans/{id}")
    public PlanDto get(@PathVariable Long id) {
        return plans.getPlan(id);
    }

    @PostMapping("/plans")
    public ResponseEntity<PlanDto> create(@RequestBody PlanRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(plans.createPlan(req));
    }

    @PutMapping("/plans/{id}")
    public PlanDto update(@PathVariable Long id, @RequestBody PlanRequest req) {
        return plans.updatePlan(id, req);
    }

    @GetMapping("/plans/{id}/features")
    public List<PlanFeatureDto> planFeatures(@PathVariable Long id) {
        return plans.getPlanFeatures(id);
    }

    @PutMapping("/plans/{id}/features")
    public List<PlanFeatureDto> putPlanFeatures(@PathVariable Long id, @RequestBody PlanFeaturesRequest req) {
        return plans.replacePlanFeatures(id, req);
    }

    @GetMapping("/features")
    public List<FeatureDto> features() {
        return plans.listFeatures();
    }
}
