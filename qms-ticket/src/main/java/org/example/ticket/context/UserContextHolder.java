package org.example.ticket.context;

import org.example.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserContextHolder {
    private static final ThreadLocal<UserContext> userContext = new ThreadLocal<>();

    public static void setContext(UserContext context) {
        userContext.set(context);
    }

    public static UserContext getContext() {
        return userContext.get();
    }

    public static void clearContext() {
        userContext.remove();
    }

    public static Long getUserId() {
        UserContext ctx = getContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "User ID not found in context. Are you missing authentication?");
        }
        return ctx.getUserId();
    }

    public static Long getBranchId() {
        UserContext ctx = getContext();
        if (ctx == null || ctx.getBranchId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Branch ID not found in context. Are you missing authentication?");
        }
        return ctx.getBranchId();
    }
}
