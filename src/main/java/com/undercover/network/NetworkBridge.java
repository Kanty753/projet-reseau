package com.undercover.network;

import com.google.gson.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.*;
import java.io.*;
import java.nio.file.*;

/**
 * NetworkBridge - Pont entre Java et les scripts Bash pour le reseau
 * 
 * PRINCIPE : Java ne fait AUCUN socket.
 * Toute la communication passe par des scripts Bash :
 *   - broadcast_server.sh / broadcast_localhost.sh : annonces UDP
 *   - listen_servers.sh / listen_localhost.sh : decouverte de serveurs
 *   - send_tcp.sh : envoi TCP (fiable, pour JOIN)
 *   - tcp_server.sh + handle_tcp_client.sh : reception TCP
 *   - send_udp.sh : envoi UDP (rapide, pour messages de jeu)
 *   - udp_server.sh : reception UDP
 *   - get_local_ip.sh : recuperation de l'IP locale
 */
public class NetworkBridge {
    
    private final BashExecutor bashExecutor;
    private final Gson gson;
    private final ScheduledExecutorService scheduler;
    
    private String broadcastProcessId;
    private String tcpServerProcessId;
    private String udpServerProcessId;
    private volatile boolean running;
    private volatile boolean localhostMode;
    
    // Port UDP en cours d'ecoute
    private int udpPort;
    // Port TCP en cours d'ecoute
    private int tcpPort;
    
    // Repertoire d'inbox TCP (les messages recus par tcp_server.sh y sont ecrits)
    private static final String TCP_INBOX_DIR = "/tmp/undercover_tcp_inbox";
    private static final String TCP_INBOX_FILE = TCP_INBOX_DIR + "/messages.jsonl";
    private Thread tcpInboxPollerThread;
    
    public NetworkBridge() {
        this.bashExecutor = new BashExecutor();
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.scheduler = Executors.newScheduledThreadPool(4, r -> {
            Thread t = new Thread(r, "NetworkBridge-Scheduler");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
        this.localhostMode = false;
        this.udpPort = 0;
        this.tcpPort = 0;
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
    
    // =====================================================================
    // ENVOI DE MESSAGES TCP (via send_tcp.sh)
    // =====================================================================
    
    /**
     * Envoie un message TCP a un serveur distant via le script Bash send_tcp.sh.
     * Le script ouvre la connexion, envoie le message, et retourne la reponse.
     * Java ne fait aucun socket directement.
     */
    public CompletableFuture<JsonObject> sendMessage(String targetIp, int targetPort, JsonObject message) {
        String jsonMessage = new Gson().toJson(message);
        
        return bashExecutor.executeAsync("send_tcp.sh", targetIp, String.valueOf(targetPort), jsonMessage)
            .thenApply(response -> {
                System.out.println("Reponse TCP recue: " + response);
                if (response != null && !response.trim().isEmpty()) {
                    try {
                        return JsonParser.parseString(response.trim()).getAsJsonObject();
                    } catch (Exception e) {
                        System.err.println("Erreur parsing reponse TCP: " + e.getMessage());
                        JsonObject result = new JsonObject();
                        result.addProperty("success", true);
                        result.addProperty("raw", response);
                        return result;
                    }
                } else {
                    JsonObject result = new JsonObject();
                    result.addProperty("success", false);
                    result.addProperty("error", "Reponse vide");
                    return result;
                }
            })
            .exceptionally(e -> {
                System.err.println("Erreur envoi TCP: " + e.getMessage());
                JsonObject error = new JsonObject();
                error.addProperty("error", e.getMessage());
                error.addProperty("success", false);
                return error;
            });
    }
    
    // =====================================================================
    // SERVEUR TCP (via tcp_server.sh + handle_tcp_client.sh)
    // =====================================================================
    
    /**
     * Demarre le serveur TCP pour recevoir les messages.
     * Le script tcp_server.sh ecoute sur le port et ecrit les messages
     * dans /tmp/undercover_tcp_inbox/messages.jsonl via handle_tcp_client.sh.
     * Java lit ce fichier en continu (polling) pour traiter les messages.
     */
    public void startTcpServer(int port, Consumer<JsonObject> messageHandler) {
        this.tcpPort = port;
        running = true;
        
        // Nettoyer l'inbox avant de demarrer
        try {
            Files.createDirectories(Paths.get(TCP_INBOX_DIR));
            Files.deleteIfExists(Paths.get(TCP_INBOX_FILE));
        } catch (IOException e) {
            System.err.println("Erreur creation inbox TCP: " + e.getMessage());
        }
        
        // Demarrer le script tcp_server.sh en arriere-plan
        tcpServerProcessId = bashExecutor.startBackground(
            "tcp_server.sh",
            output -> {
                System.out.println("[TCP Server stdout] " + output);
            },
            String.valueOf(port)
        );
        System.out.println("TCP Server (Bash) demarre sur le port " + port);
        
        // Demarrer le polling du fichier inbox
        startTcpInboxPoller(messageHandler);
    }
    
    /**
     * Lit en continu le fichier d'inbox TCP pour les messages entrants.
     * Chaque ligne est un message JSON ecrit par handle_tcp_client.sh.
     * On utilise un polling rapide (100ms) pour une latence faible.
     */
    private void startTcpInboxPoller(Consumer<JsonObject> messageHandler) {
        tcpInboxPollerThread = new Thread(() -> {
            Path inboxPath = Paths.get(TCP_INBOX_FILE);
            long lastPosition = 0;
            
            while (running) {
                try {
                    if (Files.exists(inboxPath)) {
                        long fileSize = Files.size(inboxPath);
                        if (fileSize > lastPosition) {
                            // Lire les nouvelles lignes depuis la derniere position
                            try (RandomAccessFile raf = new RandomAccessFile(inboxPath.toFile(), "r")) {
                                raf.seek(lastPosition);
                                String line;
                                while ((line = raf.readLine()) != null) {
                                    if (!line.trim().isEmpty()) {
                                        // readLine retourne en ISO-8859-1, convertir en UTF-8
                                        String utf8Line = new String(line.getBytes("ISO-8859-1"), "UTF-8");
                                        try {
                                            JsonObject msg = JsonParser.parseString(utf8Line.trim()).getAsJsonObject();
                                            if (messageHandler != null) {
                                                scheduler.submit(() -> messageHandler.accept(msg));
                                            }
                                        } catch (Exception e) {
                                            System.err.println("Erreur parsing message TCP inbox: " + e.getMessage());
                                        }
                                    }
                                }
                                lastPosition = raf.getFilePointer();
                            }
                        }
                    }
                    // Polling interval : 100ms pour une latence faible
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Erreur lecture inbox TCP: " + e.getMessage());
                    try { Thread.sleep(500); } catch (InterruptedException ie) { break; }
                }
            }
        }, "TCP-Inbox-Poller");
        tcpInboxPollerThread.setDaemon(true);
        tcpInboxPollerThread.start();
    }
    
    /**
     * Arrete le serveur TCP (processus Bash + poller)
     */
    public void stopTcpServer() {
        if (tcpServerProcessId != null) {
            bashExecutor.stopBackground(tcpServerProcessId);
            tcpServerProcessId = null;
        }
        if (tcpInboxPollerThread != null) {
            tcpInboxPollerThread.interrupt();
            tcpInboxPollerThread = null;
        }
    }
    
    // =====================================================================
    // ENVOI DE MESSAGES UDP (via send_udp.sh)
    // =====================================================================
    
    /**
     * Envoie un message UDP (fire-and-forget) via le script Bash send_udp.sh.
     * Aucun socket Java. Le script utilise socat ou netcat.
     */
    public void sendUdpMessage(String targetIp, int targetPort, JsonObject message) {
        String jsonMessage = new Gson().toJson(message);
        
        scheduler.submit(() -> {
            try {
                bashExecutor.executeSync("send_udp.sh", targetIp, String.valueOf(targetPort), jsonMessage);
            } catch (Exception e) {
                System.err.println("Erreur UDP send to " + targetIp + ":" + targetPort + " - " + e.getMessage());
            }
        });
    }
    
    // =====================================================================
    // SERVEUR UDP (via udp_server.sh)
    // =====================================================================
    
    /**
     * Demarre le serveur UDP pour recevoir les messages de jeu.
     * Le script udp_server.sh ecoute en continu et ecrit chaque message
     * recu sur stdout, qui est lu par BashExecutor.startBackground().
     */
    public void startUdpServer(int port, Consumer<JsonObject> messageHandler) {
        this.udpPort = port;
        running = true;
        
        udpServerProcessId = bashExecutor.startBackground(
            "udp_server.sh",
            output -> {
                // Chaque ligne de stdout est un message JSON recu par UDP
                if (output != null && !output.trim().isEmpty()) {
                    try {
                        JsonObject msg = JsonParser.parseString(output.trim()).getAsJsonObject();
                        if (messageHandler != null) {
                            scheduler.submit(() -> messageHandler.accept(msg));
                        }
                    } catch (Exception e) {
                        System.err.println("Erreur parsing UDP message: " + e.getMessage());
                    }
                }
            },
            String.valueOf(port)
        );
        System.out.println("UDP Server (Bash) demarre sur le port " + port);
    }
    
    /**
     * Arrete le serveur UDP (processus Bash)
     */
    public void stopUdpServer() {
        if (udpServerProcessId != null) {
            bashExecutor.stopBackground(udpServerProcessId);
            udpServerProcessId = null;
        }
    }
    
    /**
     * Retourne le port UDP actuel
     */
    public int getUdpPort() {
        return udpPort;
    }
    
    // =====================================================================
    // UTILITAIRES
    // =====================================================================
    
    /**
     * Recupere l'IP locale via le script Bash get_local_ip.sh
     */
    public String getLocalIp() {
        return bashExecutor.getLocalIp();
    }
    
    /**
     * Arrete tous les processus reseau
     */
    public void shutdown() {
        running = false;
        stopServerBroadcast();
        stopTcpServer();
        stopUdpServer();
        scheduler.shutdown();
        bashExecutor.shutdown();
    }
    
    // =====================================================================
    // CLASSE ServerInfo
    // =====================================================================
    
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
