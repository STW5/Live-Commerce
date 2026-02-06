package com.live_commerce.product.product.infrastructure.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class AuthenticationFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		String requestUri = request.getRequestURI();
		String method = request.getMethod();

		// 인증이 필요 없는 경로는 필터를 통과시킴
		if ((requestUri.startsWith("/api/v1/auth/") &&
			!requestUri.startsWith("/api/v1/auth/approve") &&
			!requestUri.equals("/api/v1/auth/logout")) ||
			requestUri.startsWith("/swagger-ui/") ||
			requestUri.startsWith("/v3/api-docs") ||
			requestUri.startsWith("/actuator") ||
			(requestUri.startsWith("/api/v1/products") && method.equals("GET")) ||
			(requestUri.startsWith("/api/v1/inventories") && method.equals("GET"))) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = request.getHeader("Authorization");
		if (token != null && token.startsWith("Bearer ")) {
			token = token.substring(7); // "Bearer " 제거
		}

		// 요청 헤더에서 사용자 정보 추출
		String userIdHeader = request.getHeader("X-User-Id");
		String username = request.getHeader("X-User-Username");
		String role = request.getHeader("X-User-Role");

		if (userIdHeader == null || username == null || role == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		UUID userId;
		try {
			userId = UUID.fromString(userIdHeader);
		} catch (IllegalArgumentException e) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		if (!role.startsWith("ROLE_")) {
			role = "ROLE_" + role;
		}

		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(role));
		UserDetails userDetails = new RequestUserDetails(userId, username, authorities);

		// 인증 정보 설정
		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(userDetails, token, authorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);

		// 필터 체인으로 넘김
		filterChain.doFilter(request, response);
	}
}
