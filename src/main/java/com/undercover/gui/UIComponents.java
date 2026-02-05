package com.undercover.gui;

import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.util.Duration;

/**
 * Composants UI réutilisables avec un design moderne
 */
public class UIComponents {
    
    public static Button createPrimaryButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("btn-primary");
        btn.setMinWidth(200);
        addHoverAnimation(btn);
        return btn;
    }
    
    public static Button createSecondaryButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("btn-secondary");
        btn.setMinWidth(200);
        addHoverAnimation(btn);
        return btn;
    }
    
    public static Button createDangerButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("btn-danger");
        addHoverAnimation(btn);
        return btn;
    }
    
    public static Button createSuccessButton(String text) {
        Button btn = new Button(text);
        btn.getStyleClass().add("btn-success");
        addHoverAnimation(btn);
        return btn;
    }
    
    public static TextField createTextField(String placeholder) {
        TextField field = new TextField();
        field.setPromptText(placeholder);
        field.getStyleClass().add("text-field-modern");
        field.setMaxWidth(400);
        return field;
    }
    
    public static Spinner<Integer> createSpinner(int min, int max, int initial) {
        Spinner<Integer> spinner = new Spinner<>(min, max, initial);
        spinner.getStyleClass().add("spinner");
        spinner.setEditable(true);
        spinner.setPrefWidth(150);
        return spinner;
    }
    
    public static VBox createCard() {
        VBox card = new VBox(15);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        return card;
    }
    
    public static Label createTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("title-label");
        return label;
    }
    
    public static Label createSubtitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("subtitle-label");
        return label;
    }
    
    public static Label createSectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }
    
    public static HBox createChatMessage(String sender, String message, String type, String time) {
        HBox container = new HBox(10);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPadding(new Insets(5, 0, 5, 0));
        
        VBox bubble = new VBox(5);
        bubble.getStyleClass().add("chat-message");
        
        String extraClass = switch (type) {
            case "SYSTEM" -> "chat-message-system";
            case "WORD" -> "chat-message-word";
            case "VOTE" -> "chat-message-vote";
            default -> "";
        };
        if (!extraClass.isEmpty()) {
            bubble.getStyleClass().add(extraClass);
        }
        
        HBox header = new HBox(10);
        Label senderLabel = new Label(sender);
        senderLabel.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #6C63FF;");
        Label timeLabel = new Label(time);
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #6B7280;");
        header.getChildren().addAll(senderLabel, timeLabel);
        
        Label messageLabel = new Label(message);
        messageLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: white;");
        messageLabel.setWrapText(true);
        
        bubble.getChildren().addAll(header, messageLabel);
        container.getChildren().add(bubble);
        
        return container;
    }
    
    public static void addHoverAnimation(Node node) {
        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(150), node);
        scaleUp.setToX(1.02);
        scaleUp.setToY(1.02);
        
        ScaleTransition scaleDown = new ScaleTransition(Duration.millis(150), node);
        scaleDown.setToX(1.0);
        scaleDown.setToY(1.0);
        
        node.setOnMouseEntered(e -> scaleUp.playFromStart());
        node.setOnMouseExited(e -> scaleDown.playFromStart());
    }
    
    public static void fadeIn(Node node, int durationMs) {
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }
    
    public static void slideIn(Node node, int durationMs) {
        node.setTranslateY(30);
        node.setOpacity(0);
        
        TranslateTransition slide = new TranslateTransition(Duration.millis(durationMs), node);
        slide.setFromY(30);
        slide.setToY(0);
        
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        
        ParallelTransition parallel = new ParallelTransition(slide, fade);
        parallel.play();
    }
    
    public static void pulse(Node node) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(500), node);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.1);
        scale.setToY(1.1);
        scale.setCycleCount(Animation.INDEFINITE);
        scale.setAutoReverse(true);
        scale.play();
    }
    
    public static void shake(Node node) {
        TranslateTransition tt = new TranslateTransition(Duration.millis(50), node);
        tt.setFromX(0);
        tt.setByX(10);
        tt.setCycleCount(6);
        tt.setAutoReverse(true);
        tt.play();
    }
    
    /**
     * Crée un fond blanc avec des cercles gris translucides animés
     * Design unifié pour toutes les scènes
     */
    public static Pane createAnimatedWhiteBackground() {
        Pane pane = new Pane();
        pane.setMouseTransparent(true);
        pane.setStyle("-fx-background-color: #FAFAFA;");
        
        java.util.Random rand = new java.util.Random();
        String[] colors = {"#9C27B0", "#667EEA", "#4CAF50", "#E0E0E0", "#B0BEC5"};
        
        for (int i = 0; i < 25; i++) {
            Circle circle = new Circle();
            circle.setRadius(rand.nextInt(40) + 15);
            String color = colors[rand.nextInt(colors.length)];
            circle.setFill(Color.web(color, 0.08 + rand.nextDouble() * 0.12));
            circle.setCenterX(rand.nextInt(1400));
            circle.setCenterY(rand.nextInt(900));
            
            // Animation de déplacement lent
            TranslateTransition move = new TranslateTransition(
                Duration.seconds(15 + rand.nextInt(20)), circle);
            move.setByX(rand.nextInt(150) - 75);
            move.setByY(rand.nextInt(150) - 75);
            move.setAutoReverse(true);
            move.setCycleCount(Animation.INDEFINITE);
            move.play();
            
            // Animation d'opacité
            FadeTransition fade = new FadeTransition(
                Duration.seconds(8 + rand.nextInt(12)), circle);
            fade.setFromValue(0.1);
            fade.setToValue(0.25);
            fade.setAutoReverse(true);
            fade.setCycleCount(Animation.INDEFINITE);
            fade.play();
            
            pane.getChildren().add(circle);
        }
        
        return pane;
    }
    
    /**
     * Crée une carte de texte avec background translucide pour une meilleure lisibilité
     */
    public static Label createReadableLabel(String text, String textColor, double fontSize) {
        Label label = new Label(text);
        label.setStyle(
            "-fx-font-size: " + fontSize + "px;" +
            "-fx-text-fill: " + textColor + ";" +
            "-fx-background-color: rgba(255, 255, 255, 0.85);" +
            "-fx-background-radius: 8;" +
            "-fx-padding: 8 15;"
        );
        return label;
    }
}
