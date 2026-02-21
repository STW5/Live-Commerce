package com.live_commerce.user.infrastructure.adapter.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJpaRepository extends JpaRepository<UserJpaEntity, UUID>,
	UserQueryJpaRepository {

	boolean existsByEmail(String email);

	Optional<UserJpaEntity> findByEmail(String email);

	Optional<UserJpaEntity> findByUsernameAndEmail(String username, String email);

	Optional<UserJpaEntity> findByUsername(String username);
}
