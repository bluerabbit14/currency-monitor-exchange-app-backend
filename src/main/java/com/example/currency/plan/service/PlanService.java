package com.example.currency.plan.service;

import com.example.currency.plan.dto.PlanFeatureResponse;
import com.example.currency.plan.dto.PlanResponse;
import com.example.currency.plan.entity.Plan;
import com.example.currency.plan.entity.PlanEntitlement;
import com.example.currency.plan.repository.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class PlanService {

	private final PlanRepository planRepository;

	public PlanService(PlanRepository planRepository) {
		this.planRepository = planRepository;
	}

	public List<PlanResponse> listActive() {
		return planRepository.findByActiveTrueOrderByCreatedAtAscNameAsc().stream()
				.map(PlanService::toResponse)
				.toList();
	}

	/** The live subscription's plan, or the default plan when the user has none. */
	public PlanResponse currentPlanFor(UUID userId) {
		return planRepository.findCurrentForUser(userId, Instant.now())
				.or(planRepository::findByDefaultPlanTrue)
				.map(PlanService::toResponse)
				.orElse(null);
	}

	private static PlanResponse toResponse(Plan plan) {
		List<PlanFeatureResponse> features = plan.getEntitlements().stream()
				.sorted(Comparator.comparing(PlanEntitlement::getFeatureKey))
				.map(e -> new PlanFeatureResponse(e.getFeatureKey(), e.isEnabled(), e.getLimitValue()))
				.toList();
		return new PlanResponse(plan.getId(), plan.getCode(), plan.getName(), plan.isDefaultPlan(), features);
	}

}
