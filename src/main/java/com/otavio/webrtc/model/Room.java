package com.otavio.webrtc.model;

import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Representa uma sala lógica. Faz parte da camada SERVIDOR no estilo Cliente-Servidor.
 * Guarda apenas metadados e sessões WebSocket para rotear mensagens de sinalização.
 */
public class Room {
    private final String id;
    private final List<Participant> participants = new CopyOnWriteArrayList<>();

    public Room(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void addParticipant(Participant participant) {
        // Evita duplicar a mesma sessão caso o navegador tente reconectar.
        boolean alreadyPresent = participants.stream()
                .anyMatch(p -> p.isSameSession(participant.getSession()));
        if (!alreadyPresent) {
            participants.add(participant);
        }
    }

    public void removeParticipant(WebSocketSession session) {
        // Remove pelo ID da sessão, não apenas por objeto, garantindo idempotência.
        participants.removeIf(p -> p.isSameSession(session));
    }

    public List<Participant> getParticipants() {
        return Collections.unmodifiableList(participants);
    }

    public Participant firstParticipant() {
        // Convenção simples: o primeiro a entrar inicia a chamada (offer).
        return participants.isEmpty() ? null : participants.get(0);
    }

    public boolean isEmpty() {
        return participants.isEmpty();
    }

    public boolean contains(WebSocketSession session) {
        return participants.stream().anyMatch(p -> p.isSameSession(session));
    }

    public int size() {
        return participants.size();
    }
}
