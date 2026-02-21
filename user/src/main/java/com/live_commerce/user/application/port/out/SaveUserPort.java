package com.live_commerce.user.application.port.out;

import com.live_commerce.user.domain.model.User;

public interface SaveUserPort {
	User save(User user);  // 반환값 필수 — DB 생성 UUID 포함
}
