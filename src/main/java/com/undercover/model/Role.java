package com.undercover.model;

/**
 * Roles des joueurs dans le jeu Undercover
 * Seulement 2 roles: CITIZEN et IMPOSTOR
 * L'imposteur ne recoit pas de mot, il doit deviner celui des citoyens
 */
public enum Role {
    CITIZEN("Citoyen", "[C]", "#48BB78"),
    IMPOSTOR("Imposteur", "[I]", "#F56565");
    
    private final String displayName;
    private final String icon;
    private final String color;
    
    Role(String displayName, String icon, String color) {
        this.displayName = displayName;
        this.icon = icon;
        this.color = color;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getEmoji() {
        return icon;
    }
    
    public String getColor() {
        return color;
    }
    
    public String getFullDisplay() {
        return icon + " " + displayName;
    }
}
