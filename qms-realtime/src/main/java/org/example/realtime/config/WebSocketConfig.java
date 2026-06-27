package org.example.realtime.config;

import org.example.realtime.security.GatewayHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final GatewayHandshakeInterceptor gatewayHandshakeInterceptor;
    private final List<String> allowedOriginPatterns;

    public WebSocketConfig(
            GatewayHandshakeInterceptor gatewayHandshakeInterceptor,
            @Value("${app.websocket.allowed-origin-patterns:http://localhost:8080,http://localhost:3000,http://localhost:5173}")
            String allowedOriginPatterns) {
        this.gatewayHandshakeInterceptor = gatewayHandshakeInterceptor;
        this.allowedOriginPatterns = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Realtime endpoint is expected to be reached through the gateway and still supports SockJS fallback.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new))
                .addInterceptors(gatewayHandshakeInterceptor)
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}
