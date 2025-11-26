// Camada CLIENTE no estilo Cliente-Servidor:
// - Obtém mídia local, cria RTCPeerConnection e envia sinais via servidor.
// - Apenas mensagens de sinalização (SDP/ICE) passam pelo servidor Java; áudio/vídeo fluem direto entre navegadores.

// Referências aos elementos de UI
const roomInput = document.getElementById('room');
const joinBtn = document.getElementById('joinBtn');
const statusEl = document.getElementById('status');
const localVideo = document.getElementById('localVideo');
const remoteVideo = document.getElementById('remoteVideo');

// Fallback simples para navegadores mobile antigos que não têm randomUUID.
let clientId = (self.crypto && self.crypto.randomUUID)
    ? self.crypto.randomUUID()
    : `client-${Math.random().toString(36).slice(2)}`;
let roomId = null;
let websocket = null;
let peerConnection = null;
let localStream = null;
let isInitiator = false;

// Clique no botão "Entrar na sala" inicia todo o fluxo.
joinBtn.addEventListener('click', () => joinRoom());

async function joinRoom() {
    roomId = roomInput.value.trim();
    if (!roomId) {
        setStatus('Informe um Room ID.');
        return;
    }

    // Na UX, bloqueamos botão para evitar cliques múltiplos até estabilizar.
    joinBtn.disabled = true;
    try {
        await startLocalMedia();
        createPeerConnection();
        connectWebSocket();
        setStatus('Abrindo WebSocket de sinalização...');
    } catch (err) {
        console.error('[joinRoom]', err);
        setStatus('Erro ao iniciar: ' + err.message);
        joinBtn.disabled = false;
    }
}

async function startLocalMedia() {
    // Verifica suporte: alguns navegadores móveis desativam em HTTP.
    const gum = navigator.mediaDevices?.getUserMedia
        || navigator.getUserMedia
        || navigator.webkitGetUserMedia
        || navigator.mozGetUserMedia;

    if (!gum) {
        throw new Error('Este navegador não expõe getUserMedia (precisa de HTTPS ou browser compatível).');
    }

    // WebRTC cuida de capturar mídia no CLIENTE.
    localStream = await gum.call(navigator.mediaDevices || navigator, {video: true, audio: true});
    localVideo.srcObject = localStream;
}

function createPeerConnection() {
    // Cria o PeerConnection com um STUN público para descobrir endereços/portas acessíveis.
    peerConnection = new RTCPeerConnection({
        iceServers: [
            {urls: 'stun:stun.l.google.com:19302'}
        ]
    });

    // Envia ICE para o servidor de sinalização que repassará ao peer.
    peerConnection.onicecandidate = event => {
        if (event.candidate) {
            sendSignal('ice', event.candidate);
        }
    };

    peerConnection.ontrack = event => {
        // Fluxo de mídia direto cliente-cliente; o servidor não toca aqui.
        remoteVideo.srcObject = event.streams[0];
    };

    peerConnection.onconnectionstatechange = () => {
        // Mostra estados como "connected", "failed" ou "disconnected".
        setStatus('Conexão WebRTC: ' + peerConnection.connectionState);
    };

    // Adiciona todas as tracks locais para serem ofertadas via SDP.
    localStream.getTracks().forEach(track => peerConnection.addTrack(track, localStream));
}

function connectWebSocket() {
    const wsProtocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    // Conecta ao servidor de sinalização Java (WebSocket).
    websocket = new WebSocket(`${wsProtocol}://${window.location.host}/signal`);

    websocket.onopen = () => {
        setStatus('WebSocket conectado, entrando na sala...');
        sendSignal('join');
    };

    websocket.onmessage = async (event) => {
        const message = JSON.parse(event.data);
        switch (message.type) {
            case 'joined':
                setStatus(message.payload?.message || 'Conectado à sala.');
                break;
            case 'waiting':
                setStatus(message.payload?.message || 'Aguardando outro participante...');
                break;
            case 'ready':
                isInitiator = Boolean(message.payload?.initiator);
                setStatus(isInitiator ? 'Você fará o offer (caller).' : 'Aguardando offer do peer.');
                if (isInitiator) {
                    // Apenas o iniciador cria offer, o outro apenas responde.
                    await createAndSendOffer();
                }
                break;
            case 'sdp':
                await handleRemoteDescription(message.payload);
                break;
            case 'ice':
                if (message.payload) {
                    await peerConnection.addIceCandidate(message.payload);
                }
                break;
            case 'peer-left':
                setStatus(message.payload?.message || 'Participante saiu.');
                resetRemoteStream();
                break;
            case 'error':
                setStatus(message.payload?.message || 'Erro de sinalização.');
                break;
            default:
                console.warn('Mensagem desconhecida', message);
        }
    };

    websocket.onclose = () => {
        setStatus('WebSocket fechado.');
        joinBtn.disabled = false;
    };
    websocket.onerror = () => {
        setStatus('Erro no WebSocket.');
        joinBtn.disabled = false;
    };
}

async function createAndSendOffer() {
    // Offer inclui descrição SDP com codecs e endereços possíveis.
    const offer = await peerConnection.createOffer();
    await peerConnection.setLocalDescription(offer);
    sendSignal('sdp', offer);
}

async function handleRemoteDescription(desc) {
    if (!desc) {
        return;
    }
    const remoteDesc = new RTCSessionDescription(desc);

    if (remoteDesc.type === 'offer') {
        // Recebemos offer: aplicamos, criamos answer e devolvemos via sinalização.
        await peerConnection.setRemoteDescription(remoteDesc);
        const answer = await peerConnection.createAnswer();
        await peerConnection.setLocalDescription(answer);
        sendSignal('sdp', answer);
        return;
    }

    await peerConnection.setRemoteDescription(remoteDesc);
}

function sendSignal(type, payload = null) {
    if (!websocket || websocket.readyState !== WebSocket.OPEN) {
        return;
    }
    // Mensagens de sinalização são leves; apenas metadados para coordenar peers.
    const message = {
        type,
        roomId,
        sender: clientId,
        payload
    };
    websocket.send(JSON.stringify(message));
}

function resetRemoteStream() {
    // Encerra tracks do peer remoto para liberar recursos (ex: ao sair da sala).
    if (remoteVideo.srcObject) {
        remoteVideo.srcObject.getTracks().forEach(track => track.stop());
    }
    remoteVideo.srcObject = null;
}

function setStatus(text) {
    statusEl.textContent = text;
    console.log('[status]', text);
}
