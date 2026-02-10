package com.example.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple memory-based message broker to carry messages back to the
        // client on destinations prefixed with /topic
        config.enableSimpleBroker("/topic");
        // Designate the /app prefix for messages that are bound for methods annotated
        // with @MessageMapping
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the "/ws" endpoint, enabling the SockJS protocol.
        // Allow CORS from frontend origin
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        // .withSockJS(); // Check if user wants SockJS. React usually works fine with
        // standard STOMP over WS.
        // Keeping it simple with standard WebSocket for now to avoid SockJS client
        // dependency unless needed.
    }
}
