package com.example.currency.alert.repository;

import com.example.currency.alert.entity.PriceAlert;
import com.example.currency.alert.enums.AlertCondition;
import com.example.currency.alert.enums.AlertStatus;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.user.entity.User;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.hibernate.ddl-auto=validate")
@Testcontainers(disabledWithoutDocker = true)
@Transactional
class PriceAlertRepositoryIT {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private PriceAlertRepository priceAlertRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AssetPairRepository assetPairRepository;

	@Autowired
	private EntityManager entityManager;

	@Test
	void persistsAlertAndMapsEntityToSchema() {
		User user = userRepository.saveAndFlush(activeUser("alert-owner@example.com"));
		AssetPair pair = seededPair("USD", "INR");

		PriceAlert alert = priceAlertRepository.saveAndFlush(alert(user, pair, AlertCondition.ABOVE, "90.00"));
		entityManager.clear();

		PriceAlert loaded = priceAlertRepository.findByIdAndUserId(alert.getId(), user.getId()).orElseThrow();
		assertEquals("USD", loaded.getPair().getBaseAsset().getSymbol());
		assertEquals("INR", loaded.getPair().getQuoteAsset().getSymbol());
		assertEquals(0, loaded.getTargetPrice().compareTo(new BigDecimal("90.00")));
		assertEquals(AlertStatus.ACTIVE, loaded.getStatus());
		assertEquals(AlertCondition.ABOVE, loaded.getCondition());
	}

	@Test
	void findByIdAndUserIdEnforcesOwnership() {
		User owner = userRepository.saveAndFlush(activeUser("owner@example.com"));
		User other = userRepository.saveAndFlush(activeUser("other@example.com"));
		AssetPair pair = seededPair("BTC", "USD");
		PriceAlert alert = priceAlertRepository.saveAndFlush(alert(owner, pair, AlertCondition.BELOW, "60000"));

		assertTrue(priceAlertRepository.findByIdAndUserId(alert.getId(), owner.getId()).isPresent());
		assertTrue(priceAlertRepository.findByIdAndUserId(alert.getId(), other.getId()).isEmpty());
	}

	@Test
	void listsAlertsForUserNewestFirst() {
		User user = userRepository.saveAndFlush(activeUser("list@example.com"));
		User other = userRepository.saveAndFlush(activeUser("stranger@example.com"));
		AssetPair usdInr = seededPair("USD", "INR");
		AssetPair btcUsd = seededPair("BTC", "USD");

		PriceAlert older = priceAlertRepository.saveAndFlush(alert(user, usdInr, AlertCondition.ABOVE, "80"));
		PriceAlert newer = priceAlertRepository.saveAndFlush(alert(user, btcUsd, AlertCondition.BELOW, "70000"));
		priceAlertRepository.saveAndFlush(alert(other, usdInr, AlertCondition.ABOVE, "85"));

		updateCreatedAt(older.getId(), Instant.parse("2026-10-10T00:00:00Z"));
		updateCreatedAt(newer.getId(), Instant.parse("2026-10-12T00:00:00Z"));
		entityManager.clear();

		Page<PriceAlert> page = priceAlertRepository.findByUserId(
				user.getId(),
				PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

		assertEquals(2, page.getTotalElements());
		assertEquals(newer.getId(), page.getContent().get(0).getId());
		assertEquals(older.getId(), page.getContent().get(1).getId());
		assertEquals("BTC", page.getContent().get(0).getPair().getBaseAsset().getSymbol());
	}

	@Test
	void deleteByIdAndUserIdIsOwnershipScoped() {
		User owner = userRepository.saveAndFlush(activeUser("del-owner@example.com"));
		User other = userRepository.saveAndFlush(activeUser("del-other@example.com"));
		AssetPair pair = seededPair("ETH", "USD");
		PriceAlert owned = priceAlertRepository.saveAndFlush(alert(owner, pair, AlertCondition.ABOVE, "4000"));
		PriceAlert strangers = priceAlertRepository.saveAndFlush(alert(other, pair, AlertCondition.ABOVE, "4100"));

		assertEquals(0, priceAlertRepository.deleteByIdAndUserId(strangers.getId(), owner.getId()));
		assertEquals(1, priceAlertRepository.deleteByIdAndUserId(owned.getId(), owner.getId()));
		entityManager.clear();

		assertFalse(priceAlertRepository.findById(owned.getId()).isPresent());
		assertTrue(priceAlertRepository.findById(strangers.getId()).isPresent());
	}

	@Test
	void deletingUserCascadesAlerts() {
		User user = userRepository.saveAndFlush(activeUser("cascade-alert@example.com"));
		AssetPair pair = seededPair("SOL", "USD");
		PriceAlert alert = priceAlertRepository.saveAndFlush(alert(user, pair, AlertCondition.ABOVE, "200"));
		UUID alertId = alert.getId();

		userRepository.delete(user);
		userRepository.flush();
		entityManager.clear();

		assertTrue(priceAlertRepository.findById(alertId).isEmpty());
		assertTrue(assetPairRepository.findById(pair.getId()).isPresent());
	}

	private void updateCreatedAt(UUID alertId, Instant createdAt) {
		entityManager.createNativeQuery("UPDATE price_alerts SET created_at = :createdAt WHERE id = :id")
				.setParameter("createdAt", createdAt)
				.setParameter("id", alertId)
				.executeUpdate();
	}

	private AssetPair seededPair(String base, String quote) {
		Optional<AssetPair> pair = assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, base, quote);
		return pair.orElseThrow();
	}

	private static User activeUser(String email) {
		User user = new User();
		user.setEmail(email);
		user.setStatus(UserStatus.ACTIVE);
		return user;
	}

	private static PriceAlert alert(User user, AssetPair pair, AlertCondition condition, String target) {
		PriceAlert alert = new PriceAlert();
		alert.setUser(user);
		alert.setPair(pair);
		alert.setCondition(condition);
		alert.setTargetPrice(new BigDecimal(target));
		alert.setStatus(AlertStatus.ACTIVE);
		return alert;
	}

}
