package com.otavio.webrtc.model;

import org.springframework.web.socket.WebSocketSession;

/**
 * Representa um CLIENTE conectado no contexto de uma sala.
 * O servidor usa apenas para sinalizar (não carrega mídia).
 */
public class Participant {

    private final String clientId;
    private final WebSocketSession session;

    public Participant(String clientId, WebSocketSession session) {
        this.clientId = clientId;
        this.session = session;
    }

    public String getClientId() {
        return clientId;
    }

    public WebSocketSession getSession() {
        return session;
    }

    public boolean isSameSession(WebSocketSession other) {
        return this.session.getId().equals(other.getId());
    }
}
