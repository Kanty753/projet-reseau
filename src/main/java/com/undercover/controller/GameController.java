package com.undercover.controller;

import com.google.gson.*;
import com.undercover.model.*;
import com.undercover.network.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * GameController - Controleur principal du jeu
 * Gere la logique de jeu et orchestre les communications via UDP/TCP
 * 
 * SYNCHRONISATION DES TOURS:
 * - Le serveur (host) gere le timer central et broadcast a tous les clients
 * - Chaque joueur a 40s pour parler (WORD_TIME_SECONDS)
 * - Si timeout, le tour passe automatiquement au joueur suivant
 * - Les clients recoivent TURN_START, TIMER_SYNC, TURN_END pour rester synchronises
 */
public class GameController {
    
    private final NetworkBridge networkBridge;
    private final Gson gson;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduler;
    
    private GameSession session;
    private Player localPlayer;
    private boolean isHost;
    private String myWord;
    
    // Port UDP pour les messages de jeu (separe du port TCP)
    private int gameUdpPort;
    private static final int UDP_PORT_OFFSET = 1000; // UDP = TCP + 1000
    
    // Listeners pour l'UI
    private final List<GameEventListener> listeners;
    
    // Mots secrets
    private List<String[]> wordPairs;
    
    // IDs des messages deja traites (pour eviter les doublons)
    private final Set<String> processedMessageIds = ConcurrentHashMap.newKeySet();
    
    // Timer de synchronisation (host uniquement)
    private ScheduledFuture<?> currentTimer;
    private int currentTimerSeconds;
    private long timerStartTime;
    
    public interface GameEventListener {
        void onPlayersUpdated(List<Player> players);
        void onGameStarted(Role role, String word);
        void onPhaseChanged(GameSession.State state);
        void onMessageReceived(GameMessage message);
        void onGameEnded(String message);
        void onServerDiscovered(List<NetworkBridge.ServerInfo> servers);
        void onConnectionStatusChanged(boolean connected, String message);
        // Nouveaux evenements pour la synchronisation des tours
        default void onTurnChanged(String currentPlayerId, int remainingSeconds, List<String> turnOrder) {}
        default void onTimerSync(int remainingSeconds) {}
    }
    
    public GameController() {
        this.networkBridge = new NetworkBridge();
        this.gson = new GsonBuilder().create();
        this.executor = Executors.newCachedThreadPool();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.listeners = new CopyOnWriteArrayList<>();
        loadWordPairs();
    }
    
    private void loadWordPairs() {
        wordPairs = new ArrayList<>();
        try {
            Path path = Paths.get("data", "word_pairs.txt");
            if (Files.exists(path)) {
                Files.lines(path).forEach(line -> {
                    String[] parts = line.split(",");
                    if (parts.length == 2) {
                        wordPairs.add(new String[]{parts[0].trim(), parts[1].trim()});
                    }
                });
            }
        } catch (IOException e) {
            // Mots par defaut
        }
        
        // Ajouter des paires par defaut si liste vide
        if (wordPairs.isEmpty()) {
            wordPairs.addAll(Arrays.asList(
                new String[]{"Chat", "Chien"},
                new String[]{"Pomme", "Poire"},
                new String[]{"Soleil", "Lune"},
                new String[]{"Voiture", "Moto"},
                new String[]{"Football", "Basketball"},
                new String[]{"Pizza", "Hamburger"},
                new String[]{"Guitare", "Piano"},
                new String[]{"Ete", "Hiver"},
                new String[]{"Cafe", "The"},
                new String[]{"Montagne", "Mer"}
            ));
        }
    }
    
    // ===== CREATION DE SERVEUR =====
    
    public void createServer(String playerName, String sessionName, int port, int maxPlayers) {
        createServer(playerName, sessionName, port, maxPlayers, false);
    }
    
    public void createServer(String playerName, String sessionName, int port, int maxPlayers, boolean localhostMode) {
        executor.submit(() -> {
            try {
                String localIp;
                
                // Mode localhost : utiliser 127.0.0.1
                if (localhostMode) {
                    localIp = "127.0.0.1";
                    networkBridge.setLocalhostMode(true);
                } else {
                    localIp = networkBridge.getLocalIp();
                    networkBridge.setLocalhostMode(false);
                }
                
                // Creer la session
                session = new GameSession(sessionName, localIp, port, maxPlayers);
                
                // Creer le joueur local (hote)
                localPlayer = new Player(playerName, localIp, port);
                localPlayer.setHost(true);
                localPlayer.setReady(true);
                session.addPlayer(localPlayer);
                
                isHost = true;
                
                // Port UDP pour les messages de jeu (TCP port + offset)
                gameUdpPort = port + UDP_PORT_OFFSET;
                localPlayer.setUdpPort(gameUdpPort);
                
        // Demarrer le broadcast via Bash (UDP ou localhost selon le mode)
                List<String> playerNames = new java.util.ArrayList<>();
                playerNames.add(localPlayer.getName());
                networkBridge.startServerBroadcast(localIp, port, sessionName, maxPlayers, 1, playerNames);
                
                // Demarrer l'ecoute TCP pour les connexions clients (JOIN uniquement)
                networkBridge.startTcpServer(port, this::handleIncomingTcpMessage);
                
                // Demarrer l'ecoute UDP pour les messages de jeu (rapide, sans latence)
                networkBridge.startUdpServer(gameUdpPort, this::handleIncomingGameMessage);
                
                String modeInfo = localhostMode ? " (Mode Local)" : "";
                notifyConnectionStatus(true, "Serveur cree sur " + localIp + ":" + port + " (UDP:" + gameUdpPort + ")" + modeInfo);
                notifyPlayersUpdated();
                
            } catch (Exception e) {
                notifyConnectionStatus(false, "Erreur: " + e.getMessage());
            }
        });
    }
    
    // ===== REJOINDRE UN SERVEUR =====
    
    public void startServerDiscovery() {
        startServerDiscovery(true);  // Par defaut, inclure localhost
    }
    
    public void startServerDiscovery(boolean includeLocalhost) {
        networkBridge.startServerDiscovery(servers -> {
            for (GameEventListener listener : listeners) {
                listener.onServerDiscovered(servers);
            }
        }, includeLocalhost);
    }
    
    public void joinServer(String playerName, NetworkBridge.ServerInfo server) {
        System.out.println("=== Tentative de connexion au serveur: " + server.ip + ":" + server.port);
        
        executor.submit(() -> {
            try {
                // Pour localhost, utiliser 127.0.0.1
                String localIp = server.ip.equals("127.0.0.1") ? "127.0.0.1" : networkBridge.getLocalIp();
                int localPort = 5100 + new Random().nextInt(900); // Eviter conflit avec le serveur
                
                // Port UDP pour ce client
                gameUdpPort = localPort + UDP_PORT_OFFSET;
                
                System.out.println("Client local: " + localIp + ":" + localPort + " (UDP:" + gameUdpPort + ")");
                
                // Creer le joueur local
                localPlayer = new Player(playerName, localIp, localPort);
                localPlayer.setUdpPort(gameUdpPort);
                isHost = false;
                
                // Demarrer l'ecoute TCP pour les connexions (JOIN uniquement)
                networkBridge.startTcpServer(localPort, this::handleIncomingTcpMessage);
                System.out.println("Client TCP server started on port " + localPort);
                
                // Demarrer l'ecoute UDP pour les messages de jeu
                networkBridge.startUdpServer(gameUdpPort, this::handleIncomingGameMessage);
                System.out.println("Client UDP server started on port " + gameUdpPort);
                
                // Petite pause pour s'assurer que le serveur est pret
                Thread.sleep(100);
                
                // Envoyer la demande de connexion
                JsonObject joinRequest = new JsonObject();
                joinRequest.addProperty("type", "JOIN_REQUEST");
                joinRequest.addProperty("playerName", playerName);
                joinRequest.addProperty("playerIp", localIp);
                joinRequest.addProperty("playerPort", localPort);
                joinRequest.addProperty("playerUdpPort", gameUdpPort);  // Port UDP pour les messages de jeu
                joinRequest.addProperty("playerId", localPlayer.getId());
                
                System.out.println("Envoi de JOIN_REQUEST a " + server.ip + ":" + server.port);
                
                // Envoyer la demande via TCP (connexion fiable)
                networkBridge.sendMessage(server.ip, server.port, joinRequest)
                    .thenAccept(response -> {
                        System.out.println("Reponse recue: " + response);
                        if (response.has("success") && response.get("success").getAsBoolean()) {
                            // La reponse directe du socket indique que le message a ete recu
                            // Le JOIN_ACCEPTED viendra separement via handleIncomingTcpMessage
                            System.out.println("Message envoye avec succes, attente de confirmation...");
                        } else {
                            String error = response.has("error") ? response.get("error").getAsString() : "Connexion refusee";
                            System.out.println("Erreur: " + error);
                            notifyConnectionStatus(false, error);
                        }
                    })
                    .exceptionally(e -> {
                        System.err.println("Erreur d'envoi: " + e.getMessage());
                        notifyConnectionStatus(false, "Erreur de connexion: " + e.getMessage());
                        return null;
                    });
                
            } catch (Exception e) {
                System.err.println("Exception: " + e.getMessage());
                e.printStackTrace();
                notifyConnectionStatus(false, "Erreur: " + e.getMessage());
            }
        });
    }
    
    // ===== GESTION DES MESSAGES ENTRANTS =====
    
    /**
     * Handler pour les messages TCP (connexions uniquement - fiable mais lent)
     */
    private void handleIncomingTcpMessage(JsonObject message) {
        String type = message.has("type") ? message.get("type").getAsString() : "";
        System.out.println("[TCP] Message recu: " + type);
        
        switch (type) {
            case "JOIN_REQUEST" -> handleJoinRequest(message);
            case "JOIN_ACCEPTED" -> handleJoinAccepted(message);
            case "JOIN_REJECTED" -> handleJoinRejected(message);
            default -> {
                // Si message de jeu recu en TCP (fallback), traiter quand meme
                handleIncomingGameMessage(message);
            }
        }
    }
    
    /**
     * Handler pour les messages UDP (messages de jeu - rapide, sans latence)
     */
    private void handleIncomingGameMessage(JsonObject message) {
        String type = message.has("type") ? message.get("type").getAsString() : "";
        
        // Generer un ID unique pour eviter les doublons
        String msgId = type + "_" + 
            (message.has("playerId") ? message.get("playerId").getAsString() : "") + "_" +
            (message.has("timestamp") ? message.get("timestamp").getAsString() : System.currentTimeMillis());
        
        // Verifier si le message a deja ete traite
        if (processedMessageIds.contains(msgId)) {
            return;
        }
        processedMessageIds.add(msgId);
        
        // Nettoyer les anciens IDs (garder max 1000)
        if (processedMessageIds.size() > 1000) {
            Iterator<String> it = processedMessageIds.iterator();
            for (int i = 0; i < 500 && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
        }
        
        switch (type) {
            case "PLAYER_LIST" -> handlePlayerList(message);
            case "GAME_START" -> handleGameStart(message);
            case "PHASE_CHANGE" -> handlePhaseChange(message);
            case "WORD_SPOKEN" -> handleWordSpoken(message);
            case "CHAT" -> handleChat(message);
            case "VOTE" -> handleVote(message);
            case "GAME_END" -> handleGameEnd(message);
            case "GUESS" -> handleGuess(message);
            case "PING" -> handlePing(message);
            // Nouveaux messages pour la synchronisation des tours
            case "TURN_START" -> handleTurnStart(message);
            case "TIMER_SYNC" -> handleTimerSync(message);
            case "TURN_TIMEOUT" -> handleTurnTimeout(message);
            case "ROUND_END" -> handleRoundEnd(message);
        }
    }
    
    /**
     * Gere les pings pour verifier la connexion
     */
    private void handlePing(JsonObject message) {
        // Repondre au ping si necessaire
        if (message.has("_senderIp") && message.has("_senderPort")) {
            JsonObject pong = new JsonObject();
            pong.addProperty("type", "PONG");
            pong.addProperty("timestamp", System.currentTimeMillis());
            networkBridge.sendUdpMessage(
                message.get("_senderIp").getAsString(),
                message.get("_senderPort").getAsInt(),
                pong
            );
        }
    }
    
    private void handleJoinRequest(JsonObject message) {
        System.out.println("=== Reception JOIN_REQUEST: " + message);
        
        if (!isHost || session == null) {
            System.out.println("Ignore: isHost=" + isHost + ", session=" + session);
            return;
        }
        
        String playerName = message.get("playerName").getAsString();
        String playerIp = message.get("playerIp").getAsString();
        int playerPort = message.get("playerPort").getAsInt();
        int playerUdpPort = message.has("playerUdpPort") ? 
            message.get("playerUdpPort").getAsInt() : playerPort + UDP_PORT_OFFSET;
        String playerId = message.has("playerId") ? 
            message.get("playerId").getAsString() : null;
        
        System.out.println("Nouveau joueur: " + playerName + " (" + playerIp + ":" + playerPort + ", UDP:" + playerUdpPort + ", ID:" + playerId + ")");
        
        // Verifier si le pseudo est deja utilise
        for (Player p : session.getPlayers()) {
            if (p.getName().equalsIgnoreCase(playerName)) {
                System.out.println("Pseudo deja utilise: " + playerName);
                // Envoyer un refus
                JsonObject response = new JsonObject();
                response.addProperty("type", "JOIN_REJECTED");
                response.addProperty("success", false);
                response.addProperty("reason", "Ce pseudo est deja utilise dans cette partie");
                networkBridge.sendMessage(playerIp, playerPort, response);
                return;
            }
        }
        
        // Utiliser l'ID envoye par le client pour garder la coherence
        Player newPlayer;
        if (playerId != null) {
            newPlayer = new Player(playerId, playerName, playerIp, playerPort, playerUdpPort);
        } else {
            newPlayer = new Player(playerName, playerIp, playerPort, playerUdpPort);
        }
        
        if (session.addPlayer(newPlayer)) {
            System.out.println("Joueur ajoute avec succes");
            
            // Envoyer confirmation au nouveau joueur via TCP (fiable)
            JsonObject response = new JsonObject();
            response.addProperty("type", "JOIN_ACCEPTED");
            response.addProperty("success", true);
            response.addProperty("sessionId", session.getId());
            response.addProperty("sessionName", session.getName());
            response.addProperty("hostIp", session.getHostIp());
            response.addProperty("hostUdpPort", gameUdpPort);  // Port UDP de l'hote
            response.addProperty("playerId", newPlayer.getId());  // ID pour confirmation
            
            System.out.println("Envoi JOIN_ACCEPTED a " + playerIp + ":" + playerPort);
            
            networkBridge.sendMessage(playerIp, playerPort, response)
                .thenAccept(r -> System.out.println("Confirmation envoyee: " + r))
                .exceptionally(e -> {
                    System.err.println("Erreur envoi confirmation: " + e.getMessage());
                    return null;
                });
            
            // Mettre a jour le broadcast avec le nouveau nombre de joueurs et leurs noms
            List<String> playerNames = new java.util.ArrayList<>();
            for (Player p : session.getPlayers()) {
                playerNames.add(p.getName());
            }
            networkBridge.startServerBroadcast(
                session.getHostIp(), 
                session.getHostPort(), 
                session.getName(),
                session.getMaxPlayers(),
                session.getPlayers().size(),
                playerNames
            );
            
            // Broadcaster la liste mise a jour
            broadcastPlayerList();
            notifyPlayersUpdated();
        } else {
            System.out.println("Echec de l'ajout du joueur");
        }
    }
    
    // Port UDP de l'hote (pour les clients)
    private int hostUdpPort;
    // IP de l'hote (pour les clients)
    private String hostIp;
    
    private void handleJoinAccepted(JsonObject message) {
        System.out.println("=== JOIN_ACCEPTED recu: " + message);
        String sessionName = message.get("sessionName").getAsString();
        
        // Recuperer le port UDP et l'IP de l'hote
        if (message.has("hostUdpPort")) {
            hostUdpPort = message.get("hostUdpPort").getAsInt();
            System.out.println("Port UDP de l'hote: " + hostUdpPort);
        }
        if (message.has("hostIp")) {
            hostIp = message.get("hostIp").getAsString();
            System.out.println("IP de l'hote: " + hostIp);
        }
        
        // Creer une session locale pour le client
        if (session == null) {
            String sessionId = message.has("sessionId") ? message.get("sessionId").getAsString() : "unknown";
            session = new GameSession(sessionName, hostIp != null ? hostIp : "127.0.0.1", 5000, 8);
        }
        
        notifyConnectionStatus(true, "Connecte a " + sessionName + " (UDP actif)");
    }
    
    private void handleJoinRejected(JsonObject message) {
        String reason = message.has("reason") ? message.get("reason").getAsString() : "Connexion refusée";
        System.out.println("=== JOIN_REJECTED: " + reason);
        notifyConnectionStatus(false, reason);
    }
    
    private void handlePlayerList(JsonObject message) {
        JsonArray playersArray = message.getAsJsonArray("players");
        List<Player> players = new ArrayList<>();
        
        for (JsonElement elem : playersArray) {
            JsonObject p = elem.getAsJsonObject();
            String playerId = p.get("id").getAsString();
            int udpPort = p.has("udpPort") ? p.get("udpPort").getAsInt() : p.get("port").getAsInt() + UDP_PORT_OFFSET;
            
            // Utiliser le constructeur avec ID explicite pour conserver l'ID du serveur
            Player player = new Player(
                playerId,
                p.get("name").getAsString(),
                p.get("ip").getAsString(),
                p.get("port").getAsInt(),
                udpPort
            );
            player.setHost(p.get("isHost").getAsBoolean());
            player.setAlive(p.get("alive").getAsBoolean());
            players.add(player);
            
            // Mettre a jour l'ID du localPlayer si c'est nous
            if (localPlayer != null && localPlayer.getName().equals(player.getName()) 
                && localPlayer.getIpAddress().equals(player.getIpAddress())) {
                // Remplacer le localPlayer avec le bon ID du serveur
                localPlayer = player;
            }
        }
        
        if (session != null) {
            // Mettre a jour la session locale avec les joueurs recus
            session.setPlayers(players);
        }
        
        notifyPlayersUpdated();
    }
    
    private void handleGameStart(JsonObject message) {
        String roleStr = message.get("role").getAsString();
        String word = message.has("word") && !message.get("word").isJsonNull() ? 
            message.get("word").getAsString() : null;
        
        Role role = Role.valueOf(roleStr);
        localPlayer.setRole(role);
        myWord = word;
        
        for (GameEventListener listener : listeners) {
            listener.onGameStarted(role, word);
        }
    }
    
    private void handlePhaseChange(JsonObject message) {
        String stateStr = message.get("state").getAsString();
        GameSession.State state = GameSession.State.valueOf(stateStr);
        
        if (session != null) {
            session.setState(state);
        }
        
        for (GameEventListener listener : listeners) {
            listener.onPhaseChanged(state);
        }
    }
    
    private void handleWordSpoken(JsonObject message) {
        String senderId = message.get("playerId").getAsString();
        String senderName = message.get("playerName").getAsString();
        String word = message.get("word").getAsString();
        
        // Si on est l'hote et que le message vient d'un client, relayer a tous
        if (isHost && session != null && !senderId.equals(localPlayer.getId())) {
            // Verifier si c'est bien le tour de ce joueur
            if (!session.isPlayerTurn(senderId)) {
                System.out.println("Ignore mot de " + senderName + " - ce n'est pas son tour");
                return;
            }
            
            // Traiter localement
            session.speakWord(senderId, word);
            // Relayer le message a tous les joueurs SAUF l'expediteur (il l'a deja affiche)
            broadcastToAllExcept(message, senderId);
            
            // Passer au joueur suivant
            advanceToNextTurn();
        }
        
        // Ne pas afficher si c'est notre propre message (deja affiche dans speakWord)
        if (senderId.equals(localPlayer.getId())) {
            return;
        }
        
        GameMessage gameMsg = new GameMessage(senderId, senderName, word, GameMessage.Type.WORD);
        
        for (GameEventListener listener : listeners) {
            listener.onMessageReceived(gameMsg);
        }
    }
    
    private void handleChat(JsonObject message) {
        String senderId = message.get("playerId").getAsString();
        String senderName = message.get("playerName").getAsString();
        String content = message.get("message").getAsString();
        
        // Si on est l'hote et que le message vient d'un client, relayer a tous
        if (isHost && session != null && !senderId.equals(localPlayer.getId())) {
            // Relayer le message a tous les autres joueurs
            broadcastToAllExcept(message, senderId);
        }
        
        GameMessage gameMsg = new GameMessage(senderId, senderName, content, GameMessage.Type.CHAT);
        
        for (GameEventListener listener : listeners) {
            listener.onMessageReceived(gameMsg);
        }
    }
    
    private void handleVote(JsonObject message) {
        String voterId = message.get("voterId").getAsString();
        String targetId = message.get("targetId").getAsString();
        
        if (isHost && session != null) {
            session.vote(voterId, targetId);
            broadcastGameState();
        }
    }
    
    private void handleGuess(JsonObject message) {
        String playerId = message.get("playerId").getAsString();
        String guess = message.get("guess").getAsString();
        
        if (isHost && session != null) {
            boolean correct = session.guessWord(playerId, guess);
            broadcastGameState();
            
            if (session.getState() == GameSession.State.FINISHED) {
                broadcastGameEnd(session.getWinMessage());
            }
        }
    }
    
    private void handleGameEnd(JsonObject message) {
        String endMessage = message.get("message").getAsString();
        
        // Arreter tous les timers
        cancelCurrentTimer();
        
        for (GameEventListener listener : listeners) {
            listener.onGameEnded(endMessage);
        }
    }
    
    // ===== NOUVEAUX HANDLERS POUR LA SYNCHRONISATION DES TOURS =====
    
    /**
     * Reception d'un changement de tour (client)
     */
    private void handleTurnStart(JsonObject message) {
        String currentPlayerId = message.get("currentPlayerId").getAsString();
        int remainingSeconds = message.get("remainingSeconds").getAsInt();
        int currentTurnIndex = message.get("currentTurnIndex").getAsInt();
        
        // Mettre a jour la session locale
        if (session != null) {
            session.setCurrentTurnIndex(currentTurnIndex);
        }
        
        // Parser l'ordre des tours
        List<String> turnOrder = new ArrayList<>();
        if (message.has("turnOrder")) {
            JsonArray orderArray = message.getAsJsonArray("turnOrder");
            for (JsonElement elem : orderArray) {
                turnOrder.add(elem.getAsString());
            }
        }
        
        // Notifier l'UI
        for (GameEventListener listener : listeners) {
            listener.onTurnChanged(currentPlayerId, remainingSeconds, turnOrder);
        }
    }
    
    /**
     * Reception d'une synchronisation de timer (client)
     */
    private void handleTimerSync(JsonObject message) {
        int remainingSeconds = message.get("remainingSeconds").getAsInt();
        
        for (GameEventListener listener : listeners) {
            listener.onTimerSync(remainingSeconds);
        }
    }
    
    /**
     * Reception d'un timeout de tour (client)
     */
    private void handleTurnTimeout(JsonObject message) {
        String playerId = message.get("playerId").getAsString();
        String playerName = message.has("playerName") ? message.get("playerName").getAsString() : "Joueur";
        
        // Afficher le message de timeout
        GameMessage gameMsg = new GameMessage("system", "Systeme", 
            playerName + " n'a pas parle a temps - Tour passe !", GameMessage.Type.SYSTEM);
        
        for (GameEventListener listener : listeners) {
            listener.onMessageReceived(gameMsg);
        }
    }
    
    /**
     * Reception de fin de ronde (client)
     */
    private void handleRoundEnd(JsonObject message) {
        // La ronde est terminee, passer en phase de debat
        if (session != null) {
            session.setState(GameSession.State.DEBATE);
        }
        
        for (GameEventListener listener : listeners) {
            listener.onPhaseChanged(GameSession.State.DEBATE);
        }
    }
    
    // ===== METHODES DE GESTION DES TOURS (HOST) =====
    
    /**
     * Demarre le tour du joueur actuel (HOST uniquement)
     */
    private void startCurrentPlayerTurn() {
        if (!isHost || session == null) return;
        
        String currentPlayerId = session.getCurrentSpeakerId();
        if (currentPlayerId == null) {
            // Plus de joueur, fin de la ronde
            endCurrentRound();
            return;
        }
        
        // Annuler le timer precedent
        cancelCurrentTimer();
        
        // Demarrer le timer pour ce joueur
        currentTimerSeconds = GameSession.WORD_TIME_SECONDS;
        timerStartTime = System.currentTimeMillis();
        
        // Broadcaster le debut du tour
        broadcastTurnStart(currentPlayerId, currentTimerSeconds);
        
        // Timer local avec synchronisation periodique
        currentTimer = scheduler.scheduleAtFixedRate(() -> {
            currentTimerSeconds--;
            
            // Synchroniser le timer toutes les 5 secondes
            if (currentTimerSeconds % 5 == 0 && currentTimerSeconds > 0) {
                broadcastTimerSync(currentTimerSeconds);
            }
            
            // Timeout
            if (currentTimerSeconds <= 0) {
                cancelCurrentTimer();
                handlePlayerTimeout(currentPlayerId);
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Passe au joueur suivant (apres mot prononce ou timeout)
     */
    private void advanceToNextTurn() {
        if (!isHost || session == null) return;
        
        cancelCurrentTimer();
        
        // Avancer au joueur suivant
        boolean roundComplete = session.nextTurn();
        
        if (roundComplete) {
            endCurrentRound();
        } else {
            // Demarrer le tour du joueur suivant
            startCurrentPlayerTurn();
        }
    }
    
    /**
     * Gere le timeout d'un joueur
     */
    private void handlePlayerTimeout(String playerId) {
        if (!isHost || session == null) return;
        
        Player player = session.getPlayer(playerId);
        String playerName = player != null ? player.getName() : "Joueur";
        
        // Broadcaster le timeout
        JsonObject timeoutMsg = new JsonObject();
        timeoutMsg.addProperty("type", "TURN_TIMEOUT");
        timeoutMsg.addProperty("playerId", playerId);
        timeoutMsg.addProperty("playerName", playerName);
        broadcastToAll(timeoutMsg);
        
        // Passer au joueur suivant
        advanceToNextTurn();
    }
    
    /**
     * Termine la ronde actuelle et passe en phase de debat
     */
    private void endCurrentRound() {
        if (!isHost || session == null) return;
        
        cancelCurrentTimer();
        
        // Broadcaster la fin de ronde
        JsonObject roundEndMsg = new JsonObject();
        roundEndMsg.addProperty("type", "ROUND_END");
        broadcastToAll(roundEndMsg);
        
        // Passer en phase de debat
        session.startDebate();
        broadcastPhaseChange(GameSession.State.DEBATE);
        
        // Demarrer le timer de debat
        startDebateTimer();
    }
    
    /**
     * Demarre le timer de la phase de debat
     */
    private void startDebateTimer() {
        if (!isHost) return;
        
        cancelCurrentTimer();
        currentTimerSeconds = GameSession.DEBATE_TIME_SECONDS;
        
        // Broadcast le debut de la phase debat avec le timer
        broadcastTimerSync(currentTimerSeconds);
        
        currentTimer = scheduler.scheduleAtFixedRate(() -> {
            currentTimerSeconds--;
            
            if (currentTimerSeconds % 10 == 0 && currentTimerSeconds > 0) {
                broadcastTimerSync(currentTimerSeconds);
            }
            
            if (currentTimerSeconds <= 0) {
                cancelCurrentTimer();
                // Passer en phase de vote
                startVotingPhase();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Demarre la phase de vote
     */
    private void startVotingPhase() {
        if (!isHost || session == null) return;
        
        cancelCurrentTimer();
        session.startVoting();
        broadcastPhaseChange(GameSession.State.VOTING);
        
        currentTimerSeconds = GameSession.VOTE_TIME_SECONDS;
        broadcastTimerSync(currentTimerSeconds);
        
        currentTimer = scheduler.scheduleAtFixedRate(() -> {
            currentTimerSeconds--;
            
            if (currentTimerSeconds % 5 == 0 && currentTimerSeconds > 0) {
                broadcastTimerSync(currentTimerSeconds);
            }
            
            // Verifier si tout le monde a vote
            if (session.hasEveryoneVoted()) {
                cancelCurrentTimer();
                resolveVotesAndContinue();
                return;
            }
            
            if (currentTimerSeconds <= 0) {
                cancelCurrentTimer();
                resolveVotesAndContinue();
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    
    /**
     * Resout les votes et continue le jeu
     */
    private void resolveVotesAndContinue() {
        if (!isHost || session == null) return;
        
        Player eliminated = session.resolveVotes();
        broadcastPlayerList();
        
        // Afficher le resultat
        if (eliminated != null) {
            GameMessage elimMsg = GameMessage.elimination(eliminated);
            broadcastMessage(elimMsg);
        } else {
            GameMessage tieMsg = GameMessage.system("Egalite ! Personne n'est elimine.");
            broadcastMessage(tieMsg);
        }
        
        // Verifier condition de victoire
        if (session.checkWinCondition()) {
            broadcastGameEnd(session.getWinMessage());
            return;
        }
        
        // Demarrer une nouvelle ronde
        scheduler.schedule(() -> {
            session.newRound();
            broadcastPhaseChange(GameSession.State.WORD_PHASE);
            broadcastPlayerList();
            startCurrentPlayerTurn();
        }, 3, TimeUnit.SECONDS);
    }
    
    /**
     * Annule le timer en cours
     */
    private void cancelCurrentTimer() {
        if (currentTimer != null && !currentTimer.isCancelled()) {
            currentTimer.cancel(false);
        }
    }
    
    // ===== BROADCASTS DE SYNCHRONISATION =====
    
    /**
     * Broadcast le debut d'un tour
     */
    private void broadcastTurnStart(String currentPlayerId, int seconds) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "TURN_START");
        msg.addProperty("currentPlayerId", currentPlayerId);
        msg.addProperty("remainingSeconds", seconds);
        msg.addProperty("currentTurnIndex", session.getCurrentTurnIndex());
        
        // Ajouter l'ordre des tours
        JsonArray orderArray = new JsonArray();
        for (String id : session.getTurnOrder()) {
            orderArray.add(id);
        }
        msg.add("turnOrder", orderArray);
        
        broadcastToAll(msg);
        
        // Notifier localement aussi (pour l'host)
        for (GameEventListener listener : listeners) {
            listener.onTurnChanged(currentPlayerId, seconds, session.getTurnOrder());
        }
    }
    
    /**
     * Broadcast la synchronisation du timer
     */
    private void broadcastTimerSync(int seconds) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "TIMER_SYNC");
        msg.addProperty("remainingSeconds", seconds);
        broadcastToAll(msg);
        
        // Notifier localement
        for (GameEventListener listener : listeners) {
            listener.onTimerSync(seconds);
        }
    }
    
    /**
     * Broadcast un message de jeu a tous
     */
    private void broadcastMessage(GameMessage gameMessage) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "GAME_MESSAGE");
        msg.addProperty("senderId", gameMessage.getSenderId());
        msg.addProperty("senderName", gameMessage.getSenderName());
        msg.addProperty("content", gameMessage.getContent());
        msg.addProperty("messageType", gameMessage.getType().name());
        broadcastToAll(msg);
        
        // Notifier localement
        for (GameEventListener listener : listeners) {
            listener.onMessageReceived(gameMessage);
        }
    }
    
    // ===== ACTIONS DU JOUEUR =====
    
    // Minimum de joueurs pour lancer (3 en production, 2 pour les tests)
    private static final int MIN_PLAYERS_TO_START = 2;
    
    public void startGame() {
        System.out.println("=== startGame() appele ===");
        System.out.println("isHost: " + isHost);
        System.out.println("session: " + (session != null ? "OK" : "NULL"));
        if (session != null) {
            System.out.println("players count: " + session.getPlayers().size());
        }
        
        if (!isHost) {
            System.out.println("ERREUR: Pas l'hote, impossible de lancer");
            return;
        }
        if (session == null) {
            System.out.println("ERREUR: Session nulle");
            return;
        }
        if (session.getPlayers().size() < MIN_PLAYERS_TO_START) {
            System.out.println("ERREUR: Pas assez de joueurs (" + session.getPlayers().size() + "/" + MIN_PLAYERS_TO_START + ")");
            return;
        }
        
        System.out.println("=== Lancement de la partie ===");
        
        executor.submit(() -> {
            try {
                System.out.println("[THREAD] Debut du lancement...");
                
                // Choisir une paire de mots aleatoire
                System.out.println("[THREAD] wordPairs.size() = " + wordPairs.size());
                String[] pair = wordPairs.get(new Random().nextInt(wordPairs.size()));
                String citizenWord = pair[0];
                System.out.println("[THREAD] Mot choisi: " + citizenWord);
                
                // Demarrer la partie (l'imposteur n'a pas de mot)
                System.out.println("[THREAD] Appel session.startGame()...");
                session.startGame(citizenWord, null);
                System.out.println("[THREAD] session.startGame() termine, state=" + session.getState());
                
                // Envoyer les roles a chaque joueur via UDP (rapide)
                System.out.println("[THREAD] Envoi des roles aux " + session.getPlayers().size() + " joueurs...");
                for (Player player : session.getPlayers()) {
                    String word = session.getWordForPlayer(player);
                    System.out.println("[THREAD] Joueur " + player.getName() + " - Role: " + player.getRole() + " - Mot: " + word);
                    
                    JsonObject startMsg = new JsonObject();
                    startMsg.addProperty("type", "GAME_START");
                    startMsg.addProperty("role", player.getRole().name());
                    if (word != null) {
                        startMsg.addProperty("word", word);
                    } else {
                        startMsg.add("word", JsonNull.INSTANCE);
                    }
                    
                    // Envoyer via UDP pour rapidite
                    System.out.println("[THREAD] Envoi UDP a " + player.getIpAddress() + ":" + player.getUdpPort());
                    networkBridge.sendUdpMessage(player.getIpAddress(), player.getUdpPort(), startMsg);
                }
                
                // Notifier le changement de phase
                System.out.println("[THREAD] Broadcast phase change...");
                broadcastPhaseChange(GameSession.State.WORD_PHASE);
                
                // Notifier l'UI locale (hote)
                System.out.println("[THREAD] Notification listeners...");
                for (GameEventListener listener : listeners) {
                    String myWord = session.getWordForPlayer(localPlayer);
                    listener.onGameStarted(localPlayer.getRole(), myWord);
                }
                
                // Attendre un peu puis demarrer le premier tour
                System.out.println("[THREAD] Programmation du premier tour dans 2s...");
                scheduler.schedule(() -> {
                    System.out.println("[SCHEDULER] Demarrage du premier tour...");
                    startCurrentPlayerTurn();
                }, 2, TimeUnit.SECONDS);
                
                System.out.println("[THREAD] Lancement termine avec succes!");
                
            } catch (Exception e) {
                System.err.println("[THREAD] ERREUR dans startGame: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    public void speakWord(String word) {
        if (localPlayer == null) return;
        
        // Verifier si c'est notre tour (cote host, les clients font confiance au serveur)
        if (isHost && session != null && !session.isPlayerTurn(localPlayer.getId())) {
            System.out.println("Ce n'est pas votre tour de parler !");
            return;
        }
        
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "WORD_SPOKEN");
        msg.addProperty("playerId", localPlayer.getId());
        msg.addProperty("playerName", localPlayer.getName());
        msg.addProperty("word", word);
        msg.addProperty("timestamp", System.currentTimeMillis());
        
        // Afficher localement d'abord
        GameMessage gameMsg = new GameMessage(localPlayer.getId(), localPlayer.getName(), word, GameMessage.Type.WORD);
        for (GameEventListener listener : listeners) {
            listener.onMessageReceived(gameMsg);
        }
        
        if (isHost && session != null) {
            // Traiter localement
            session.speakWord(localPlayer.getId(), word);
            
            // Broadcaster a tous les autres
            broadcastToAllExcept(msg, localPlayer.getId());
            
            // Passer au joueur suivant
            advanceToNextTurn();
        } else {
            // Envoyer au serveur qui relaiera
            sendToServer(msg);
        }
    }
    
    public void sendChat(String message) {
        if (localPlayer == null) return;
        
        // Verifier si le chat est autorise
        if (session != null && !session.isChatAllowed()) {
            System.out.println("Chat non autorise pendant cette phase");
            return;
        }
        
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "CHAT");
        msg.addProperty("playerId", localPlayer.getId());
        msg.addProperty("playerName", localPlayer.getName());
        msg.addProperty("message", message);
        msg.addProperty("timestamp", System.currentTimeMillis());
        
        // Afficher localement d'abord
        GameMessage gameMsg = new GameMessage(localPlayer.getId(), localPlayer.getName(), message, GameMessage.Type.CHAT);
        for (GameEventListener listener : listeners) {
            listener.onMessageReceived(gameMsg);
        }
        
        if (isHost) {
            // Broadcaster a tous les autres (pas a soi-meme)
            broadcastToAllExcept(msg, localPlayer.getId());
        } else {
            // Envoyer au serveur qui relaiera
            sendToServer(msg);
        }
    }
    
    public void vote(String targetPlayerId) {
        if (localPlayer == null) return;
        
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "VOTE");
        msg.addProperty("voterId", localPlayer.getId());
        msg.addProperty("targetId", targetPlayerId);
        
        if (isHost && session != null) {
            session.vote(localPlayer.getId(), targetPlayerId);
            broadcastGameState();
        } else {
            sendToServer(msg);
        }
    }
    
    public void guessWord(String guess) {
        if (localPlayer == null || localPlayer.getRole() != Role.IMPOSTOR) return;
        
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "GUESS");
        msg.addProperty("playerId", localPlayer.getId());
        msg.addProperty("guess", guess);
        
        if (isHost && session != null) {
            session.guessWord(localPlayer.getId(), guess);
            broadcastGameState();
            
            if (session.getState() == GameSession.State.FINISHED) {
                broadcastGameEnd(session.getWinMessage());
            }
        } else {
            sendToServer(msg);
        }
    }
    
    public void startVoting() {
        if (!isHost || session == null) return;
        
        // Cette methode peut etre appelee manuellement, donc on utilise startVotingPhase
        startVotingPhase();
    }
    
    // ===== BROADCASTS =====
    
    private void broadcastPlayerList() {
        if (session == null) return;
        
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "PLAYER_LIST");
        
        JsonArray players = new JsonArray();
        for (Player p : session.getPlayers()) {
            JsonObject pObj = new JsonObject();
            pObj.addProperty("id", p.getId());
            pObj.addProperty("name", p.getName());
            pObj.addProperty("ip", p.getIpAddress());
            pObj.addProperty("port", p.getPort());
            pObj.addProperty("udpPort", p.getUdpPort());  // Port UDP pour les messages de jeu
            pObj.addProperty("isHost", p.isHost());
            pObj.addProperty("alive", p.isAlive());
            players.add(pObj);
        }
        msg.add("players", players);
        
        broadcastToAll(msg);
    }
    
    private void broadcastPhaseChange(GameSession.State state) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "PHASE_CHANGE");
        msg.addProperty("state", state.name());
        broadcastToAll(msg);
    }
    
    private void broadcastGameState() {
        if (session == null) return;
        broadcastPlayerList();
    }
    
    private void broadcastGameEnd(String message) {
        JsonObject msg = new JsonObject();
        msg.addProperty("type", "GAME_END");
        msg.addProperty("message", message);
        broadcastToAll(msg);
    }
    
    /**
     * Broadcast un message a tous les joueurs via UDP (rapide, sans latence)
     */
    private void broadcastToAll(JsonObject message) {
        if (session == null) return;
        
        for (Player player : session.getPlayers()) {
            // Utiliser UDP pour les messages de jeu (rapide)
            networkBridge.sendUdpMessage(player.getIpAddress(), player.getUdpPort(), message);
        }
    }
    
    /**
     * Broadcast un message a tous les joueurs SAUF un (pour eviter les echos)
     */
    private void broadcastToAllExcept(JsonObject message, String excludePlayerId) {
        if (session == null) return;
        
        for (Player player : session.getPlayers()) {
            if (excludePlayerId != null && player.getId().equals(excludePlayerId)) {
                continue;  // Sauter ce joueur
            }
            // Utiliser UDP pour les messages de jeu (rapide)
            networkBridge.sendUdpMessage(player.getIpAddress(), player.getUdpPort(), message);
        }
    }
    
    /**
     * Envoie un message au serveur (hote) via UDP
     */
    private void sendToServer(JsonObject message) {
        if (hostIp != null && hostUdpPort > 0) {
            // Utiliser UDP pour les messages de jeu
            networkBridge.sendUdpMessage(hostIp, hostUdpPort, message);
        } else if (session != null) {
            // Fallback avec l'IP de la session
            networkBridge.sendUdpMessage(session.getHostIp(), hostUdpPort > 0 ? hostUdpPort : session.getHostPort() + UDP_PORT_OFFSET, message);
        }
    }
    
    // ===== NOTIFICATIONS =====
    
    private void notifyPlayersUpdated() {
        if (session == null) return;
        List<Player> players = session.getPlayers();
        for (GameEventListener listener : listeners) {
            listener.onPlayersUpdated(players);
        }
    }
    
    private void notifyConnectionStatus(boolean connected, String message) {
        for (GameEventListener listener : listeners) {
            listener.onConnectionStatusChanged(connected, message);
        }
    }
    
    // ===== GETTERS & SETTERS =====
    
    public void addListener(GameEventListener listener) {
        listeners.add(listener);
    }
    
    public void removeListener(GameEventListener listener) {
        listeners.remove(listener);
    }
    
    public GameSession getSession() {
        return session;
    }
    
    public Player getLocalPlayer() {
        return localPlayer;
    }
    
    public boolean isHost() {
        return isHost;
    }
    
    public void shutdown() {
        cancelCurrentTimer();
        scheduler.shutdown();
        networkBridge.shutdown();
        executor.shutdown();
    }
}
