package com.otavio.webrtc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Camada SERVIDOR do estilo Cliente-Servidor:
 * - Sobe o servidor HTTP e WebSocket para sinalização WebRTC.
 * - Não transporta mídia; apenas coordena os peers.
 */
@SpringBootApplication
public class VideoChatApplication {

    public static void main(String[] args) {
        // Sobe a aplicação Spring Boot que expõe o endpoint de sinalização WebSocket.
        SpringApplication.run(VideoChatApplication.class, args);
    }
}
