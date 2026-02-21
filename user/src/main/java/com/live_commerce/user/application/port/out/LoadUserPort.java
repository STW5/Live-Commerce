package com.live_commerce.user.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.live_commerce.user.domain.model.User;

public interface LoadUserPort {
	Optional<User> loadById(UUID userId);
	Optional<User> loadByUsername(String username);
	Optional<User> loadByEmail(String email);
	Optional<User> loadByUsernameAndEmail(String username, String email);
	boolean existsByEmail(String email);
}
