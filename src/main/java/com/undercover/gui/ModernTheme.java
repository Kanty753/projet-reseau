package com.undercover.gui;

/**
 * Thème moderne pour l'application UNDERCOVER
 * Inspiré du design Material/Fluent avec un style sombre
 */
public class ModernTheme {
    
    // Couleurs principales
    public static final String PRIMARY = "#6C63FF";      // Violet principal
    public static final String PRIMARY_DARK = "#5A52D5";
    public static final String SECONDARY = "#FF6584";    // Rose accent
    public static final String SUCCESS = "#4CAF50";      // Vert
    public static final String WARNING = "#FFC107";      // Jaune
    public static final String DANGER = "#F44336";       // Rouge
    public static final String INFO = "#2196F3";         // Bleu
    
    // Couleurs de fond
    public static final String BG_DARK = "#1A1A2E";
    public static final String BG_MEDIUM = "#16213E";
    public static final String BG_LIGHT = "#0F3460";
    public static final String BG_CARD = "#1F2937";
    
    // Couleurs de texte
    public static final String TEXT_PRIMARY = "#FFFFFF";
    public static final String TEXT_SECONDARY = "#9CA3AF";
    public static final String TEXT_MUTED = "#6B7280";
    
    // Couleurs des rôles
    public static final String CITIZEN_COLOR = "#4CAF50";
    public static final String IMPOSTOR_COLOR = "#F44336";
    public static final String MR_WHITE_COLOR = "#9C27B0";
    
    /**
     * CSS global de l'application
     */
    public static String getGlobalCSS() {
        return """
            .root {
                -fx-font-family: 'Segoe UI', 'Roboto', sans-serif;
                -fx-background-color: #1A1A2E;
            }
            .title-label {
                -fx-font-size: 42px;
                -fx-font-weight: bold;
                -fx-text-fill: white;
            }
            .subtitle-label {
                -fx-font-size: 18px;
                -fx-text-fill: #9CA3AF;
            }
            .section-title {
                -fx-font-size: 24px;
                -fx-font-weight: bold;
                -fx-text-fill: white;
            }
            .btn-primary {
                -fx-background-color: linear-gradient(to right, #6C63FF, #5A52D5);
                -fx-text-fill: white;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-padding: 15 40 15 40;
                -fx-background-radius: 25;
                -fx-cursor: hand;
            }
            .btn-primary:hover {
                -fx-background-color: linear-gradient(to right, #7B73FF, #6962E5);
            }
            .btn-secondary {
                -fx-background-color: transparent;
                -fx-border-color: #6C63FF;
                -fx-border-width: 2;
                -fx-border-radius: 25;
                -fx-text-fill: #6C63FF;
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-padding: 15 40 15 40;
                -fx-background-radius: 25;
                -fx-cursor: hand;
            }
            .btn-secondary:hover {
                -fx-background-color: rgba(108, 99, 255, 0.1);
            }
            .btn-danger {
                -fx-background-color: linear-gradient(to right, #F44336, #D32F2F);
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 10 25 10 25;
                -fx-background-radius: 20;
                -fx-cursor: hand;
            }
            .btn-success {
                -fx-background-color: linear-gradient(to right, #4CAF50, #388E3C);
                -fx-text-fill: white;
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-padding: 10 25 10 25;
                -fx-background-radius: 20;
                -fx-cursor: hand;
            }
            .btn-icon {
                -fx-background-color: rgba(255, 255, 255, 0.1);
                -fx-text-fill: white;
                -fx-font-size: 18px;
                -fx-padding: 10 15 10 15;
                -fx-background-radius: 10;
                -fx-cursor: hand;
            }
            .btn-icon:hover {
                -fx-background-color: rgba(255, 255, 255, 0.2);
            }
            .text-field-modern {
                -fx-background-color: #2D3748;
                -fx-text-fill: white;
                -fx-prompt-text-fill: #6B7280;
                -fx-font-size: 16px;
                -fx-padding: 15 20 15 20;
                -fx-background-radius: 10;
                -fx-border-color: transparent;
            }
            .text-field-modern:focused {
                -fx-background-color: #374151;
                -fx-border-color: #6C63FF;
                -fx-border-width: 2;
                -fx-border-radius: 10;
            }
            .card {
                -fx-background-color: #1F2937;
                -fx-background-radius: 15;
                -fx-padding: 20;
            }
            .card-hover {
                -fx-background-color: #1F2937;
                -fx-background-radius: 15;
                -fx-padding: 15;
                -fx-cursor: hand;
            }
            .card-hover:hover {
                -fx-background-color: #2D3748;
            }
            .player-card {
                -fx-background-color: #2D3748;
                -fx-background-radius: 10;
                -fx-padding: 12 15 12 15;
            }
            .player-name {
                -fx-font-size: 16px;
                -fx-font-weight: bold;
                -fx-text-fill: white;
            }
            .player-status {
                -fx-font-size: 12px;
                -fx-text-fill: #9CA3AF;
            }
            .chat-container {
                -fx-background-color: #16213E;
                -fx-background-radius: 15;
            }
            .chat-message {
                -fx-background-color: #2D3748;
                -fx-background-radius: 10;
                -fx-padding: 10 15 10 15;
            }
            .chat-message-system {
                -fx-background-color: rgba(108, 99, 255, 0.2);
            }
            .chat-message-word {
                -fx-background-color: rgba(76, 175, 80, 0.2);
            }
            .chat-message-vote {
                -fx-background-color: rgba(255, 193, 7, 0.2);
            }
            .chat-input {
                -fx-background-color: #2D3748;
                -fx-text-fill: white;
                -fx-prompt-text-fill: #6B7280;
                -fx-font-size: 14px;
                -fx-padding: 12 15 12 15;
                -fx-background-radius: 25;
            }
            .phase-indicator {
                -fx-background-color: linear-gradient(to right, #6C63FF, #FF6584);
                -fx-background-radius: 20;
                -fx-padding: 8 20 8 20;
            }
            .phase-text {
                -fx-font-size: 14px;
                -fx-font-weight: bold;
                -fx-text-fill: white;
            }
            .timer-display {
                -fx-font-size: 48px;
                -fx-font-weight: bold;
                -fx-text-fill: white;
            }
            .scroll-pane {
                -fx-background-color: transparent;
                -fx-background: transparent;
            }
            .scroll-pane .viewport {
                -fx-background-color: transparent;
            }
            .scroll-bar {
                -fx-background-color: transparent;
            }
            .scroll-bar .thumb {
                -fx-background-color: rgba(255, 255, 255, 0.3);
                -fx-background-radius: 5;
            }
            .spinner {
                -fx-background-color: #2D3748;
                -fx-background-radius: 10;
            }
            .spinner .text-field {
                -fx-background-color: transparent;
                -fx-text-fill: white;
            }
            """;
    }
}
