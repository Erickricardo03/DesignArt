package com.designart.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlanFeatureRepository extends JpaRepository<PlanFeature, Long> {
    List<PlanFeature> findByPlanId(Long planId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from PlanFeature pf where pf.planId = :planId")
    int deleteByPlanId(@Param("planId") Long planId);
}
