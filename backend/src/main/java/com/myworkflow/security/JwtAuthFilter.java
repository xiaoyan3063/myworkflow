package com.myworkflow.security;

import com.myworkflow.common.context.UserContext;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token) && jwtTokenProvider.validate(token)) {
                Claims claims = jwtTokenProvider.parse(token);
                Long userId = Long.valueOf(claims.getSubject());
                String username = (String) claims.get("username");
                Long tenantId = claims.get("tenantId") == null ? 0L : Long.valueOf(claims.get("tenantId").toString());
                boolean admin = Boolean.TRUE.equals(claims.get("admin"));

                UserContext ctx = new UserContext();
                ctx.setUserId(userId);
                ctx.setUsername(username);
                ctx.setTenantId(tenantId);
                ctx.setAdmin(admin);
                UserContext.set(ctx);

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        userId, null,
                        Collections.singletonList(new SimpleGrantedAuthority(admin ? "ROLE_ADMIN" : "ROLE_USER")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return request.getHeader("X-Access-Token");
    }
}
