package com.live_commerce.user.application.port.out;

import java.util.List;

import com.live_commerce.user.application.dto.auth.request.UserSearchCondition;
import com.live_commerce.user.domain.model.User;

public interface SearchUserPort {
	List<User> searchUser(UserSearchCondition condition);
}
