package com.live_commerce.gateway.filter;

import com.live_commerce.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter {

	private final JwtUtil jwtUtil;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		String path = exchange.getRequest().getURI().getPath();
		String method = exchange.getRequest().getMethod().name();

		if (isPublicPath(path, method)) {
			return chain.filter(exchange);
		}

		String token = jwtUtil.extractToken(exchange);

		if (token == null || !jwtUtil.validateToken(token)) {
			exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
			return exchange.getResponse().setComplete();
		}

		Claims claims = jwtUtil.parseClaims(token);
		String userId = claims.get("userId", String.class);
		String username = claims.get("username", String.class);
		String role = claims.get("role", String.class);
		String finalRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;

		ServerWebExchange modifiedExchange = exchange.mutate()
			.request(exchange.getRequest().mutate()
				.header("X-User-Id", userId)
				.header("X-User-Username", username)
				.header("X-User-Role", finalRole)
				.build())
			.build();

		return chain.filter(modifiedExchange);
	}

	private boolean isPublicPath(String path, String method) {
		return (path.equals("/api/v1/ai") && method.equalsIgnoreCase("POST")) ||
			path.startsWith("/api/v1/auth/") ||
			path.startsWith("/api/v2/auth/") ||
			(path.startsWith("/api/v1/products") && method.equalsIgnoreCase("GET")) ||
			(path.startsWith("/api/v1/inventories") && method.equalsIgnoreCase("GET")) ||
			path.startsWith("/api/v1/broadcasts") ||
			path.startsWith("/swagger-ui/") ||
			path.startsWith("/v3/api-docs") ||
			path.startsWith("/actuator");
	}


}

