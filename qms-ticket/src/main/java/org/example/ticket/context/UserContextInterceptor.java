package org.example.ticket.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
                    .role(roleStr)
                    .build();

            if (branchIdStr != null && !branchIdStr.isEmpty()) {
                ctx.setBranchId(Long.parseLong(branchIdStr));
            }

            UserContextHolder.setContext(ctx);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContextHolder.clearContext();
    }
}
