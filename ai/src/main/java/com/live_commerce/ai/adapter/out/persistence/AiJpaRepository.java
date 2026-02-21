package com.live_commerce.ai.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiJpaRepository extends JpaRepository<AiJpaEntity, UUID>, AiQueryJpaRepository {
}
