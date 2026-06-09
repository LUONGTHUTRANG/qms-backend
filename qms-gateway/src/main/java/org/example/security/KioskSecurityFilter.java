package org.example.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

@Component
public class KioskSecurityFilter implements GlobalFilter, Ordered {

    // Lấy danh sách IP từ application.yml
    @Value("${security.allowed-kiosk-ips:127.0.0.1,0:0:0:0:0:0:0:1}")
    private List<String> allowedIps;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Chỉ kiểm tra IP đối với API tạo vé của Kiosk
        if (path.equals("/api/v1/ticket/tickets/create")) {
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();

            if (remoteAddress != null) {
                String clientIp = remoteAddress.getAddress().getHostAddress();

                if (!allowedIps.contains(clientIp)) {
                    // Nếu IP không nằm trong danh sách trắng -> Bắn lỗi 403 ngay tại Gateway
                    exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                    return exchange.getResponse().setComplete();
                }
            }
        }

        // Trùng khớp IP hoặc gọi API khác -> Cho phép đi tiếp
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -2; // Độ ưu tiên cao nhất, chạy đầu tiên khi request vừa tới Gateway
    }
}