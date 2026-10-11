package com.example.currency.watchlist.entity;

import com.example.currency.common.AuditableEntity;
import com.example.currency.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
		name = "watchlists",
		indexes = @Index(name = "watchlists_user_id_idx", columnList = "user_id")
)
public class Watchlist extends AuditableEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private User user;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(name = "is_default", nullable = false)
	private boolean defaultList;

	@OneToMany(mappedBy = "watchlist", fetch = FetchType.LAZY)
	private Set<WatchlistItem> items = new LinkedHashSet<>();

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public boolean isDefaultList() {
		return defaultList;
	}

	public void setDefaultList(boolean defaultList) {
		this.defaultList = defaultList;
	}

	public Set<WatchlistItem> getItems() {
		return items;
	}

}
