package com.otavio.webrtc.service;

import com.otavio.webrtc.model.Room;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Camada SERVIDOR: mantém o catálogo de salas para rotear mensagens
 * de sinalização entre clientes.
 */
@Component
public class RoomRegistry {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();

    public Room getOrCreate(String roomId) {
        return rooms.computeIfAbsent(roomId, Room::new);
    }

    public Optional<Room> find(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public void removeIfEmpty(String roomId) {
        find(roomId).ifPresent(room -> {
            if (room.isEmpty()) {
                rooms.remove(roomId);
            }
        });
    }
}
