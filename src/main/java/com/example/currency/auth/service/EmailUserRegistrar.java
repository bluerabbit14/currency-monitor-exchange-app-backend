package com.example.currency.auth.service;

import com.example.currency.user.entity.User;
import com.example.currency.user.entity.UserAuthProvider;
import com.example.currency.user.enums.AuthProvider;
import com.example.currency.user.enums.UserStatus;
import com.example.currency.user.repository.UserAuthProviderRepository;
import com.example.currency.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class EmailUserRegistrar {

	private final UserRepository userRepository;
	private final UserAuthProviderRepository userAuthProviderRepository;

	public EmailUserRegistrar(
			UserRepository userRepository,
			UserAuthProviderRepository userAuthProviderRepository) {
		this.userRepository = userRepository;
		this.userAuthProviderRepository = userAuthProviderRepository;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public User register(String email) {
		User user = new User();
		user.setEmail(email);
		user.setStatus(UserStatus.ACTIVE);
		user = userRepository.saveAndFlush(user);

		UserAuthProvider provider = new UserAuthProvider();
		provider.setUser(user);
		provider.setProvider(AuthProvider.EMAIL);
		provider.setProviderUserId(email);
		provider.setLastUsedAt(Instant.now());
		userAuthProviderRepository.save(provider);
		return user;
	}

}
