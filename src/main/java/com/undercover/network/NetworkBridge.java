package com.undercover.network;

import com.google.gson.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * NetworkBridge - Pont entre Java et les scripts Bash pour le réseau
 * Utilise UDP pour les messages de jeu (faible latence) et TCP pour les connexions initiales
 */
public class NetworkBridge {
    
    private final BashExecutor bashExecutor;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;
    private final ConcurrentHashMap<String, Consumer<JsonObject>> messageHandlers;
    
    private String broadcastProcessId;
    private String serverListenerProcessId;
    private volatile boolean running;
    private volatile boolean localhostMode;
    
    // UDP pour les messages de jeu (rapide, sans connexion)
    private DatagramSocket udpSocket;
    private Thread udpListenerThread;
    private int udpPort;
    private static final int UDP_BUFFER_SIZE = 8192;
    
    public NetworkBridge() {
        this.bashExecutor = new BashExecutor();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "NetworkBridge-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.messageHandlers = new ConcurrentHashMap<>();
        this.running = false;
        this.localhostMode = false;
        this.udpPort = 0;
    }
    
    /**
     * Active ou désactive le mode localhost (plusieurs instances sur le même PC)
     */
    public void setLocalhostMode(boolean enabled) {
        this.localhostMode = enabled;
    }
    
    /**
     * Vérifie si le mode localhost est actif
     */
    public boolean isLocalhostMode() {
        return localhostMode;
    }
    
    /**
     * Démarre le broadcast du serveur (annonces UDP ou localhost)
     */
    public void startServerBroadcast(String serverIp, int serverPort, String sessionName, 
                                      int maxPlayers, int currentPlayers) {
        startServerBroadcast(serverIp, serverPort, sessionName, maxPlayers, currentPlayers, new ArrayList<>());
    }
    
    /**
     * Démarre le broadcast du serveur avec les noms des joueurs
     */
    public void startServerBroadcast(String serverIp, int serverPort, String sessionName, 
                                      int maxPlayers, int currentPlayers, List<String> playerNames) {
        if (broadcastProcessId != null) {
            bashExecutor.stopBackground(broadcastProcessId);
        }
        
        running = true;
        String namesArg = playerNames != null ? String.join(",", playerNames) : "";
        
        if (localhostMode) {
            // Mode localhost - utilise le fichier partagé
            broadcastProcessId = bashExecutor.startBackground(
                "broadcast_localhost.sh",
                output -> {
                    // Log optionnel des annonces
                },
                String.valueOf(serverPort),
                sessionName,
                String.valueOf(maxPlayers),
                String.valueOf(currentPlayers),
                namesArg
            );
        } else {
            // Mode LAN - broadcast UDP
            broadcastProcessId = bashExecutor.startBackground(
                "broadcast_server.sh",
                output -> {
                    // Log optionnel des annonces
                },
                serverIp,
                String.valueOf(serverPort),
                sessionName,
                String.valueOf(maxPlayers),
                String.valueOf(currentPlayers),
                namesArg
            );
        }
    }
    
    /**
     * Arrête le broadcast du serveur
     */
    public void stopServerBroadcast() {
        if (broadcastProcessId != null) {
            bashExecutor.stopBackground(broadcastProcessId);
            broadcastProcessId = null;
        }
    }
    
    /**
     * Écoute les serveurs disponibles sur le réseau ou localhost
     */
    public CompletableFuture<List<ServerInfo>> discoverServers(int timeoutSeconds) {
        String script = localhostMode ? "listen_localhost.sh" : "listen_servers.sh";
        
        return bashExecutor.executeAsync(script, String.valueOf(timeoutSeconds))
            .thenApply(this::parseServerList);
    }
    
    /**
     * Écoute les serveurs sur LAN ET localhost simultanément
     */
    public CompletableFuture<List<ServerInfo>> discoverAllServers(int timeoutSeconds) {
        // Localhost est rapide (lecture de fichier), le lancer immédiatement
        CompletableFuture<List<ServerInfo>> localFuture = 
            bashExecutor.executeAsync("listen_localhost.sh", "1")
                .thenApply(this::parseServerList)
                .exceptionally(e -> new ArrayList<>());
        
        // LAN peut être lent, avec timeout court
        CompletableFuture<List<ServerInfo>> lanFuture = 
            bashExecutor.executeAsync("listen_servers.sh", String.valueOf(timeoutSeconds))
                .thenApply(this::parseServerList)
                .exceptionally(e -> new ArrayList<>());
        
        // Combiner les deux résultats, mais ne pas attendre LAN si localhost a des résultats
        return localFuture.thenCombine(
            lanFuture.completeOnTimeout(new ArrayList<>(), timeoutSeconds + 1, TimeUnit.SECONDS),
            (localServers, lanServers) -> {
                List<ServerInfo> all = new ArrayList<>(localServers);
                for (ServerInfo lan : lanServers) {
                    boolean exists = all.stream().anyMatch(s -> 
                        s.port == lan.port && (s.ip.equals(lan.ip) || 
                        s.ip.equals("127.0.0.1") && lan.ip.equals("127.0.0.1")));
                    if (!exists) {
                        all.add(lan);
                    }
                }
                return all;
            }
        );
    }
    
    private List<ServerInfo> parseServerList(String output) {
        List<ServerInfo> servers = new ArrayList<>();
        try {
            JsonArray array = JsonParser.parseString(output).getAsJsonArray();
            for (JsonElement elem : array) {
                JsonObject obj = elem.getAsJsonObject();
                List<String> playerNames = new ArrayList<>();
                if (obj.has("playerNames")) {
                    String names = obj.get("playerNames").getAsString();
                    if (!names.isEmpty()) {
                        for (String n : names.split(",")) {
                            if (!n.trim().isEmpty()) playerNames.add(n.trim());
                        }
                    }
                }
                servers.add(new ServerInfo(
                    obj.get("ip").getAsString(),
                    obj.get("port").getAsInt(),
                    obj.get("name").getAsString(),
                    obj.get("maxPlayers").getAsInt(),
                    obj.get("currentPlayers").getAsInt(),
                    playerNames
                ));
            }
        } catch (Exception e) {
            // Parsing error
        }
        return servers;
    }
    
    /**
     * Démarre l'écoute continue des serveurs (LAN et/ou localhost)
     */
    public void startServerDiscovery(Consumer<List<ServerInfo>> onServersFound) {
        startServerDiscovery(onServersFound, true);
    }
    
    /**
     * Démarre l'écoute continue des serveurs
     * @param includeLocalhost true pour inclure les parties sur localhost
     */
    public void startServerDiscovery(Consumer<List<ServerInfo>> onServersFound, boolean includeLocalhost) {
        running = true;
        scheduler.scheduleAtFixedRate(() -> {
            if (running) {
                if (includeLocalhost) {
                    discoverAllServers(2).thenAccept(onServersFound);
                } else {
                    bashExecutor.executeAsync("listen_servers.sh", "3")
                        .thenApply(this::parseServerList)
                        .thenAccept(onServersFound);
                }
            }
        }, 0, 3, TimeUnit.SECONDS);
    }
    
    /**
     * Envoie un message TCP à un serveur (implémentation Java directe)
     * Utilise DataOutputStream pour envoyer la longueur puis le message
     */
    public CompletableFuture<JsonObject> sendMessage(String targetIp, int targetPort, JsonObject message) {
        String jsonMessage = new Gson().toJson(message);  // Version compacte (sans pretty print)
        
        return CompletableFuture.supplyAsync(() -> {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(targetIp, targetPort), 5000);
                socket.setSoTimeout(10000);
                socket.setTcpNoDelay(true);  // Désactiver Nagle pour envoi immédiat
                
                System.out.println("Connexion établie à " + targetIp + ":" + targetPort);
                
                // Envoyer le message avec longueur préfixée
                java.io.DataOutputStream dos = new java.io.DataOutputStream(socket.getOutputStream());
                byte[] messageBytes = jsonMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                dos.writeInt(messageBytes.length);
                dos.write(messageBytes);
                dos.flush();
                System.out.println("Message envoyé (" + messageBytes.length + " bytes): " + 
                    jsonMessage.substring(0, Math.min(100, jsonMessage.length())) + "...");
                
                // Lire la réponse avec longueur préfixée
                java.io.DataInputStream dis = new java.io.DataInputStream(socket.getInputStream());
                int responseLength = dis.readInt();
                byte[] responseBytes = new byte[responseLength];
                dis.readFully(responseBytes);
                String response = new String(responseBytes, java.nio.charset.StandardCharsets.UTF_8);
                System.out.println("Réponse reçue (" + responseLength + " bytes): " + response);
                
                if (response != null && !response.isEmpty()) {
                    try {
                        return JsonParser.parseString(response).getAsJsonObject();
                    } catch (Exception e) {
                        System.err.println("Erreur parsing réponse: " + e.getMessage());
                        JsonObject result = new JsonObject();
                        result.addProperty("success", true);
                        result.addProperty("raw", response);
                        return result;
                    }
                } else {
                    JsonObject result = new JsonObject();
                    result.addProperty("success", true);
                    return result;
                }
            } catch (Exception e) {
                System.err.println("Erreur connexion TCP: " + e.getMessage());
                e.printStackTrace();
                JsonObject error = new JsonObject();
                error.addProperty("error", e.getMessage());
                error.addProperty("success", false);
                return error;
            }
        }, scheduler);
    }
    
    // ===== UDP MESSAGING - RAPIDE, SANS CONNEXION =====
    
    /**
     * Démarre le serveur UDP pour recevoir les messages de jeu
     * UDP est plus rapide que TCP car pas de handshake ni de confirmation
     */
    public void startUdpServer(int port, Consumer<JsonObject> messageHandler) {
        this.udpPort = port;
        running = true;
        
        udpListenerThread = new Thread(() -> {
            try {
                udpSocket = new DatagramSocket(port);
                udpSocket.setReuseAddress(true);
                System.out.println("UDP Server listening on port " + port);
                
                byte[] buffer = new byte[UDP_BUFFER_SIZE];
                
                while (running && !udpSocket.isClosed()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        udpSocket.receive(packet);
                        
                        String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        String senderInfo = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                        
                        // Traiter dans un thread séparé pour ne pas bloquer l'écoute
                        scheduler.submit(() -> {
                            try {
                                JsonObject msg = JsonParser.parseString(message).getAsJsonObject();
                                // Ajouter les infos de l'expéditeur
                                msg.addProperty("_senderIp", packet.getAddress().getHostAddress());
                                msg.addProperty("_senderPort", packet.getPort());
                                
                                if (messageHandler != null) {
                                    messageHandler.accept(msg);
                                }
                            } catch (Exception e) {
                                System.err.println("Erreur parsing UDP message: " + e.getMessage());
                            }
                        });
                        
                    } catch (SocketException e) {
                        if (running) {
                            System.err.println("UDP Socket error: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to start UDP server on port " + port + ": " + e.getMessage());
            }
        }, "UDP-Server-" + port);
        
        udpListenerThread.setDaemon(true);
        udpListenerThread.start();
    }
    
    /**
     * Envoie un message UDP (fire-and-forget, très rapide)
     * Pas d'attente de confirmation - idéal pour les messages de jeu
     */
    public void sendUdpMessage(String targetIp, int targetPort, JsonObject message) {
        scheduler.submit(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(1000);
                
                String jsonMessage = new Gson().toJson(message);
                byte[] data = jsonMessage.getBytes(StandardCharsets.UTF_8);
                
                InetAddress address = InetAddress.getByName(targetIp);
                DatagramPacket packet = new DatagramPacket(data, data.length, address, targetPort);
                
                socket.send(packet);
                socket.close();
                
            } catch (Exception e) {
                System.err.println("Erreur UDP send to " + targetIp + ":" + targetPort + " - " + e.getMessage());
            }
        });
    }
    
    /**
     * Envoie un message UDP et attend une réponse (avec timeout court)
     * Utile pour les confirmations critiques
     */
    public CompletableFuture<JsonObject> sendUdpMessageWithResponse(String targetIp, int targetPort, JsonObject message, int timeoutMs) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                socket.setSoTimeout(timeoutMs);
                
                String jsonMessage = new Gson().toJson(message);
                byte[] data = jsonMessage.getBytes(StandardCharsets.UTF_8);
                
                InetAddress address = InetAddress.getByName(targetIp);
                DatagramPacket sendPacket = new DatagramPacket(data, data.length, address, targetPort);
                socket.send(sendPacket);
                
                // Attendre la réponse
                byte[] buffer = new byte[UDP_BUFFER_SIZE];
                DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivePacket);
                
                String response = new String(receivePacket.getData(), 0, receivePacket.getLength(), StandardCharsets.UTF_8);
                socket.close();
                
                return JsonParser.parseString(response).getAsJsonObject();
                
            } catch (SocketTimeoutException e) {
                JsonObject timeout = new JsonObject();
                timeout.addProperty("success", false);
                timeout.addProperty("error", "timeout");
                return timeout;
            } catch (Exception e) {
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("error", e.getMessage());
                return error;
            }
        }, scheduler);
    }
    
    /**
     * Envoie une réponse UDP (pour répondre à un message reçu)
     */
    public void sendUdpResponse(String targetIp, int targetPort, JsonObject response) {
        sendUdpMessage(targetIp, targetPort, response);
    }
    
    /**
     * Arrête le serveur UDP
     */
    public void stopUdpServer() {
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        if (udpListenerThread != null) {
            udpListenerThread.interrupt();
            udpListenerThread = null;
        }
    }
    
    /**
     * Retourne le port UDP actuel
     */
    public int getUdpPort() {
        return udpPort;
    }
    
    // Serveur TCP Java natif
    private java.net.ServerSocket tcpServerSocket;
    private Thread tcpServerThread;
    
    /**
     * Démarre le serveur TCP pour recevoir les messages (implémentation Java)
     */
    public void startTcpServer(int port, Consumer<JsonObject> messageHandler) {
        running = true;
        
        tcpServerThread = new Thread(() -> {
            try {
                tcpServerSocket = new java.net.ServerSocket(port);
                tcpServerSocket.setReuseAddress(true);
                System.out.println("TCP Server listening on port " + port);
                
                while (running && !tcpServerSocket.isClosed()) {
                    try {
                        java.net.Socket clientSocket = tcpServerSocket.accept();
                        
                        // Gérer chaque client dans un thread séparé
                        scheduler.submit(() -> handleClient(clientSocket, messageHandler));
                        
                    } catch (java.net.SocketException e) {
                        // Socket fermé, c'est normal à l'arrêt
                        if (running) {
                            System.err.println("Socket error: " + e.getMessage());
                        }
                    }
                }
            } catch (java.io.IOException e) {
                System.err.println("Failed to start TCP server on port " + port + ": " + e.getMessage());
            }
        }, "TCP-Server-" + port);
        
        tcpServerThread.setDaemon(true);
        tcpServerThread.start();
    }
    
    private void handleClient(java.net.Socket clientSocket, Consumer<JsonObject> messageHandler) {
        try {
            clientSocket.setSoTimeout(30000);
            clientSocket.setTcpNoDelay(true);
            String clientInfo = clientSocket.getInetAddress().getHostAddress() + ":" + clientSocket.getPort();
            System.out.println("Client connecté: " + clientInfo);
            
            // Lire avec longueur préfixée
            java.io.DataInputStream dis = new java.io.DataInputStream(clientSocket.getInputStream());
            java.io.DataOutputStream dos = new java.io.DataOutputStream(clientSocket.getOutputStream());
            
            int messageLength = dis.readInt();
            byte[] messageBytes = new byte[messageLength];
            dis.readFully(messageBytes);
            String line = new String(messageBytes, java.nio.charset.StandardCharsets.UTF_8);
            
            System.out.println("Message reçu de " + clientInfo + " (" + messageLength + " bytes): " + line);
            
            try {
                JsonObject msg = JsonParser.parseString(line).getAsJsonObject();
                
                // Envoyer une réponse de confirmation AVANT de traiter
                JsonObject response = new JsonObject();
                response.addProperty("success", true);
                response.addProperty("status", "received");
                String responseStr = new Gson().toJson(response);
                byte[] responseBytes = responseStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                dos.writeInt(responseBytes.length);
                dos.write(responseBytes);
                dos.flush();
                System.out.println("Réponse envoyée (" + responseBytes.length + " bytes): " + responseStr);
                
                // Traiter le message dans un thread séparé pour ne pas bloquer
                if (messageHandler != null) {
                    System.out.println("Appel du handler pour le message type: " + 
                        (msg.has("type") ? msg.get("type").getAsString() : "inconnu"));
                    scheduler.submit(() -> messageHandler.accept(msg));
                }
            } catch (Exception e) {
                System.err.println("Erreur parsing JSON: " + e.getMessage());
                JsonObject error = new JsonObject();
                error.addProperty("success", false);
                error.addProperty("error", "Invalid JSON: " + e.getMessage());
                String errorStr = new Gson().toJson(error);
                byte[] errorBytes = errorStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                dos.writeInt(errorBytes.length);
                dos.write(errorBytes);
                dos.flush();
            }
        } catch (Exception e) {
            System.err.println("Erreur handleClient: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * Arrête le serveur TCP
     */
    public void stopTcpServer() {
        // Fermer le serveur TCP Java
        if (tcpServerSocket != null && !tcpServerSocket.isClosed()) {
            try {
                tcpServerSocket.close();
            } catch (Exception e) {
                // Ignorer
            }
        }
        if (tcpServerThread != null) {
            tcpServerThread.interrupt();
            tcpServerThread = null;
        }
        
        // Aussi arrêter le processus Bash si existant
        if (serverListenerProcessId != null) {
            bashExecutor.stopBackground(serverListenerProcessId);
            serverListenerProcessId = null;
        }
    }
    
    /**
     * Récupère l'IP locale
     */
    public String getLocalIp() {
        return bashExecutor.getLocalIp();
    }
    
    /**
     * Arrête tous les processus réseau
     */
    public void shutdown() {
        running = false;
        stopServerBroadcast();
        stopTcpServer();
        stopUdpServer();
        scheduler.shutdown();
        bashExecutor.shutdown();
    }
    
    /**
     * Classe interne pour les infos serveur
     */
    public static class ServerInfo {
        public final String ip;
        public final int port;
        public final String name;
        public final int maxPlayers;
        public final int currentPlayers;
        public final List<String> playerNames;
        
        public ServerInfo(String ip, int port, String name, int maxPlayers, int currentPlayers) {
            this(ip, port, name, maxPlayers, currentPlayers, new ArrayList<>());
        }
        
        public ServerInfo(String ip, int port, String name, int maxPlayers, int currentPlayers, List<String> playerNames) {
            this.ip = ip;
            this.port = port;
            this.name = name;
            this.maxPlayers = maxPlayers;
            this.currentPlayers = currentPlayers;
            this.playerNames = playerNames != null ? playerNames : new ArrayList<>();
        }
        
        @Override
        public String toString() {
            return String.format("%s (%s:%d) - %d/%d joueurs", name, ip, port, currentPlayers, maxPlayers);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ServerInfo that = (ServerInfo) o;
            return port == that.port && ip.equals(that.ip);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(ip, port);
        }
    }
}
