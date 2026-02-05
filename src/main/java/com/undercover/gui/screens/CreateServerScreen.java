package com.undercover.gui.screens;

import com.undercover.gui.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Rectangle;

import java.util.function.Consumer;

/**
 * Écran de configuration pour créer un serveur
 */
public class CreateServerScreen extends StackPane {
    
    private TextField playerNameField;
    private TextField sessionNameField;
    private Spinner<Integer> portSpinner;
    private Spinner<Integer> maxPlayersSpinner;
    private CheckBox localhostCheckbox;
    
    private Consumer<ServerConfig> onCreateServer;
    private Runnable onBack;
    
    public record ServerConfig(String playerName, String sessionName, int port, int maxPlayers, boolean localhostMode) {}
    
    public CreateServerScreen() {
        setupUI();
    }
    
    private void setupUI() {
        // Fond blanc avec motifs animés
        setStyle("-fx-background-color: #FAFAFA;");
        
        Pane bgPatterns = UIComponents.createAnimatedWhiteBackground();
        
        VBox mainContent = new VBox(30);
        mainContent.setAlignment(Pos.CENTER);
        mainContent.setPadding(new Insets(40));
        mainContent.setMaxWidth(500);
        
        // Header avec bouton retour
        HBox header = createHeader();
        
        // Formulaire
        VBox form = createForm();
        
        // Bouton créer
        Button createBtn = new Button("🚀 Créer la partie");
        createBtn.setStyle(
            "-fx-background-color: linear-gradient(to right, #667EEA, #764ba2);" +
            "-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;" +
            "-fx-padding: 15 40; -fx-background-radius: 25; -fx-cursor: hand;"
        );
        createBtn.setOnAction(e -> handleCreate());
        
        mainContent.getChildren().addAll(header, form, createBtn);
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
        Label title = new Label("🏠 Créer une partie");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        Label subtitle = new Label("Configurez votre serveur de jeu");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        titleBox.getChildren().addAll(title, subtitle);
        HBox.setHgrow(titleBox, Priority.ALWAYS);
        
        header.getChildren().addAll(backBtn, titleBox);
        return header;
    }
    
    private VBox createForm() {
        VBox form = new VBox(20);
        form.setPadding(new Insets(25));
        form.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 15;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 15, 0, 0, 3);"
        );
        form.setAlignment(Pos.CENTER_LEFT);
        
        // Nom du joueur
        VBox playerNameBox = createFormField("👤 Votre pseudo", "Entrez votre nom...");
        playerNameField = (TextField) playerNameBox.getChildren().get(1);
        
        // Nom de la session
        VBox sessionNameBox = createFormField("🎮 Nom de la partie", "Ma super partie...");
        sessionNameField = (TextField) sessionNameBox.getChildren().get(1);
        
        // Port et Max joueurs en ligne
        HBox numbersRow = new HBox(20);
        numbersRow.setAlignment(Pos.CENTER_LEFT);
        
        VBox portBox = new VBox(8);
        Label portLabel = new Label("🔌 Port");
        portLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        portSpinner = new Spinner<>(1024, 65535, 5000);
        portSpinner.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 8;");
        portSpinner.setPrefWidth(120);
        portBox.getChildren().addAll(portLabel, portSpinner);
        
        VBox maxPlayersBox = new VBox(8);
        Label maxLabel = new Label("👥 Joueurs max");
        maxLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        maxPlayersSpinner = new Spinner<>(3, 12, 6);
        maxPlayersSpinner.setStyle("-fx-background-color: #F5F5F5; -fx-background-radius: 8;");
        maxPlayersSpinner.setPrefWidth(120);
        maxPlayersBox.getChildren().addAll(maxLabel, maxPlayersSpinner);
        
        numbersRow.getChildren().addAll(portBox, maxPlayersBox);
        
        // Option mode localhost
        VBox localhostBox = new VBox(8);
        localhostCheckbox = new CheckBox("🏠 Mode Local (même PC)");
        localhostCheckbox.setStyle("-fx-font-size: 14px; -fx-text-fill: #444444;");
        Label localhostHint = new Label("Permet de jouer à plusieurs sur le même ordinateur");
        localhostHint.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");
        localhostBox.getChildren().addAll(localhostCheckbox, localhostHint);
        
        // Séparateur
        Rectangle separator = new Rectangle(0, 1);
        separator.widthProperty().bind(form.widthProperty().subtract(50));
        separator.setFill(Color.web("#E0E0E0"));
        
        // Info règles
        VBox rulesInfo = new VBox(10);
        rulesInfo.setPadding(new Insets(10));
        rulesInfo.setStyle("-fx-background-color: #F8F8FF; -fx-background-radius: 10;");
        Label rulesTitle = new Label("📋 Règles rapides");
        rulesTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #667EEA;");
        
        Label rules = new Label(
            "• 3-5 joueurs = 1 imposteur\n" +
            "• 6-9 joueurs = 2 imposteurs\n" +
            "• 10+ joueurs = 3 imposteurs"
        );
        rules.setStyle("-fx-font-size: 12px; -fx-text-fill: #666666;");
        rulesInfo.getChildren().addAll(rulesTitle, rules);
        
        form.getChildren().addAll(playerNameBox, sessionNameBox, numbersRow, localhostBox, separator, rulesInfo);
        return form;
    }
    
    private VBox createFormField(String labelText, String placeholder) {
        VBox field = new VBox(8);
        
        Label label = new Label(labelText);
        label.setStyle("-fx-font-size: 14px; -fx-text-fill: #666666;");
        
        TextField textField = new TextField();
        textField.setPromptText(placeholder);
        textField.setMaxWidth(Double.MAX_VALUE);
        textField.setStyle(
            "-fx-background-color: #F5F5F5; -fx-text-fill: #333333;" +
            "-fx-prompt-text-fill: #999999; -fx-font-size: 14px;" +
            "-fx-padding: 12 15; -fx-background-radius: 10;" +
            "-fx-border-color: #E0E0E0; -fx-border-radius: 10;"
        );
        
        field.getChildren().addAll(label, textField);
        return field;
    }
    
    private void handleCreate() {
        String playerName = playerNameField.getText().trim();
        String sessionName = sessionNameField.getText().trim();
        
        if (playerName.isEmpty()) {
            UIComponents.shake(playerNameField);
            return;
        }
        
        if (sessionName.isEmpty()) {
            sessionName = playerName + "'s Game";
        }
        
        if (onCreateServer != null) {
            onCreateServer.accept(new ServerConfig(
                playerName,
                sessionName,
                portSpinner.getValue(),
                maxPlayersSpinner.getValue(),
                localhostCheckbox.isSelected()
            ));
        }
    }
    
    public void setOnCreateServer(Consumer<ServerConfig> handler) {
        this.onCreateServer = handler;
    }
    
    public void setOnBack(Runnable handler) {
        this.onBack = handler;
    }
}
