package com.undercover.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Message de chat ou evenement systeme
 */
public class GameMessage implements Serializable {
    private static final long serialVersionUID = 2L;
    
    public enum Type {
        CHAT(""),
        SYSTEM(""),
        WORD(""),
        VOTE(""),
        ELIMINATION(""),
        VICTORY(""),
        GUESS(""),
        JOIN(""),
        LEAVE("");
        
        private final String icon;
        Type(String icon) { this.icon = icon; }
        public String getIcon() { return icon; }
    }
    
    private final String senderId;
    private final String senderName;
    private final String content;
    private final Type type;
    private final LocalDateTime timestamp;
    
    public GameMessage(String senderId, String senderName, String content, Type type) {
        this.senderId = senderId;
        this.senderName = senderName;
        this.content = content;
        this.type = type;
        this.timestamp = LocalDateTime.now();
    }
    
    public static GameMessage system(String content) {
        return new GameMessage("SYSTEM", "Systeme", content, Type.SYSTEM);
    }
    
    public static GameMessage word(Player player, String word) {
        return new GameMessage(player.getId(), player.getName(), word, Type.WORD);
    }
    
    public static GameMessage vote(Player voter, Player target) {
        String content = voter.getName() + " a vote contre " + target.getName();
        return new GameMessage(voter.getId(), voter.getName(), content, Type.VOTE);
    }
    
    public static GameMessage elimination(Player eliminated) {
        return new GameMessage("SYSTEM", "Systeme", 
            eliminated.getName() + " a ete elimine ! (etait " + eliminated.getRole().getDisplayName() + ")", 
            Type.ELIMINATION);
    }
    
    public static GameMessage victory(String team, String message) {
        return new GameMessage("SYSTEM", team, message, Type.VICTORY);
    }
    
    // Getters
    public String getSenderId() { return senderId; }
    public String getSenderName() { return senderName; }
    public String getContent() { return content; }
    public Type getType() { return type; }
    public LocalDateTime getTimestamp() { return timestamp; }
    
    public String getFormattedTime() {
        return timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }
    
    public String getDisplayText() {
        return switch (type) {
            case CHAT -> String.format("[%s] %s: %s", getFormattedTime(), senderName, content);
            case WORD -> String.format("%s dit: %s", senderName, content);
            case SYSTEM, ELIMINATION, VICTORY -> content;
            case VOTE -> content;
            case GUESS -> String.format("%s tente de deviner: %s", senderName, content);
            case JOIN -> String.format("%s a rejoint la partie", senderName);
            case LEAVE -> String.format("%s a quitte la partie", senderName);
        };
    }
}
