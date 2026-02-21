package com.live_commerce.user.infrastructure.adapter.persistence;

import java.util.List;

import com.live_commerce.user.application.dto.auth.request.UserSearchCondition;

public interface UserQueryJpaRepository {
	List<UserJpaEntity> searchUser(UserSearchCondition condition);
}
