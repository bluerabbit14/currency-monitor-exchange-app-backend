package com.example.currency.plan.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class PlanEntitlementId implements Serializable {

	private UUID plan;
	private String featureKey;

	public PlanEntitlementId() {
	}

	public PlanEntitlementId(UUID plan, String featureKey) {
		this.plan = plan;
		this.featureKey = featureKey;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof PlanEntitlementId other)) {
			return false;
		}
		return Objects.equals(plan, other.plan) && Objects.equals(featureKey, other.featureKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(plan, featureKey);
	}

}
