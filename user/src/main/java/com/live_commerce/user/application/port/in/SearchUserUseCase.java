package com.live_commerce.user.application.port.in;

import java.util.List;

import com.live_commerce.user.application.dto.auth.request.UserSearchCondition;
import com.live_commerce.user.application.dto.result.UserGetResult;

public interface SearchUserUseCase {
	List<UserGetResult> searchUser(UserSearchCondition condition);
}
