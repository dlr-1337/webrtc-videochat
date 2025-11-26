package com.otavio.webrtc.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.otavio.webrtc.model.Participant;
import com.otavio.webrtc.model.Room;
import com.otavio.webrtc.model.SignalMessage;
import com.otavio.webrtc.service.RoomRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Handler de WebSocket - parte do SERVIDOR no estilo Cliente-Servidor.
 * - Recebe mensagens de sinalização (SDP/ICE) dos clientes.
 * - Gerencia associação de sessões WebSocket às salas.
 * - Encaminha mensagens para outros participantes da mesma sala.
 * Não faz streaming de mídia; WebRTC trata mídia peer-to-peer entre navegadores.
 */
@Component
public class SignalingHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SignalingHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoomRegistry roomRegistry;

    public SignalingHandler(RoomRegistry roomRegistry) {
        this.roomRegistry = roomRegistry;
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = (String) session.getAttributes().get("roomId");
        if (roomId != null) {
            roomRegistry.find(roomId).ifPresent(room -> {
                // Remove a sessão do catálogo e informa o peer remanescente.
                room.removeParticipant(session);
                notifyPeerLeft(room, session);
                roomRegistry.removeIfEmpty(roomId);
            });
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SignalMessage incoming;
        try {
            incoming = objectMapper.readValue(message.getPayload(), SignalMessage.class);
        } catch (JsonProcessingException e) {
            // Rejeita JSON quebrado para evitar NPEs e manter o protocolo simples.
            logger.warn("Mensagem inválida recebida", e);
            sendError(session, "Mensagem JSON inválida");
            return;
        }

        if (incoming.getRoomId() == null || incoming.getType() == null) {
            // Type define a ação de sinalização e roomId garante roteamento correto.
            sendError(session, "Mensagem precisa de type e roomId");
            return;
        }

        switch (incoming.getType()) {
            case "join" -> handleJoin(session, incoming);
            case "sdp", "ice" -> forwardToRoom(session, incoming, message.getPayload());
            default -> sendError(session, "Tipo não suportado: " + incoming.getType());
        }
    }

    private void handleJoin(WebSocketSession session, SignalMessage join) throws IOException {
        String roomId = join.getRoomId();
        String clientId = Optional.ofNullable(join.getSender()).orElse(UUID.randomUUID().toString());
        // Guarda no WebSocket os metadados que identificam a sessão na sala.
        session.getAttributes().put("roomId", roomId);
        session.getAttributes().put("clientId", clientId);

        Room room = roomRegistry.getOrCreate(roomId);
        Participant participant = new Participant(clientId, session);
        room.addParticipant(participant);

        // Confirmação didática para mostrar que o servidor apenas coordena a sala.
        ObjectNode joinAckPayload = objectMapper.createObjectNode()
                .put("participants", room.size())
                .put("message", "Sinalização conectada para a sala " + roomId);
        sendMessage(session, new SignalMessage("joined", roomId, "server", null, joinAckPayload));

        if (room.size() == 1) {
            // Primeiro participante espera: nada de oferta/answer ainda.
            sendMessage(session, new SignalMessage("waiting", roomId, "server", null,
                    objectMapper.createObjectNode().put("message", "Aguardando outra pessoa entrar...")));
            return;
        }

        // Há mais de um participante: notifica que podem iniciar negociação WebRTC.
        notifyReady(room);
    }

    private void forwardToRoom(WebSocketSession sender, SignalMessage signal, String rawJson) throws IOException {
        Room room = roomRegistry.find(signal.getRoomId()).orElse(null);
        if (room == null || !room.contains(sender)) {
            // Garantia de segurança: não encaminhar mensagens órfãs ou de outra sala.
            sendError(sender, "Você precisa entrar na sala antes de sinalizar");
            return;
        }

        for (Participant participant : room.getParticipants()) {
            if (!participant.isSameSession(sender) && participant.getSession().isOpen()) {
                participant.getSession().sendMessage(new TextMessage(rawJson));
            }
        }
    }

    private void notifyReady(Room room) throws IOException {
        Participant initiator = room.firstParticipant(); // o primeiro conectado cria o offer
        for (Participant participant : room.getParticipants()) {
            boolean isInitiator = initiator != null && participant == initiator;
            // Indicamos quem deve iniciar a troca de SDP para evitar colisão de offers.
            ObjectNode payload = objectMapper.createObjectNode().put("initiator", isInitiator);
            sendMessage(participant.getSession(),
                    new SignalMessage("ready", room.getId(), "server", null, payload));
        }
    }

    private void notifyPeerLeft(Room room, WebSocketSession departed) {
        ObjectNode payload = objectMapper.createObjectNode().put("message", "Um participante saiu da sala");
        SignalMessage signalMessage = new SignalMessage("peer-left",
                room.getId(), "server", null, payload);

        room.getParticipants().forEach(participant -> {
            if (!participant.isSameSession(departed) && participant.getSession().isOpen()) {
                try {
                    // Envia aviso para parar o fluxo e permitir renegociação futura.
                    sendMessage(participant.getSession(), signalMessage);
                } catch (IOException e) {
                    logger.warn("Falha ao notificar saída de participante", e);
                }
            }
        });
    }

    private void sendError(WebSocketSession session, String message) {
        try {
            // Normaliza formato de erro para o cliente manter UX consistente.
            ObjectNode payload = objectMapper.createObjectNode().put("message", message);
            sendMessage(session, new SignalMessage("error", null, "server", null, payload));
        } catch (IOException e) {
            logger.warn("Falha ao enviar erro para cliente", e);
        }
    }

    private void sendMessage(WebSocketSession session, SignalMessage message) throws IOException {
        // Centraliza serialização para manter o mesmo formato JSON em toda a aplicação.
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
    }
}
