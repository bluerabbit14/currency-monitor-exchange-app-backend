package com.example.currency.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "plan_entitlements")
@IdClass(PlanEntitlementId.class)
public class PlanEntitlement {

	@Id
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_id")
	private Plan plan;

	@Id
	@Column(name = "feature_key", nullable = false, length = 64)
	private String featureKey;

	@Column(nullable = false)
	private boolean enabled = true;

	/** Null means unlimited when the feature is enabled. */
	@Column(name = "limit_value")
	private Integer limitValue;

	public Plan getPlan() {
		return plan;
	}

	public String getFeatureKey() {
		return featureKey;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public Integer getLimitValue() {
		return limitValue;
	}

}
