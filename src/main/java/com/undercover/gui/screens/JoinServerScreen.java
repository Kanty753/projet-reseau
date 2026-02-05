package com.undercover.gui.screens;

import com.undercover.gui.*;
import com.undercover.network.NetworkBridge;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.animation.*;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Écran pour rejoindre une partie existante
 */
public class JoinServerScreen extends StackPane {
    
    private TextField playerNameField;
    private VBox serverListContainer;
    private NetworkBridge.ServerInfo selectedServer;
    private Label statusLabel;
    private ProgressIndicator loadingIndicator;
    private CheckBox includeLocalhostCheckbox;
    private Button joinButton;  // Bouton rejoindre
    private List<NetworkBridge.ServerInfo> currentServers = new ArrayList<>();
    
    private BiConsumer<String, NetworkBridge.ServerInfo> onJoinServer;
    private Runnable onBack;
    private Runnable onRefreshRequest;
    
    public JoinServerScreen() {
        setupUI();
    }
    
    private void setupUI() {
        // Fond blanc avec motifs animés
        setStyle("-fx-background-color: #FAFAFA;");
        
        Pane bgPatterns = UIComponents.createAnimatedWhiteBackground();
        
        VBox mainContent = new VBox(25);
        mainContent.setAlignment(Pos.TOP_CENTER);
        mainContent.setPadding(new Insets(40));
        mainContent.setMaxWidth(600);
        
        // Header
        HBox header = createHeader();
        
        // Champ nom du joueur
        VBox nameSection = createNameSection();
        
        // Liste des serveurs
        VBox serverSection = createServerSection();
        VBox.setVgrow(serverSection, Priority.ALWAYS);
        
        // Bouton rejoindre (stocké en variable d'instance)
        joinButton = new Button("🎮 Rejoindre la partie");
        joinButton.setStyle(
            "-fx-background-color: #CCCCCC;" +
            "-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;" +
            "-fx-padding: 15 40; -fx-background-radius: 25;"
        );
        joinButton.setOnAction(e -> handleJoin());
        joinButton.setDisable(true);
        
        mainContent.getChildren().addAll(header, nameSection, serverSection, joinButton);
        getChildren().addAll(bgPatterns, mainContent);
        
        UIComponents.slideIn(mainContent, 400);
    }
    
    private HBox createHeader() {
        HBox header = new HBox(20);
        header.setAlignment(Pos.CENTER_LEFT);
        
        Button backBtn = new Button("← Retour");
        backBtn.setStyle(
            "-fx-background-color: white; -fx-text-fill: #667EEA;" +
            "-fx-font-size: 14px; -fx-background-radius: 10;" +
            "-fx-border-color: #667EEA; -fx-border-radius: 10; -fx-cursor: hand;"
        );
        backBtn.setOnAction(e -> { if (onBack != null) onBack.run(); });
        
        VBox titleBox = new VBox(5);
        Label title = new Label("🔍 Rejoindre une partie");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        Label subtitle = new Label("Recherche des parties sur le réseau local...");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        titleBox.getChildren().addAll(title, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        
        // Indicateur de recherche
        loadingIndicator = new ProgressIndicator();
        loadingIndicator.setMaxSize(30, 30);
        loadingIndicator.setStyle("-fx-progress-color: #667EEA;");
        
        header.getChildren().addAll(backBtn, titleBox, loadingIndicator);
        return header;
    }
    
    private VBox createNameSection() {
        VBox section = new VBox(8);
        
        Label label = new Label("👤 Votre pseudo");
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        
        playerNameField = new TextField();
        playerNameField.setPromptText("Entrez votre nom...");
        playerNameField.setMaxWidth(Double.MAX_VALUE);
        playerNameField.setStyle(
            "-fx-background-color: white; -fx-text-fill: #333333;" +
            "-fx-prompt-text-fill: #999999; -fx-font-size: 14px;" +
            "-fx-padding: 12 15; -fx-background-radius: 10;" +
            "-fx-border-color: #E0E0E0; -fx-border-radius: 10;"
        );
        
        // Option localhost
        includeLocalhostCheckbox = new CheckBox("🏠 Inclure les parties locales (même PC)");
        includeLocalhostCheckbox.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666;");
        includeLocalhostCheckbox.setSelected(true);
        includeLocalhostCheckbox.setOnAction(e -> {
            if (onRefreshRequest != null) onRefreshRequest.run();
        });
        
        section.getChildren().addAll(label, playerNameField, includeLocalhostCheckbox);
        return section;
    }
    
    private VBox createServerSection() {
        VBox section = new VBox(15);
        section.setAlignment(Pos.TOP_CENTER);
        
        HBox headerRow = new HBox(10);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        
        Label title = new Label("📡 Parties disponibles");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        HBox.setHgrow(title, Priority.ALWAYS);
        
        Button refreshBtn = new Button("🔄 Actualiser");
        refreshBtn.setStyle(
            "-fx-background-color: white; -fx-text-fill: #667EEA;" +
            "-fx-font-size: 12px; -fx-background-radius: 8;" +
            "-fx-border-color: #667EEA; -fx-border-radius: 8; -fx-cursor: hand;"
        );
        refreshBtn.setOnAction(e -> refreshServerList());
        
        headerRow.getChildren().addAll(title, refreshBtn);
        
        // Container pour la liste des serveurs
        serverListContainer = new VBox(10);
        serverListContainer.setAlignment(Pos.TOP_CENTER);
        serverListContainer.setPadding(new Insets(10));
        serverListContainer.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 10;"
        );
        
        ScrollPane scrollPane = new ScrollPane(serverListContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPrefHeight(300);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        // Status label
        statusLabel = new Label("🔍 Recherche en cours...");
        statusLabel.setStyle(
            "-fx-font-size: 14px; -fx-text-fill: #666666;" +
            "-fx-background-color: rgba(255,255,255,0.8);" +
            "-fx-background-radius: 8; -fx-padding: 6 12;"
        );
        
        section.getChildren().addAll(headerRow, scrollPane, statusLabel);
        
        // Afficher placeholder initial
        showEmptyState();
        
        return section;
    }
    
    private void showEmptyState() {
        serverListContainer.getChildren().clear();
        
        VBox emptyState = new VBox(15);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setPadding(new Insets(40));
        emptyState.setStyle(
            "-fx-background-color: #F8F8F8;" +
            "-fx-background-radius: 10;"
        );
        
        Label icon = new Label("📡");
        icon.setStyle("-fx-font-size: 48px;");
        
        Label message = new Label("Aucune partie trouvée");
        message.setStyle("-fx-font-size: 16px; -fx-text-fill: #666666;");
        
        Label hint = new Label("Vérifiez que vous êtes sur le même réseau\nque le serveur de jeu, ou activez 'parties locales'");
        hint.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888; -fx-text-alignment: center;");
        hint.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        
        emptyState.getChildren().addAll(icon, message, hint);
        serverListContainer.getChildren().add(emptyState);
    }
    
    public void updateServerList(List<NetworkBridge.ServerInfo> servers) {
        Platform.runLater(() -> {
            currentServers = new ArrayList<>(servers);
            serverListContainer.getChildren().clear();
            
            if (servers.isEmpty()) {
                showEmptyState();
                statusLabel.setText("🔍 Aucune partie trouvée - Actualisation automatique...");
                return;
            }
            
            statusLabel.setText("✅ " + servers.size() + " partie(s) trouvée(s)");
            
            for (NetworkBridge.ServerInfo server : servers) {
                VBox card = createServerCard(server);
                serverListContainer.getChildren().add(card);
            }
        });
    }
    
    private VBox createServerCard(NetworkBridge.ServerInfo server) {
        VBox card = new VBox(8);
        card.setPadding(new Insets(15));
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setUserData(server);  // Stocker la référence du serveur
        
        boolean isSelected = selectedServer != null && selectedServer.equals(server);
        boolean isFull = server.currentPlayers >= server.maxPlayers;
        
        // Style blanc cohérent avec le thème
        String bgColor = isSelected ? "#E8E8FF" : "white";
        String borderColor = isSelected ? "#667EEA" : "#E0E0E0";
        
        card.setStyle(
            "-fx-background-color: " + bgColor + ";" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);"
        );
        
        HBox content = new HBox(15);
        content.setAlignment(Pos.CENTER_LEFT);
        
        // Icône
        StackPane iconContainer = new StackPane();
        Circle circle = new Circle(25);
        circle.setFill(Color.web(isFull ? "#F44336" : "#4CAF50", 0.2));
        Label icon = new Label(isFull ? "🔒" : "🎮");
        icon.setStyle("-fx-font-size: 24px;");
        iconContainer.getChildren().addAll(circle, icon);
        
        // Infos
        VBox info = new VBox(4);
        HBox.setHgrow(info, Priority.ALWAYS);
        
        Label nameLabel = new Label(server.name);
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        
        Label addressLabel = new Label(server.ip + ":" + server.port);
        addressLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888;");
        
        info.getChildren().addAll(nameLabel, addressLabel);
        
        // Afficher les noms des joueurs en petit
        if (server.playerNames != null && !server.playerNames.isEmpty()) {
            String names = String.join(", ", server.playerNames);
            Label namesLabel = new Label(names);
            namesLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #666666; -fx-font-style: italic;");
            namesLabel.setWrapText(true);
            namesLabel.setMaxWidth(200);
            info.getChildren().add(namesLabel);
        }
        
        // Joueurs
        VBox playersBox = new VBox(2);
        playersBox.setAlignment(Pos.CENTER_RIGHT);
        
        Label playersLabel = new Label(server.currentPlayers + "/" + server.maxPlayers);
        playersLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + 
                             (isFull ? "#F44336" : "#4CAF50") + ";");
        
        Label playersText = new Label("joueurs");
        playersText.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888;");
        
        playersBox.getChildren().addAll(playersLabel, playersText);
        
        content.getChildren().addAll(iconContainer, info, playersBox);
        card.getChildren().add(content);
        
        String normalStyle = card.getStyle();
        String hoverStyle = 
            "-fx-background-color: #F0F0FF;" +
            "-fx-background-radius: 12;" +
            "-fx-border-color: #667EEA;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 12;" +
            "-fx-effect: dropshadow(gaussian, rgba(102,126,234,0.2), 12, 0, 0, 3);";
        
        if (!isFull) {
            card.setOnMouseEntered(e -> {
                if (!server.equals(selectedServer)) {
                    card.setStyle(hoverStyle);
                }
            });
            
            card.setOnMouseExited(e -> {
                if (!server.equals(selectedServer)) {
                    card.setStyle(normalStyle);
                }
            });
            
            card.setOnMouseClicked(e -> selectServer(server));
        } else {
            card.setOpacity(0.6);
            card.setCursor(javafx.scene.Cursor.DEFAULT);
        }
        
        return card;
    }
    
    private void selectServer(NetworkBridge.ServerInfo server) {
        System.out.println("=== Serveur sélectionné: " + server);
        selectedServer = server;
        
        // Activer et styliser le bouton rejoindre
        joinButton.setDisable(false);
        joinButton.setStyle(
            "-fx-background-color: linear-gradient(to right, #667EEA, #764ba2);" +
            "-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;" +
            "-fx-padding: 15 40; -fx-background-radius: 25; -fx-cursor: hand;"
        );
        System.out.println("Bouton Rejoindre activé");
        
        // Animation de sélection - juste mettre à jour le status sans recréer les cartes
        statusLabel.setText("✅ Serveur sélectionné: " + server.name);
        
        // Mettre à jour visuellement les cartes pour montrer la sélection
        updateCardSelection();
    }
    
    private void updateCardSelection() {
        for (javafx.scene.Node node : serverListContainer.getChildren()) {
            if (node instanceof VBox card) {
                // Récupérer les données du serveur stockées dans les userData
                Object userData = card.getUserData();
                if (userData instanceof NetworkBridge.ServerInfo serverInfo) {
                    boolean isSelected = selectedServer != null && selectedServer.equals(serverInfo);
                    String bgColor = isSelected ? "#E8E8FF" : "white";
                    String borderColor = isSelected ? "#667EEA" : "#E0E0E0";
                    
                    card.setStyle(
                        "-fx-background-color: " + bgColor + ";" +
                        "-fx-background-radius: 12;" +
                        "-fx-border-color: " + borderColor + ";" +
                        "-fx-border-width: 2;" +
                        "-fx-border-radius: 12;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 8, 0, 0, 2);"
                    );
                }
            }
        }
    }
    
    private void refreshServerList() {
        loadingIndicator.setVisible(true);
        statusLabel.setText("🔍 Recherche en cours...");
        showEmptyState();
        // Le NetworkBridge appellera updateServerList
    }
    
    private void handleJoin() {
        System.out.println("=== handleJoin appelé ===");
        String playerName = playerNameField.getText().trim();
        
        if (playerName.isEmpty()) {
            System.out.println("Nom vide, shake du champ");
            UIComponents.shake(playerNameField);
            return;
        }
        
        if (selectedServer == null) {
            System.out.println("Aucun serveur sélectionné");
            statusLabel.setText("⚠️ Sélectionnez une partie d'abord");
            return;
        }
        
        System.out.println("Tentative de connexion: " + playerName + " -> " + selectedServer);
        
        if (onJoinServer != null) {
            System.out.println("Appel de onJoinServer...");
            onJoinServer.accept(playerName, selectedServer);
        } else {
            System.out.println("ERREUR: onJoinServer est null!");
        }
    }
    
    public void setOnJoinServer(BiConsumer<String, NetworkBridge.ServerInfo> handler) {
        this.onJoinServer = handler;
    }
    
    public void setOnBack(Runnable handler) {
        this.onBack = handler;
    }
    
    public void setOnRefreshRequest(Runnable handler) {
        this.onRefreshRequest = handler;
    }
    
    public boolean isIncludeLocalhost() {
        return includeLocalhostCheckbox.isSelected();
    }
    
    public void setSearching(boolean searching) {
        Platform.runLater(() -> {
            loadingIndicator.setVisible(searching);
        });
    }
}
