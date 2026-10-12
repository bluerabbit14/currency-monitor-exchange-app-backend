package com.example.currency.alert.service;

import com.example.currency.alert.dto.CreatePriceAlertRequest;
import com.example.currency.alert.dto.PriceAlertResponse;
import com.example.currency.alert.dto.UpdatePriceAlertRequest;
import com.example.currency.alert.entity.PriceAlert;
import com.example.currency.alert.enums.AlertStatus;
import com.example.currency.alert.exception.AlertNotFoundException;
import com.example.currency.alert.repository.PriceAlertRepository;
import com.example.currency.asset.entity.AssetPair;
import com.example.currency.asset.enums.AssetStatus;
import com.example.currency.asset.repository.AssetPairRepository;
import com.example.currency.rate.exception.UnsupportedPairException;
import com.example.currency.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class PriceAlertService {

	private final PriceAlertRepository priceAlertRepository;
	private final AssetPairRepository assetPairRepository;
	private final UserRepository userRepository;

	public PriceAlertService(
			PriceAlertRepository priceAlertRepository,
			AssetPairRepository assetPairRepository,
			UserRepository userRepository) {
		this.priceAlertRepository = priceAlertRepository;
		this.assetPairRepository = assetPairRepository;
		this.userRepository = userRepository;
	}

	@Transactional
	public PriceAlertResponse create(UUID userId, CreatePriceAlertRequest request) {
		String base = requireSymbol(request.base(), "base");
		String quote = requireSymbol(request.quote(), "quote");
		BigDecimal targetRate = requirePositiveTargetRate(request.targetRate());
		AssetPair pair = requireActivePair(base, quote);

		PriceAlert alert = new PriceAlert();
		alert.setUser(userRepository.getReferenceById(userId));
		alert.setPair(pair);
		alert.setCondition(request.condition());
		alert.setTargetPrice(targetRate);
		alert.setStatus(AlertStatus.ACTIVE);
		alert.setTriggeredAt(null);

		return toResponse(priceAlertRepository.save(alert));
	}

	@Transactional(readOnly = true)
	public Page<PriceAlertResponse> list(UUID userId, Pageable pageable) {
		return priceAlertRepository.findByUserId(userId, pageable).map(PriceAlertService::toResponse);
	}

	@Transactional(readOnly = true)
	public PriceAlertResponse get(UUID userId, UUID alertId) {
		return toResponse(requireOwnedAlert(userId, alertId));
	}

	@Transactional
	public PriceAlertResponse update(UUID userId, UUID alertId, UpdatePriceAlertRequest request) {
		PriceAlert alert = requireOwnedAlert(userId, alertId);
		if (request.condition() != null) {
			alert.setCondition(request.condition());
		}
		if (request.targetRate() != null) {
			alert.setTargetPrice(requirePositiveTargetRate(request.targetRate()));
		}
		return toResponse(alert);
	}

	@Transactional
	public void delete(UUID userId, UUID alertId) {
		int deleted = priceAlertRepository.deleteByIdAndUserId(alertId, userId);
		if (deleted == 0) {
			throw new AlertNotFoundException(alertId);
		}
	}

	private PriceAlert requireOwnedAlert(UUID userId, UUID alertId) {
		return priceAlertRepository.findByIdAndUserId(alertId, userId)
				.orElseThrow(() -> new AlertNotFoundException(alertId));
	}

	private AssetPair requireActivePair(String base, String quote) {
		return assetPairRepository.findByStatusAndSymbols(AssetStatus.ACTIVE, base, quote)
				.orElseThrow(() -> new UnsupportedPairException(base, quote));
	}

	private static PriceAlertResponse toResponse(PriceAlert alert) {
		AssetPair pair = alert.getPair();
		return new PriceAlertResponse(
				alert.getId(),
				pair.getBaseAsset().getSymbol(),
				pair.getQuoteAsset().getSymbol(),
				alert.getCondition(),
				alert.getTargetPrice(),
				alert.getStatus(),
				alert.getCreatedAt(),
				alert.getUpdatedAt(),
				alert.getTriggeredAt());
	}

	private static String requireSymbol(String symbol, String field) {
		if (symbol == null) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		String normalized = symbol.trim().toUpperCase();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
		return normalized;
	}

	private static BigDecimal requirePositiveTargetRate(BigDecimal targetRate) {
		if (targetRate == null) {
			throw new IllegalArgumentException("targetRate is required");
		}
		if (targetRate.signum() <= 0) {
			throw new IllegalArgumentException("targetRate must be positive");
		}
		return targetRate;
	}

}
