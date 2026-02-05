package com.undercover.model;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Represente un joueur dans le jeu
 */
public class Player implements Serializable {
    private static final long serialVersionUID = 2L;
    
    private final String id;
    private final String name;
    private final String ipAddress;
    private final int port;
    private int udpPort;  // Port UDP pour les messages de jeu (rapide)
    
    private Role role;
    private boolean alive;
    private boolean hasVoted;
    private String votedFor;
    private String spokenWord;
    private boolean isHost;
    private boolean isReady;
    
    public Player(String name, String ipAddress, int port) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.udpPort = port + 1000;  // Par defaut, UDP = TCP + 1000
        this.role = Role.CITIZEN;
        this.alive = true;
        this.hasVoted = false;
        this.isHost = false;
        this.isReady = false;
    }
    
    public Player(String name, String ipAddress, int port, int udpPort) {
        this(name, ipAddress, port);
        this.udpPort = udpPort;
    }
    
    /**
     * Constructeur avec ID explicite (pour recreer un joueur depuis le reseau)
     */
    public Player(String id, String name, String ipAddress, int port, int udpPort) {
        this.id = id;
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.udpPort = udpPort;
        this.role = Role.CITIZEN;
        this.alive = true;
        this.hasVoted = false;
        this.isHost = false;
        this.isReady = false;
    }
    
    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getIpAddress() { return ipAddress; }
    public int getPort() { return port; }
    public int getUdpPort() { return udpPort; }
    public Role getRole() { return role; }
    public boolean isAlive() { return alive; }
    public boolean hasVoted() { return hasVoted; }
    public String getVotedFor() { return votedFor; }
    public String getSpokenWord() { return spokenWord; }
    public boolean isHost() { return isHost; }
    public boolean isReady() { return isReady; }
    
    // Setters
    public void setRole(Role role) { this.role = role; }
    public void setAlive(boolean alive) { this.alive = alive; }
    public void setHasVoted(boolean hasVoted) { this.hasVoted = hasVoted; }
    public void setVotedFor(String votedFor) { this.votedFor = votedFor; }
    public void setSpokenWord(String word) { this.spokenWord = word; }
    public void setHost(boolean host) { this.isHost = host; }
    public void setReady(boolean ready) { this.isReady = ready; }
    public void setUdpPort(int udpPort) { this.udpPort = udpPort; }
    
    public void resetForNewRound() {
        this.hasVoted = false;
        this.votedFor = null;
        this.spokenWord = null;
    }
    
    public String getAddress() {
        return ipAddress + ":" + port;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return id.equals(player.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return name + (isHost ? " ⭐" : "") + (alive ? "" : " ☠️");
    }
}
