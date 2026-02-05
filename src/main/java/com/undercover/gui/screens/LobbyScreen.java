package com.undercover.gui.screens;

import com.undercover.gui.*;
import com.undercover.model.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;

import java.util.List;
import java.util.function.Consumer;

/**
 * Écran du lobby en attente de joueurs
 */
public class LobbyScreen extends StackPane {
    
    private final boolean isHost;
    private VBox playerListContainer;
    private Label statusLabel;
    private Label playerCountLabel;
    private Button startButton;
    private String sessionName;
    private String serverAddress;
    
    private Runnable onStartGame;
    private Runnable onLeave;
    
    public LobbyScreen(boolean isHost, String sessionName, String serverAddress) {
        this.isHost = isHost;
        this.sessionName = sessionName;
        this.serverAddress = serverAddress;
        setupUI();
    }
    
    private void setupUI() {
        // Fond blanc avec motifs animés
        setStyle("-fx-background-color: #FAFAFA;");
        
        Pane bgPatterns = UIComponents.createAnimatedWhiteBackground();
        
        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(30));
        
        // Header
        VBox header = createHeader();
        layout.setTop(header);
        
        // Centre - Liste des joueurs
        VBox center = createPlayerSection();
        layout.setCenter(center);
        BorderPane.setMargin(center, new Insets(20, 0, 20, 0));
        
        // Footer - Boutons d'action
        HBox footer = createFooter();
        layout.setBottom(footer);
        
        getChildren().addAll(bgPatterns, layout);
        UIComponents.slideIn(layout, 400);
    }
    
    private VBox createHeader() {
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);
        
        // Titre de la session
        Label title = new Label("🎮 " + sessionName);
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        
        // Adresse du serveur
        HBox addressBox = new HBox(10);
        addressBox.setAlignment(Pos.CENTER);
        addressBox.setPadding(new Insets(10, 20, 10, 20));
        addressBox.setStyle(
            "-fx-background-color: rgba(102, 126, 234, 0.15);" +
            "-fx-background-radius: 20;"
        );
        
        Label addressLabel = new Label("📡 " + serverAddress);
        addressLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #555555;");
        
        Button copyBtn = new Button("📋");
        copyBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #667EEA; -fx-cursor: hand;");
        copyBtn.setTooltip(new Tooltip("Copier l'adresse"));
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(serverAddress);
            clipboard.setContent(content);
        });
        
        addressBox.getChildren().addAll(addressLabel, copyBtn);
        
        // Status
        statusLabel = new Label("⏳ En attente de joueurs...");
        statusLabel.setStyle(
            "-fx-font-size: 16px; -fx-text-fill: #E67E22;" +
            "-fx-background-color: rgba(255,255,255,0.8);" +
            "-fx-background-radius: 10; -fx-padding: 8 15;"
        );
        
        header.getChildren().addAll(title, addressBox, statusLabel);
        return header;
    }
    
    private VBox createPlayerSection() {
        VBox section = new VBox(15);
        section.setAlignment(Pos.TOP_CENTER);
        
        // Header de la liste
        HBox listHeader = new HBox(10);
        listHeader.setAlignment(Pos.CENTER_LEFT);
        
        Label playersTitle = new Label("👥 Joueurs");
        playersTitle.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        HBox.setHgrow(playersTitle, Priority.ALWAYS);
        
        playerCountLabel = new Label("0/6");
        playerCountLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: #667EEA;");
        
        listHeader.getChildren().addAll(playersTitle, playerCountLabel);
        
        // Container pour les joueurs
        playerListContainer = new VBox(10);
        playerListContainer.setAlignment(Pos.TOP_CENTER);
        playerListContainer.setPadding(new Insets(10));
        
        ScrollPane scrollPane = new ScrollPane(playerListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // Indication minimum joueurs
        Label minPlayers = new Label("💡 Minimum 3 joueurs pour commencer");
        minPlayers.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: #888888;" +
            "-fx-background-color: rgba(255,255,255,0.7);" +
            "-fx-background-radius: 8; -fx-padding: 5 10;"
        );
        
        section.getChildren().addAll(listHeader, scrollPane, minPlayers);
        return section;
    }
    
    private HBox createFooter() {
        HBox footer = new HBox(20);
        footer.setAlignment(Pos.CENTER);
        
        Button leaveBtn = new Button("🚪 Quitter");
        leaveBtn.setStyle(
            "-fx-background-color: white; -fx-text-fill: #E53E3E;" +
            "-fx-font-size: 14px; -fx-font-weight: bold;" +
            "-fx-padding: 12 25; -fx-background-radius: 20;" +
            "-fx-border-color: #E53E3E; -fx-border-radius: 20; -fx-cursor: hand;"
        );
        leaveBtn.setOnAction(e -> { if (onLeave != null) onLeave.run(); });
        
        if (isHost) {
            startButton = new Button("🚀 Lancer la partie");
            startButton.setStyle(
                "-fx-background-color: #CCC; -fx-text-fill: white;" +
                "-fx-font-size: 16px; -fx-font-weight: bold;" +
                "-fx-padding: 15 40; -fx-background-radius: 25; -fx-cursor: hand;"
            );
            startButton.setDisable(true);
            startButton.setOnAction(e -> {
                System.out.println("=== Bouton Lancer clique ===");
                System.out.println("onStartGame est null? " + (onStartGame == null));
                if (onStartGame != null) {
                    System.out.println("Appel de onStartGame.run()");
                    onStartGame.run();
                }
            });
            footer.getChildren().addAll(leaveBtn, startButton);
        } else {
            Label waitingLabel = new Label("⏳ En attente que l'hôte lance la partie...");
            waitingLabel.setStyle(
                "-fx-font-size: 14px; -fx-text-fill: #666666;" +
                "-fx-background-color: rgba(255,255,255,0.7);" +
                "-fx-background-radius: 10; -fx-padding: 8 15;"
            );
            footer.getChildren().addAll(leaveBtn, waitingLabel);
        }
        
        return footer;
    }
    
    public void updatePlayers(List<Player> players, int maxPlayers) {
        Platform.runLater(() -> {
            playerListContainer.getChildren().clear();
            playerCountLabel.setText(players.size() + "/" + maxPlayers);
            
            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                HBox card = createPlayerCard(player, i + 1);
                playerListContainer.getChildren().add(card);
                
                // Animation d'entrée échelonnée
                card.setOpacity(0);
                card.setTranslateX(-20);
                javafx.animation.Timeline timeline = new javafx.animation.Timeline(
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(i * 100),
                        new javafx.animation.KeyValue(card.opacityProperty(), 0),
                        new javafx.animation.KeyValue(card.translateXProperty(), -20)
                    ),
                    new javafx.animation.KeyFrame(javafx.util.Duration.millis(i * 100 + 300),
                        new javafx.animation.KeyValue(card.opacityProperty(), 1),
                        new javafx.animation.KeyValue(card.translateXProperty(), 0)
                    )
                );
                timeline.play();
            }
            
            // Activer le bouton start si assez de joueurs (minimum 2 pour tests, 3 en production)
            final int MIN_PLAYERS = 2;
            System.out.println("=== updatePlayers() ===");
            System.out.println("isHost: " + isHost);
            System.out.println("startButton null? " + (startButton == null));
            System.out.println("players.size(): " + players.size());
            
            if (isHost && startButton != null) {
                boolean canStart = players.size() >= MIN_PLAYERS;
                System.out.println("canStart: " + canStart);
                startButton.setDisable(!canStart);
                
                if (canStart) {
                    statusLabel.setText("✅ Prêt à lancer ! (" + players.size() + " joueurs)");
                    statusLabel.setStyle(
                        "-fx-font-size: 16px; -fx-text-fill: #27AE60;" +
                        "-fx-background-color: rgba(255,255,255,0.9);" +
                        "-fx-background-radius: 10; -fx-padding: 8 15;"
                    );
                    // Style plus visible pour le bouton activé
                    startButton.setStyle(
                        "-fx-background-color: linear-gradient(to right, #48BB78, #38A169);" +
                        "-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;" +
                        "-fx-padding: 15 40; -fx-background-radius: 25; -fx-cursor: hand;"
                    );
                    System.out.println("BOUTON ACTIVE - Style applique");
                } else {
                    statusLabel.setText("⏳ En attente de joueurs... (" + (MIN_PLAYERS - players.size()) + " de plus)");
                    statusLabel.setStyle(
                        "-fx-font-size: 16px; -fx-text-fill: #E67E22;" +
                        "-fx-background-color: rgba(255,255,255,0.8);" +
                        "-fx-background-radius: 10; -fx-padding: 8 15;"
                    );
                    startButton.setStyle(
                        "-fx-background-color: #CCC; -fx-text-fill: white;" +
                        "-fx-font-size: 16px; -fx-font-weight: bold;" +
                        "-fx-padding: 15 40; -fx-background-radius: 25; -fx-cursor: hand;"
                    );
                }
            }
        });
    }
    
    private HBox createPlayerCard(Player player, int position) {
        HBox card = new HBox(15);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(15));
        card.setMaxWidth(400);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);"
        );
        
        // Position
        Label posLabel = new Label("#" + position);
        posLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #888888;");
        posLabel.setMinWidth(30);
        
        // Avatar
        StackPane avatar = new StackPane();
        Circle circle = new Circle(22);
        circle.setFill(Color.web(player.isHost() ? "#F39C12" : "#667EEA"));
        Label initial = new Label(player.getName().substring(0, 1).toUpperCase());
        initial.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        avatar.getChildren().addAll(circle, initial);
        
        // Nom
        VBox nameBox = new VBox(2);
        HBox.setHgrow(nameBox, Priority.ALWAYS);
        
        Label nameLabel = new Label(player.getName());
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        
        Label statusLbl = new Label(player.isHost() ? "⭐ Hôte" : "Joueur");
        statusLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: " + 
                          (player.isHost() ? "#F39C12" : "#888888") + ";");
        
        nameBox.getChildren().addAll(nameLabel, statusLbl);
        
        // Badge ready
        Label readyBadge = new Label(player.isReady() ? "✅" : "⏳");
        readyBadge.setStyle("-fx-font-size: 20px;");
        
        card.getChildren().addAll(posLabel, avatar, nameBox, readyBadge);
        return card;
    }
    
    public void setOnStartGame(Runnable handler) {
        this.onStartGame = handler;
    }
    
    public void setOnLeave(Runnable handler) {
        this.onLeave = handler;
    }
}
