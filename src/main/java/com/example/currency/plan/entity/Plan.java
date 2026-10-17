package com.example.currency.plan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

	@Id
	private UUID id;

	@Column(nullable = false, unique = true, length = 32)
	private String code;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "is_default", nullable = false)
	private boolean defaultPlan;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "plan", fetch = FetchType.LAZY)
	private Set<PlanEntitlement> entitlements = new LinkedHashSet<>();

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public boolean isDefaultPlan() {
		return defaultPlan;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Set<PlanEntitlement> getEntitlements() {
		return entitlements;
	}

}
