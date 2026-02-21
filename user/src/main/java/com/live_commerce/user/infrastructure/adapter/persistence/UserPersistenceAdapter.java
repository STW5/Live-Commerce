package com.live_commerce.user.infrastructure.adapter.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.live_commerce.user.application.dto.auth.request.UserSearchCondition;
import com.live_commerce.user.application.port.out.LoadUserPort;
import com.live_commerce.user.application.port.out.SaveUserPort;
import com.live_commerce.user.application.port.out.SearchUserPort;
import com.live_commerce.user.domain.model.User;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements LoadUserPort, SaveUserPort, SearchUserPort {

	private final UserJpaRepository userJpaRepository;

	@Override
	public User save(User user) {
		return userJpaRepository.save(UserJpaEntity.from(user)).toDomain();
	}

	@Override
	public Optional<User> loadById(UUID userId) {
		return userJpaRepository.findById(userId).map(UserJpaEntity::toDomain);
	}

	@Override
	public Optional<User> loadByUsername(String username) {
		return userJpaRepository.findByUsername(username).map(UserJpaEntity::toDomain);
	}

	@Override
	public Optional<User> loadByEmail(String email) {
		return userJpaRepository.findByEmail(email).map(UserJpaEntity::toDomain);
	}

	@Override
	public Optional<User> loadByUsernameAndEmail(String username, String email) {
		return userJpaRepository.findByUsernameAndEmail(username, email)
			.map(UserJpaEntity::toDomain);
	}

	@Override
	public boolean existsByEmail(String email) {
		return userJpaRepository.existsByEmail(email);
	}

	@Override
	public List<User> searchUser(UserSearchCondition condition) {
		return userJpaRepository.searchUser(condition).stream()
			.map(UserJpaEntity::toDomain)
			.toList();
	}
}
