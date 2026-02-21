package com.live_commerce.user.infrastructure.adapter.persistence;

import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import com.live_commerce.user.domain.model.User;
import com.live_commerce.user.domain.model.UserRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "p_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserJpaEntity extends BaseJpaEntity {

	@Id
	@UuidGenerator
	private UUID userId;

	@Column(nullable = false, unique = true)
	private String username;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(nullable = false)
	private String nickname;

	@Column(nullable = false)
	private boolean alarmConsent;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private UserRole userRole;

	@Column(nullable = false)
	private boolean approved;

	// Domain → JPA Entity
	public static UserJpaEntity from(User domain) {
		UserJpaEntity entity = new UserJpaEntity();
		entity.userId = domain.getUserId();
		entity.username = domain.getUsername();
		entity.password = domain.getPassword();
		entity.email = domain.getEmail();
		entity.nickname = domain.getNickname();
		entity.alarmConsent = domain.isAlarmConsent();
		entity.userRole = domain.getUserRole();
		entity.approved = domain.isApproved();
		if (domain.isDeleted()) {
			entity.markAsDeleted(domain.getDeletedBy());
		}
		return entity;
	}

	// JPA Entity → Domain
	public User toDomain() {
		return User.reconstitute(
			userId, username, password, email, nickname,
			alarmConsent, userRole, approved,
			isDeletedStatus(), getDeletedBy()
		);
	}
}
