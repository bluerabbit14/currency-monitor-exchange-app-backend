package com.example.currency.common;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Small TTL-bound hash store. Every multi-command operation is a Lua script so a key can never
 * exist without its TTL, and counters never resurrect an expired key.
 */
@Component
public class RedisHashStore {

	private static final DefaultRedisScript<Long> CREATE = new DefaultRedisScript<>("""
			redis.call('DEL', KEYS[1])
			redis.call('HSET', KEYS[1], unpack(ARGV, 2))
			redis.call('EXPIRE', KEYS[1], ARGV[1])
			return 1
			""", Long.class);

	private static final DefaultRedisScript<Long> UPDATE_IF_EXISTS = new DefaultRedisScript<>("""
			if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
			redis.call('HSET', KEYS[1], unpack(ARGV))
			return 1
			""", Long.class);

	private static final DefaultRedisScript<Long> INCREMENT_IF_EXISTS = new DefaultRedisScript<>("""
			if redis.call('EXISTS', KEYS[1]) == 0 then return -1 end
			return redis.call('HINCRBY', KEYS[1], ARGV[1], 1)
			""", Long.class);

	private final StringRedisTemplate redis;

	public RedisHashStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** Replaces any existing hash at {@code key}; the key expires after {@code ttl}. */
	public void create(String key, Map<String, String> fields, Duration ttl) {
		if (fields.isEmpty()) {
			throw new IllegalArgumentException("fields must not be empty");
		}
		List<String> args = new ArrayList<>();
		args.add(String.valueOf(Math.max(1, ttl.toSeconds())));
		fields.forEach((field, value) -> {
			args.add(field);
			args.add(value);
		});
		redis.execute(CREATE, List.of(key), args.toArray());
	}

	public Optional<Map<String, String>> get(String key) {
		Map<Object, Object> entries = redis.opsForHash().entries(key);
		if (entries.isEmpty()) {
			return Optional.empty();
		}
		Map<String, String> result = new java.util.HashMap<>();
		entries.forEach((field, value) -> result.put(field.toString(), value.toString()));
		return Optional.of(result);
	}

	/** Sets fields on an existing hash without touching its TTL. Returns false if the key is gone. */
	public boolean updateIfExists(String key, Map<String, String> fields) {
		if (fields.isEmpty()) {
			return get(key).isPresent();
		}
		List<String> args = new ArrayList<>();
		fields.forEach((field, value) -> {
			args.add(field);
			args.add(value);
		});
		Long updated = redis.execute(UPDATE_IF_EXISTS, List.of(key), args.toArray());
		return updated != null && updated == 1L;
	}

	/** Returns the new value, or -1 if the key no longer exists. */
	public long incrementIfExists(String key, String field) {
		Long value = redis.execute(INCREMENT_IF_EXISTS, List.of(key), field);
		return value == null ? -1 : value;
	}

	/** Returns true only for the caller that actually removed the key (single-use guarantee). */
	public boolean delete(String key) {
		return Boolean.TRUE.equals(redis.delete(key));
	}

}
