package com.live_commerce.user.application.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.live_commerce.user.application.dto.auth.request.UserSearchCondition;
import com.live_commerce.user.application.dto.result.UserGetResult;
import com.live_commerce.user.application.port.in.SearchUserUseCase;
import com.live_commerce.user.application.port.out.SearchUserPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchUserService implements SearchUserUseCase {

	private final SearchUserPort searchUserPort;

	@Override
	public List<UserGetResult> searchUser(UserSearchCondition condition) {
		return searchUserPort.searchUser(condition).stream()
			.map(UserGetResult::from)
			.toList();
	}
}
