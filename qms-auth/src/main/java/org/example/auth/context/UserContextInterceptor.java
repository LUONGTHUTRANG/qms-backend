package org.example.auth.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.auth.entity.enums.Role;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = request.getHeader("X-Auth-User-Id");
        String branchIdStr = request.getHeader("X-Auth-Branch-Id");
        String username = request.getHeader("X-Auth-Username");
        String roleStr = request.getHeader("X-Auth-Role");

        if (userIdStr != null && !userIdStr.isEmpty()) {
            UserContext ctx = UserContext.builder()
                    .userId(Long.parseLong(userIdStr))
                    .username(username)
                    .build();

            if (branchIdStr != null && !branchIdStr.isEmpty()) {
                ctx.setBranchId(Long.parseLong(branchIdStr));
            }
            if (roleStr != null && !roleStr.isEmpty()) {
                ctx.setRole(Role.valueOf(roleStr));
            }

            UserContextHolder.setContext(ctx);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Clear ThreadLocal variable after the request is finished to prevent memory leaks in thread pooling
        UserContextHolder.clearContext();
    }
}

