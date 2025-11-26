# Video Chat WebRTC – Arquitetura Cliente-Servidor

Aplicação didática de videochat que usa **WebRTC** para mídia P2P no navegador (camada cliente) e **Spring Boot** no Java para servir os assets e atuar como servidor de **sinalização** via WebSocket.

## Visão geral da arquitetura
- **Servidor (Java/Spring Boot)**: expõe `/signal` (WebSocket) para troca de mensagens de sinalização (join, SDP, ICE). Gerencia salas (`Room`, `Participant`) e repassa mensagens entre clientes. Não trafega mídia.
- **Cliente (HTML/JS)**: usa `getUserMedia` e `RTCPeerConnection`; fala com o servidor apenas para sinalização, enquanto áudio/vídeo fluem diretamente entre navegadores.
- **Estáticos**: `src/main/resources/static` serve `index.html`, `css/style.css`, `js/webrtc.js`.

## Estrutura
```
webrtc-videochat/
├─ pom.xml
├─ src/main/java/com/otavio/webrtc/
│  ├─ VideoChatApplication.java           # bootstrap Spring Boot
│  ├─ config/WebSocketConfig.java         # registra endpoint /signal
│  ├─ controller/SignalingHandler.java    # roteia mensagens SDP/ICE entre peers
│  ├─ model/{SignalMessage, Room, Participant}.java
│  └─ service/RoomRegistry.java           # catálogo de salas
└─ src/main/resources/static/
   ├─ index.html
   ├─ css/style.css
   └─ js/webrtc.js                        # lógica WebRTC e sinalização
```

## Executar
Pré-requisitos: Java 17+ e Maven instalados.

1. Instalar dependências/compilar:
   ```bash
   mvn package
   ```
2. Subir servidor:
   ```bash
   mvn spring-boot:run
   ```
3. Acessar no navegador: `http://localhost:8080/`
4. Testar chamada: abra duas abas (ou dispositivos) com o mesmo Room ID, clique em “Entrar na sala” e permita câmera/mic.

## Acesso via celular
- Use o IP da máquina na LAN (ex.: `http://192.168.0.105:8080/`).
- Navegadores móveis podem exigir **HTTPS** para `getUserMedia`; se houver bloqueio, use um túnel como `ngrok http 8080` ou configure certificado local.

## Fluxo de sinalização (resumo)
1. Cliente A e B abrem o WebSocket `/signal` e enviam `join` com `roomId`.
2. Servidor registra na sala e, quando há dois participantes, envia `ready`; o primeiro conectado faz o `offer`.
3. Troca de `sdp` (offer/answer) e `ice` ocorre via servidor; `SignalingHandler` só repassa ao outro participante.
4. Mídia flui diretamente entre os peers (P2P).

## Notas
- `webrtc.js` inclui fallback para `crypto.randomUUID` e mensagem clara se `getUserMedia` não estiver disponível.
- O servidor não guarda estado de mídia; apenas metadados de sala e sessões WebSocket.
