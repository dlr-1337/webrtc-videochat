package com.otavio.webrtc.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * DTO de sinalização (lado SERVIDOR do estilo Cliente-Servidor).
 * Circula entre clientes via servidor e contém metadados da sala.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SignalMessage {

    private String type; // join, ready, sdp, ice, peer-left etc.
    private String roomId;
    private String sender;
    private String target;
    private JsonNode payload;

    public SignalMessage() {
        // Necessário para desserialização automática do Jackson.
    }

    public SignalMessage(String type, String roomId, String sender, String target, JsonNode payload) {
        this.type = type;
        this.roomId = roomId;
        this.sender = sender;
        this.target = target;
        this.payload = payload;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }
}
