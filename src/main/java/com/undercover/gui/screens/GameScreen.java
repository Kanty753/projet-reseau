package com.undercover.gui.screens;

import com.undercover.gui.*;
import com.undercover.model.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.util.Duration;

import java.util.*;
import java.util.function.Consumer;

/**
 * Ecran principal du jeu - Design avec CARDS joueurs
 * 
 * SYNCHRONISATION:
 * - Le timer est synchronise par le serveur (TIMER_SYNC)
 * - Le joueur actuel est indique sur sa CARD (TURN_START)
 * - L'input est desactive si ce n'est pas notre tour
 */
public class GameScreen extends StackPane {
    
    private final Player localPlayer;
    
    // Containers principaux
    private FlowPane playerCardsContainer;
    private VBox chatSection;
    private VBox chatContainer;
    private ScrollPane chatScrollPane;
    private TextField chatInput;
    private Button sendChatBtn;
    
    // Header elements
    private Label phaseLabel;
    private Label timerLabel;
    private Label secretWordLabel;
    private Label roleLabel;
    private Label turnIndicator;
    
    // Action elements
    private VBox actionPanel;
    private TextField wordInput;
    private Button speakBtn;
    private Button guessButton;
    
    // Callbacks
    private Consumer<String> onSendChat;
    private Consumer<String> onSpeakWord;
    private Consumer<String> onVote;
    private Consumer<String> onGuess;
    private Runnable onLeave;
    
    // State
    private GameSession.State currentState;
    private Timeline timerTimeline;
    private int remainingSeconds;
    private boolean chatEnabled = false;
    
    // Synchronisation des tours
    private String currentSpeakerId;
    private List<String> turnOrder = new ArrayList<>();
    private List<Player> currentPlayers = new ArrayList<>();
    
    // Track des mots prononces par joueur
    private final Map<String, List<String>> spokenWordsByPlayer = new LinkedHashMap<>();
    
    // Animation du background
    private Timeline backgroundAnimation;
    
    public GameScreen(Player localPlayer, String word, Role role) {
        this.localPlayer = localPlayer;
        setupUI(word, role);
        startBackgroundAnimation();
    }
    
    private void setupUI(String word, Role role) {
        // Fond blanc avec motifs gris animes (via CSS)
        setStyle("-fx-background-color: #FAFAFA;");
        
        // Conteneur pour les motifs animes
        Pane patternsPane = createAnimatedPatterns();
        
        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(15));
        layout.setStyle("-fx-background-color: transparent;");
        
        // === TOP: Header avec phase, timer et role ===
        VBox top = createHeader(word, role);
        layout.setTop(top);
        BorderPane.setMargin(top, new Insets(0, 0, 15, 0));
        
        // === CENTER: Cartes des joueurs ===
        VBox center = createPlayerCardsSection();
        layout.setCenter(center);
        
        // === BOTTOM: Actions et Chat ===
        HBox bottom = createBottomPanel(role);
        layout.setBottom(bottom);
        BorderPane.setMargin(bottom, new Insets(15, 0, 0, 0));
        
        getChildren().addAll(patternsPane, layout);
        
        // Charger le CSS depuis le classpath (independant du repertoire parent)
        java.net.URL cssResource = getClass().getResource("/styles/game.css");
        if (cssResource != null) {
            getStylesheets().add(cssResource.toExternalForm());
        } else {
            System.out.println("CSS non charge - utilisation du style par defaut");
        }
    }
    
    /**
     * Cree le panneau avec les motifs gris animes
     */
    private Pane createAnimatedPatterns() {
        Pane patternsPane = new Pane();
        patternsPane.setMouseTransparent(true);
        
        // Creer plusieurs cercles/formes grises
        Random rand = new Random();
        for (int i = 0; i < 20; i++) {
            Circle circle = new Circle();
            circle.setRadius(rand.nextInt(30) + 10);
            circle.setFill(Color.web("#E0E0E0", 0.3 + rand.nextDouble() * 0.3));
            circle.setCenterX(rand.nextInt(1200));
            circle.setCenterY(rand.nextInt(800));
            
            // Animation de deplacement lent
            TranslateTransition move = new TranslateTransition(
                Duration.seconds(10 + rand.nextInt(20)), circle);
            move.setByX(rand.nextInt(100) - 50);
            move.setByY(rand.nextInt(100) - 50);
            move.setAutoReverse(true);
            move.setCycleCount(Animation.INDEFINITE);
            move.play();
            
            // Animation d'opacite
            FadeTransition fade = new FadeTransition(
                Duration.seconds(5 + rand.nextInt(10)), circle);
            fade.setFromValue(0.2);
            fade.setToValue(0.5);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();
            
            patternsPane.getChildren().add(circle);
        }
        
        return patternsPane;
    }
    
    private void startBackgroundAnimation() {
        // Animation supplementaire si necessaire
    }
    
    private VBox createHeader(String word, Role role) {
        VBox header = new VBox(10);
        header.setAlignment(Pos.CENTER);
        
        // Ligne 1: Phase et Timer
        HBox topRow = new HBox(30);
        topRow.setAlignment(Pos.CENTER);
        topRow.setPadding(new Insets(15, 25, 10, 25));
        topRow.setStyle(
            "-fx-background-color: linear-gradient(to right, #667eea, #764ba2);" +
            "-fx-background-radius: 20;"
        );
        
        phaseLabel = new Label("En attente...");
        phaseLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: white;");
        
        timerLabel = new Label("--:--");
        timerLabel.setStyle(
            "-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: white;" +
            "-fx-background-color: rgba(0,0,0,0.3); -fx-background-radius: 10;" +
            "-fx-padding: 5 15;"
        );
        
        // Bouton quitter
        Button leaveBtn = new Button("X");
        leaveBtn.setStyle(
            "-fx-background-color: #ff6b6b;" +
            "-fx-text-fill: white; -fx-font-weight: bold;" +
            "-fx-background-radius: 50%; -fx-min-width: 35; -fx-min-height: 35;"
        );
        leaveBtn.setOnAction(e -> { if (onLeave != null) onLeave.run(); });
        
        Region spacer1 = new Region();
        Region spacer2 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);
        HBox.setHgrow(spacer2, Priority.ALWAYS);
        
        topRow.getChildren().addAll(phaseLabel, spacer1, timerLabel, spacer2, leaveBtn);
        
        // Ligne 2: Indicateur de tour (qui parle maintenant)
        turnIndicator = new Label("En attente du premier tour...");
        turnIndicator.setStyle(
            "-fx-font-size: 16px; -fx-font-weight: bold;" +
            "-fx-text-fill: #333333;" +
            "-fx-background-color: #FFE066;" +
            "-fx-background-radius: 10; -fx-padding: 8 20;"
        );
        turnIndicator.setVisible(false);
        
        // Ligne 3: Role et Mot secret
        HBox roleRow = new HBox(20);
        roleRow.setAlignment(Pos.CENTER);
        roleRow.setPadding(new Insets(10, 20, 10, 20));
        
        boolean isImpostor = role == Role.IMPOSTOR;
        String roleColor = isImpostor ? "#E53E3E" : "#38A169";
        
        roleLabel = new Label(role.getDisplayName().toUpperCase());
        roleLabel.setStyle(
            "-fx-font-size: 16px; -fx-font-weight: bold;" +
            "-fx-text-fill: " + roleColor + ";" +
            "-fx-background-color: white;" +
            "-fx-border-color: " + roleColor + ";" +
            "-fx-border-width: 2; -fx-border-radius: 15;" +
            "-fx-background-radius: 15; -fx-padding: 8 15;"
        );
        
        if (isImpostor) {
            secretWordLabel = new Label("Vous n'avez pas de mot - Devinez celui des citoyens!");
            secretWordLabel.setStyle(
                "-fx-font-size: 14px; -fx-font-style: italic;" +
                "-fx-text-fill: #E53E3E;"
            );
        } else {
            secretWordLabel = new Label("Votre mot: " + word);
            secretWordLabel.setStyle(
                "-fx-font-size: 18px; -fx-font-weight: bold;" +
                "-fx-text-fill: #2B6CB0;" +
                "-fx-background-color: white;" +
                "-fx-border-color: #2B6CB0; -fx-border-width: 2;" +
                "-fx-border-radius: 10; -fx-background-radius: 10;" +
                "-fx-padding: 8 15;"
            );
        }
        
        roleRow.getChildren().addAll(roleLabel, secretWordLabel);
        
        header.getChildren().addAll(topRow, turnIndicator, roleRow);
        return header;
    }
    
    private VBox createPlayerCardsSection() {
        VBox section = new VBox(10);
        section.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(section, Priority.ALWAYS);
        
        Label title = new Label("Joueurs");
        title.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #4A5568;");
        
        playerCardsContainer = new FlowPane(15, 15);
        playerCardsContainer.setAlignment(Pos.CENTER);
        playerCardsContainer.setPadding(new Insets(10));
        
        ScrollPane scroll = new ScrollPane(playerCardsContainer);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        
        section.getChildren().addAll(title, scroll);
        return section;
    }
    
    private HBox createBottomPanel(Role role) {
        HBox bottom = new HBox(15);
        bottom.setAlignment(Pos.CENTER);
        bottom.setPrefHeight(180);
        
        // === Panneau Actions (gauche) ===
        actionPanel = new VBox(10);
        actionPanel.setAlignment(Pos.CENTER);
        actionPanel.setPrefWidth(300);
        actionPanel.setPadding(new Insets(15));
        actionPanel.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 15;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);"
        );
        
        Label actionTitle = new Label("Actions");
        actionTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #667EEA;");
        
        // Input mot
        HBox wordBox = new HBox(8);
        wordBox.setAlignment(Pos.CENTER);
        
        wordInput = new TextField();
        wordInput.setPromptText("Votre mot...");
        wordInput.setPrefWidth(180);
        wordInput.setStyle(
            "-fx-background-color: #F7FAFC; -fx-text-fill: #2D3748;" +
            "-fx-prompt-text-fill: #A0AEC0; -fx-background-radius: 8;" +
            "-fx-border-color: #E2E8F0; -fx-border-radius: 8;"
        );
        wordInput.setOnAction(e -> speakWord());
        
        speakBtn = new Button("Parler");
        speakBtn.setStyle(
            "-fx-background-color: #48BB78; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-background-radius: 8;"
        );
        speakBtn.setOnAction(e -> speakWord());
        
        wordBox.getChildren().addAll(wordInput, speakBtn);
        
        actionPanel.getChildren().addAll(actionTitle, wordBox);
        
        // Bouton deviner pour imposteur
        if (role == Role.IMPOSTOR) {
            guessButton = new Button("Deviner le mot secret");
            guessButton.setStyle(
                "-fx-background-color: linear-gradient(to right, #ED64A6, #D53F8C);" +
                "-fx-text-fill: white; -fx-font-weight: bold;" +
                "-fx-background-radius: 8; -fx-padding: 8 20;"
            );
            guessButton.setOnAction(e -> showGuessDialog());
            actionPanel.getChildren().add(guessButton);
        }
        
        // === Panneau Chat (droite) ===
        chatSection = new VBox(8);
        chatSection.setPrefWidth(400);
        chatSection.setPadding(new Insets(10));
        chatSection.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 15;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);"
        );
        HBox.setHgrow(chatSection, Priority.ALWAYS);
        
        Label chatTitle = new Label("Chat (disponible apres la phase des mots)");
        chatTitle.setStyle("-fx-font-size: 12px; -fx-text-fill: #718096;");
        
        chatContainer = new VBox(5);
        chatScrollPane = new ScrollPane(chatContainer);
        chatScrollPane.setFitToWidth(true);
        chatScrollPane.setPrefHeight(100);
        chatScrollPane.setStyle("-fx-background: transparent; -fx-background-color: #F7FAFC; -fx-background-radius: 8;");
        VBox.setVgrow(chatScrollPane, Priority.ALWAYS);
        
        HBox chatInputBox = new HBox(8);
        chatInputBox.setAlignment(Pos.CENTER);
        
        chatInput = new TextField();
        chatInput.setPromptText("Message...");
        chatInput.setDisable(true);
        chatInput.setStyle(
            "-fx-background-color: #F7FAFC; -fx-text-fill: #2D3748;" +
            "-fx-prompt-text-fill: #A0AEC0; -fx-background-radius: 8;" +
            "-fx-border-color: #E2E8F0; -fx-border-radius: 8;"
        );
        chatInput.setOnAction(e -> sendChat());
        HBox.setHgrow(chatInput, Priority.ALWAYS);
        
        sendChatBtn = new Button("Envoyer");
        sendChatBtn.setDisable(true);
        sendChatBtn.setStyle(
            "-fx-background-color: #4299E1; -fx-text-fill: white;" +
            "-fx-font-weight: bold; -fx-background-radius: 8;"
        );
        sendChatBtn.setOnAction(e -> sendChat());
        
        chatInputBox.getChildren().addAll(chatInput, sendChatBtn);
        chatSection.getChildren().addAll(chatTitle, chatScrollPane, chatInputBox);
        
        // Initialement le chat est desactive
        setChatEnabled(false);
        
        bottom.getChildren().addAll(actionPanel, chatSection);
        return bottom;
    }
    
    private void sendChat() {
        if (!chatEnabled) return;
        String message = chatInput.getText().trim();
        if (!message.isEmpty() && onSendChat != null) {
            onSendChat.accept(message);
            chatInput.clear();
        }
    }
    
    private void speakWord() {
        String word = wordInput.getText().trim();
        if (!word.isEmpty() && onSpeakWord != null) {
            onSpeakWord.accept(word);
            wordInput.clear();
            wordInput.setDisable(true);
            speakBtn.setDisable(true);
        }
    }
    
    private void showGuessDialog() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Deviner le mot secret");
        dialog.setHeaderText("Tentative de l'Imposteur");
        dialog.setContentText("Quel est le mot des citoyens ?");
        
        dialog.showAndWait().ifPresent(guess -> {
            if (!guess.trim().isEmpty() && onGuess != null) {
                onGuess.accept(guess.trim());
            }
        });
    }
    
    // ===== METHODES DE MISE A JOUR =====
    
    /**
     * Met a jour les cartes des joueurs avec leurs mots prononces
     */
    public void updatePlayers(List<Player> players) {
        this.currentPlayers = new ArrayList<>(players);
        Platform.runLater(() -> {
            playerCardsContainer.getChildren().clear();
            
            for (Player player : players) {
                VBox card = createPlayerCard(player);
                playerCardsContainer.getChildren().add(card);
            }
        });
    }
    
    /**
     * Cree une carte visuelle pour un joueur
     * En phase de vote, la carte entiere est cliquable
     */
    private VBox createPlayerCard(Player player) {
        VBox card = new VBox(8);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(180);
        card.setMinHeight(140);
        card.setPadding(new Insets(15));
        
        boolean isLocal = player.getId().equals(localPlayer.getId());
        boolean isAlive = player.isAlive();
        boolean isCurrentSpeaker = player.getId().equals(currentSpeakerId);
        boolean isVotingPhase = currentState == GameSession.State.VOTING;
        boolean canBeVoted = isVotingPhase && isAlive && !isLocal;
        
        // Couleur de fond selon etat
        String bgColor;
        String borderColor;
        String borderWidth = "2";
        
        if (!isAlive) {
            bgColor = "#F0F0F0";
            borderColor = "#CCCCCC";
        } else if (isCurrentSpeaker) {
            // Joueur dont c'est le tour - surbrillance jaune/orange
            bgColor = "#FFF9E6";
            borderColor = "#F6AD55";
            borderWidth = "3";
        } else if (isLocal) {
            // Notre carte - bleu clair
            bgColor = "#EBF8FF";
            borderColor = "#4299E1";
        } else if (canBeVoted) {
            // Peut être voté - style spécial
            bgColor = "white";
            borderColor = "#E53E3E";
        } else {
            bgColor = "white";
            borderColor = "#E0E0E0";
        }
        
        String baseStyle = 
            "-fx-background-color: " + bgColor + ";" +
            "-fx-background-radius: 15;" +
            "-fx-border-color: " + borderColor + ";" +
            "-fx-border-radius: 15; -fx-border-width: " + borderWidth + ";" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.12), 10, 0, 0, 3);";
        
        card.setStyle(baseStyle);
        
        // Rendre la carte cliquable en phase de vote
        if (canBeVoted) {
            card.setCursor(javafx.scene.Cursor.HAND);
            String hoverStyle = 
                "-fx-background-color: #FFF5F5;" +
                "-fx-background-radius: 15;" +
                "-fx-border-color: #E53E3E;" +
                "-fx-border-radius: 15; -fx-border-width: 3;" +
                "-fx-effect: dropshadow(gaussian, rgba(229,62,62,0.4), 15, 0, 0, 4);";
            
            card.setOnMouseEntered(e -> card.setStyle(hoverStyle));
            card.setOnMouseExited(e -> card.setStyle(baseStyle));
            card.setOnMouseClicked(e -> {
                if (onVote != null) onVote.accept(player.getId());
            });
        }
        
        // Badge "A VOUS" ou "VOTEZ" selon la phase
        if (isCurrentSpeaker && isAlive && !isVotingPhase) {
            Label turnBadge = new Label("🎯 A VOUS!");
            turnBadge.setStyle(
                "-fx-font-size: 11px; -fx-font-weight: bold;" +
                "-fx-text-fill: white; -fx-background-color: #E53E3E;" +
                "-fx-background-radius: 12; -fx-padding: 3 10;"
            );
            card.getChildren().add(turnBadge);
        } else if (canBeVoted) {
            Label voteBadge = new Label("🗳️ Cliquez pour voter");
            voteBadge.setStyle(
                "-fx-font-size: 10px; -fx-font-weight: bold;" +
                "-fx-text-fill: white; -fx-background-color: #E53E3E;" +
                "-fx-background-radius: 10; -fx-padding: 2 8;"
            );
            card.getChildren().add(voteBadge);
        }
        
        // Avatar (première lettre)
        StackPane avatar = new StackPane();
        Circle avatarCircle = new Circle(22);
        avatarCircle.setFill(Color.web(isAlive ? "#667EEA" : "#AAAAAA"));
        Label avatarLabel = new Label(player.getName().substring(0, 1).toUpperCase());
        avatarLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: white;");
        avatar.getChildren().addAll(avatarCircle, avatarLabel);
        
        // Nom du joueur
        Label nameLabel = new Label(player.getName() + (isLocal ? " (vous)" : ""));
        nameLabel.setStyle(
            "-fx-font-size: 14px; -fx-font-weight: bold;" +
            "-fx-text-fill: " + (isAlive ? "#333333" : "#999999") + ";" +
            (isAlive ? "" : "-fx-strikethrough: true;")
        );
        
        // Separateur
        Rectangle sep = new Rectangle(120, 1);
        sep.setFill(Color.web("#E0E0E0"));
        
        // Container pour les mots prononces - TOUJOURS VISIBLE
        VBox wordsContainer = new VBox(4);
        wordsContainer.setAlignment(Pos.CENTER);
        wordsContainer.setPadding(new Insets(5, 0, 0, 0));
        
        List<String> words = spokenWordsByPlayer.getOrDefault(player.getId(), new ArrayList<>());
        if (words.isEmpty()) {
            Label noWord = new Label(isCurrentSpeaker ? "⏳ En attente..." : "—");
            noWord.setStyle("-fx-font-size: 11px; -fx-text-fill: #AAAAAA; -fx-font-style: italic;");
            wordsContainer.getChildren().add(noWord);
        } else {
            for (int i = 0; i < words.size(); i++) {
                Label wordLabel = new Label("💬 " + words.get(i));
                wordLabel.setStyle(
                    "-fx-font-size: 12px; -fx-text-fill: #5D4E37;" +
                    "-fx-background-color: #FFF8DC;" +
                    "-fx-background-radius: 8; -fx-padding: 4 10;"
                );
                wordsContainer.getChildren().add(wordLabel);
            }
        }
        
        card.getChildren().addAll(avatar, nameLabel, sep, wordsContainer);
        
        // Indicateur mort
        if (!isAlive) {
            Label deadLabel = new Label("☠️ ÉLIMINÉ");
            deadLabel.setStyle(
                "-fx-font-size: 11px; -fx-text-fill: #E53E3E;" +
                "-fx-font-weight: bold;" +
                "-fx-background-color: #FFE0E0;" +
                "-fx-background-radius: 8; -fx-padding: 3 8;"
            );
            card.getChildren().add(deadLabel);
        }
        
        return card;
    }
    
    /**
     * Enregistre un mot prononce par un joueur
     */
    public void recordSpokenWord(String playerId, String word) {
        Platform.runLater(() -> {
            spokenWordsByPlayer.computeIfAbsent(playerId, k -> new ArrayList<>()).add(word);
        });
    }
    
    /**
     * Met a jour le tour actuel (synchronisation)
     */
    public void updateCurrentTurn(String currentPlayerId, int remainingSeconds, List<String> newTurnOrder) {
        Platform.runLater(() -> {
            this.currentSpeakerId = currentPlayerId;
            this.turnOrder = new ArrayList<>(newTurnOrder);
            
            // Trouver le nom du joueur actuel
            String playerName = "Joueur";
            for (Player p : currentPlayers) {
                if (p.getId().equals(currentPlayerId)) {
                    playerName = p.getName();
                    break;
                }
            }
            
            boolean isMyTurn = currentPlayerId != null && currentPlayerId.equals(localPlayer.getId());
            
            // Mettre a jour l'indicateur de tour
            turnIndicator.setVisible(true);
            if (isMyTurn) {
                turnIndicator.setText("C'est VOTRE tour ! (" + remainingSeconds + "s)");
                turnIndicator.setStyle(
                    "-fx-font-size: 16px; -fx-font-weight: bold;" +
                    "-fx-text-fill: white;" +
                    "-fx-background-color: #E53E3E;" +
                    "-fx-background-radius: 10; -fx-padding: 8 20;"
                );
                // Activer l'input
                wordInput.setDisable(false);
                speakBtn.setDisable(false);
                wordInput.requestFocus();
            } else {
                turnIndicator.setText("Tour de " + playerName + " (" + remainingSeconds + "s)");
                turnIndicator.setStyle(
                    "-fx-font-size: 16px; -fx-font-weight: bold;" +
                    "-fx-text-fill: #333333;" +
                    "-fx-background-color: #FFE066;" +
                    "-fx-background-radius: 10; -fx-padding: 8 20;"
                );
                // Desactiver l'input
                wordInput.setDisable(true);
                speakBtn.setDisable(true);
            }
            
            // Mettre a jour le timer
            startTimer(remainingSeconds);
            
            // Rafraichir les cartes pour montrer le joueur actuel
            if (!currentPlayers.isEmpty()) {
                updatePlayers(currentPlayers);
            }
        });
    }
    
    /**
     * Synchronise le timer avec le serveur
     */
    public void syncTimer(int remainingSeconds) {
        Platform.runLater(() -> {
            this.remainingSeconds = remainingSeconds;
            updateTimerDisplay();
        });
    }
    
    /**
     * Met a jour la phase de jeu
     */
    public void updatePhase(GameSession.State state, int seconds) {
        Platform.runLater(() -> {
            this.currentState = state;
            phaseLabel.setText(state.getDisplay());
            
            switch (state) {
                case WORD_PHASE -> {
                    // L'activation de l'input depend du tour actuel
                    // (sera gere par updateCurrentTurn)
                    wordInput.setDisable(true);
                    speakBtn.setDisable(true);
                    setChatEnabled(false);
                    turnIndicator.setVisible(true);
                }
                case DEBATE -> {
                    wordInput.setDisable(true);
                    speakBtn.setDisable(true);
                    setChatEnabled(true);
                    turnIndicator.setText("Phase de debat - Discutez!");
                    turnIndicator.setStyle(
                        "-fx-font-size: 16px; -fx-font-weight: bold;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-color: #4299E1;" +
                        "-fx-background-radius: 10; -fx-padding: 8 20;"
                    );
                    // Reset le speaker actuel
                    currentSpeakerId = null;
                    if (!currentPlayers.isEmpty()) {
                        updatePlayers(currentPlayers);
                    }
                }
                case VOTING -> {
                    setChatEnabled(true);
                    turnIndicator.setText("Phase de vote - Votez!");
                    turnIndicator.setStyle(
                        "-fx-font-size: 16px; -fx-font-weight: bold;" +
                        "-fx-text-fill: white;" +
                        "-fx-background-color: #E53E3E;" +
                        "-fx-background-radius: 10; -fx-padding: 8 20;"
                    );
                    // Rafraichir les cartes pour montrer les boutons de vote
                    if (!currentPlayers.isEmpty()) {
                        updatePlayers(currentPlayers);
                    }
                }
                case RESULT -> {
                    turnIndicator.setText("Resultat du vote");
                    turnIndicator.setStyle(
                        "-fx-font-size: 16px; -fx-font-weight: bold;" +
                        "-fx-text-fill: #333333;" +
                        "-fx-background-color: #FFE066;" +
                        "-fx-background-radius: 10; -fx-padding: 8 20;"
                    );
                }
                case FINISHED -> {
                    setChatEnabled(false);
                    turnIndicator.setVisible(false);
                    showFinishedUI();
                }
            }
            
            startTimer(seconds);
        });
    }
    
    /**
     * Active ou desactive le chat
     */
    public void setChatEnabled(boolean enabled) {
        this.chatEnabled = enabled;
        Platform.runLater(() -> {
            chatInput.setDisable(!enabled);
            sendChatBtn.setDisable(!enabled);
            
            if (enabled) {
                chatSection.setStyle(
                    "-fx-background-color: white;" +
                    "-fx-background-radius: 15;" +
                    "-fx-border-color: #48BB78; -fx-border-radius: 15; -fx-border-width: 2;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2);"
                );
            } else {
                chatSection.setStyle(
                    "-fx-background-color: #F7FAFC;" +
                    "-fx-background-radius: 15;" +
                    "-fx-opacity: 0.7;" +
                    "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.05), 5, 0, 0, 1);"
                );
            }
        });
    }
    
    private void startTimer(int seconds) {
        if (timerTimeline != null) {
            timerTimeline.stop();
        }
        
        remainingSeconds = seconds;
        updateTimerDisplay();
        
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            updateTimerDisplay();
            if (remainingSeconds <= 0) {
                timerTimeline.stop();
            }
        }));
        timerTimeline.setCycleCount(seconds);
        timerTimeline.play();
    }
    
    private void updateTimerDisplay() {
        int mins = remainingSeconds / 60;
        int secs = remainingSeconds % 60;
        String time = String.format("%02d:%02d", mins, secs);
        
        String color = remainingSeconds > 30 ? "white" : 
                       remainingSeconds > 10 ? "#F6E05E" : "#FC8181";
        
        timerLabel.setText(time);
        timerLabel.setStyle(
            "-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: " + color + ";" +
            "-fx-background-color: rgba(0,0,0,0.3); -fx-background-radius: 10; -fx-padding: 5 15;"
        );
        
        // Mettre a jour aussi l'indicateur de tour si visible
        if (turnIndicator.isVisible() && currentSpeakerId != null) {
            boolean isMyTurn = currentSpeakerId.equals(localPlayer.getId());
            if (isMyTurn) {
                turnIndicator.setText("C'est VOTRE tour ! (" + remainingSeconds + "s)");
            } else {
                String playerName = "Joueur";
                for (Player p : currentPlayers) {
                    if (p.getId().equals(currentSpeakerId)) {
                        playerName = p.getName();
                        break;
                    }
                }
                turnIndicator.setText("Tour de " + playerName + " (" + remainingSeconds + "s)");
            }
        }
    }
    
    private void showFinishedUI() {
        if (timerTimeline != null) {
            timerTimeline.stop();
        }
        timerLabel.setText("FIN");
    }
    
    /**
     * Ajoute un message au chat
     */
    public void addMessage(GameMessage message) {
        Platform.runLater(() -> {
            // Si c'est un mot prononce, l'enregistrer pour la carte
            if (message.getType() == GameMessage.Type.WORD) {
                recordSpokenWord(message.getSenderId(), message.getContent());
                // Rafraichir les cartes
                if (!currentPlayers.isEmpty()) {
                    updatePlayers(currentPlayers);
                }
            }
            
            HBox msgBox = createMessageBox(message);
            chatContainer.getChildren().add(msgBox);
            
            // Auto-scroll
            chatScrollPane.setVvalue(1.0);
        });
    }
    
    private HBox createMessageBox(GameMessage message) {
        HBox box = new HBox(8);
        box.setPadding(new Insets(6, 10, 6, 10));
        box.setAlignment(Pos.CENTER_LEFT);
        
        String bgColor;
        String textColor;
        String icon = "";
        boolean isImportant = false;
        
        switch (message.getType()) {
            case WORD -> {
                bgColor = "#FFF8DC";
                textColor = "#5D4E37";
                icon = "💬 ";
            }
            case SYSTEM, ELIMINATION -> {
                bgColor = "#E8F4FD";
                textColor = "#1E5F8A";
                icon = "ℹ️ ";
            }
            case VICTORY -> {
                bgColor = "#D4EDDA";
                textColor = "#155724";
                icon = "🏆 ";
                isImportant = true;
            }
            case VOTE -> {
                bgColor = "#F8D7DA";
                textColor = "#721C24";
                icon = "🗳️ ";
            }
            case GUESS -> {
                // Message de tentative de l'imposteur - très visible
                bgColor = "#FFE0B2";
                textColor = "#E65100";
                icon = "🎯 ";
                isImportant = true;
            }
            default -> {
                bgColor = "#F5F5F5";
                textColor = "#555555";
            }
        }
        
        box.setStyle(
            "-fx-background-color: " + bgColor + ";" +
            "-fx-background-radius: 8;" +
            (isImportant ? "-fx-border-color: " + textColor + "; -fx-border-radius: 8; -fx-border-width: 2;" : "")
        );
        
        Label content = new Label(icon + message.getDisplayText());
        content.setStyle(
            "-fx-font-size: " + (isImportant ? "14" : "12") + "px;" +
            "-fx-text-fill: " + textColor + ";" +
            (isImportant ? "-fx-font-weight: bold;" : "")
        );
        content.setWrapText(true);
        
        box.getChildren().add(content);
        return box;
    }
    
    /**
     * Affiche l'ecran de victoire
     */
    public void showVictory(String message) {
        Platform.runLater(() -> {
            VBox overlay = new VBox(20);
            overlay.setAlignment(Pos.CENTER);
            overlay.setStyle("-fx-background-color: rgba(255,255,255,0.95);");
            
            Label trophy = new Label("VICTOIRE!");
            trophy.setStyle("-fx-font-size: 48px; -fx-font-weight: bold; -fx-text-fill: #D69E2E;");
            
            Label msg = new Label(message);
            msg.setStyle("-fx-font-size: 20px; -fx-text-fill: #2D3748;");
            msg.setWrapText(true);
            msg.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            msg.setMaxWidth(400);
            
            Button backBtn = new Button("Retour au menu");
            backBtn.setStyle(
                "-fx-background-color: linear-gradient(to right, #667eea, #764ba2);" +
                "-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;" +
                "-fx-background-radius: 10; -fx-padding: 12 30;"
            );
            backBtn.setOnAction(e -> { if (onLeave != null) onLeave.run(); });
            
            overlay.getChildren().addAll(trophy, msg, backBtn);
            getChildren().add(overlay);
            
            // Animation fade in
            FadeTransition fade = new FadeTransition(Duration.millis(500), overlay);
            fade.setFromValue(0);
            fade.setToValue(1);
            fade.play();
        });
    }
    
    // ===== SETTERS POUR LES HANDLERS =====
    
    public void setOnSendChat(Consumer<String> handler) { this.onSendChat = handler; }
    public void setOnSpeakWord(Consumer<String> handler) { this.onSpeakWord = handler; }
    public void setOnVote(Consumer<String> handler) { this.onVote = handler; }
    public void setOnGuess(Consumer<String> handler) { this.onGuess = handler; }
    public void setOnLeave(Runnable handler) { this.onLeave = handler; }
}
