package org.example.realtime.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Điểm kết nối (endpoint) mà Frontend (React, Vue) sẽ kết nối vào
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS(); // Hỗ trợ fallback nếu browser không hỗ trợ WebSocket
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Tiền tố cho các kênh (topic) mà Client có thể Subscribe để nghe thông báo
        registry.enableSimpleBroker("/topic");
        
        // Tiền tố cho các endpoint mà Client gửi tin nhắn LÊN Server (Giả sử Client muốn chủ động chat)
        registry.setApplicationDestinationPrefixes("/app");
    }
}

