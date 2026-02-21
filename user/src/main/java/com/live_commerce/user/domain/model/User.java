package com.live_commerce.user.domain.model;

import java.util.UUID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

	private UUID userId;        // null before save, populated after reconstitute()
	private String username;
	private String password;
	private String email;
	private String nickname;
	private boolean alarmConsent;
	private UserRole userRole;
	private boolean approved;
	private boolean deletedStatus;
	private String deletedBy;

	// 신규 도메인 객체용 (DB 저장 전, ID 없음)
	public static User of(String username, String password, String email, String nickname,
		boolean alarmConsent, UserRole userRole, boolean approved) {
		User user = new User();
		user.username = username;
		user.password = password;
		user.email = email;
		user.nickname = nickname;
		user.alarmConsent = alarmConsent;
		user.userRole = userRole;
		user.approved = approved;
		user.deletedStatus = false;
		return user;
	}

	// DB 재구성용 (ID 포함)
	public static User reconstitute(UUID userId, String username, String password, String email,
		String nickname, boolean alarmConsent, UserRole userRole, boolean approved,
		boolean deletedStatus, String deletedBy) {
		User user = new User();
		user.userId = userId;
		user.username = username;
		user.password = password;
		user.email = email;
		user.nickname = nickname;
		user.alarmConsent = alarmConsent;
		user.userRole = userRole;
		user.approved = approved;
		user.deletedStatus = deletedStatus;
		user.deletedBy = deletedBy;
		return user;
	}

	public void updateUser(String password, String email, String nickname,
		boolean alarmConsent, UserRole userRole) {
		this.password = password;
		this.email = email;
		this.nickname = nickname;
		this.alarmConsent = alarmConsent;
		this.userRole = userRole;
	}

	public void changePassword(String newEncodedPassword) {
		this.password = newEncodedPassword;
	}

	public boolean isApproved() {
		return approved;
	}

	public void approve() {
		this.approved = true;
	}

	public boolean isDeleted() {
		return deletedStatus;
	}

	public void softDelete(String deletedBy) {
		this.deletedStatus = true;
		this.deletedBy = deletedBy;
	}
}
