package com.otavio.webrtc.config;

import com.otavio.webrtc.controller.SignalingHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Configura o endpoint de WebSocket usado para sinalização.
 * Mantém a separação Cliente-Servidor: o navegador conecta em /signal
 * apenas para trocar mensagens de coordenação (SDP/ICE).
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalingHandler signalingHandler;

    @Autowired
    public WebSocketConfig(SignalingHandler signalingHandler) {
        this.signalingHandler = signalingHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Mapeia o handler de sinalização em /signal, aceitando origens diversas para fins didáticos.
        registry.addHandler(signalingHandler, "/signal").setAllowedOrigins("*");
    }
}
