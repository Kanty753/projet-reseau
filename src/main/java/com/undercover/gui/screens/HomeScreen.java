package com.undercover.gui.screens;

import com.undercover.gui.*;
import javafx.animation.*;
import javafx.geometry.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;
import javafx.util.Duration;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Écran d'accueil moderne avec choix du mode (Créer/Rejoindre)
 */
public class HomeScreen extends StackPane {
    
    private Consumer<HomeAction> onAction;
    
    public enum HomeAction {
        CREATE_SERVER,
        JOIN_SERVER
    }
    
    public HomeScreen() {
        setupUI();
    }
    
    private void setupUI() {
        // Fond blanc avec motifs animés
        setStyle("-fx-background-color: #FAFAFA;");
        
        // Particules décoratives en arrière-plan
        Pane particles = UIComponents.createAnimatedWhiteBackground();
        
        // Conteneur principal
        VBox mainContent = new VBox(40);
        mainContent.setAlignment(Pos.CENTER);
        mainContent.setPadding(new Insets(50));
        mainContent.setMaxWidth(600);
        
        // Logo/Titre animé
        VBox titleSection = createTitleSection();
        
        // Boutons de sélection
        VBox buttonSection = createButtonSection();
        
        // Footer sans version
        VBox footerSection = createFooterSection();
        
        mainContent.getChildren().addAll(titleSection, buttonSection, footerSection);
        
        getChildren().addAll(particles, mainContent);
        
        // Animation d'entrée
        animateEntrance(mainContent);
    }
    
    private VBox createTitleSection() {
        VBox section = new VBox(15);
        section.setAlignment(Pos.CENTER);
        
        // Icône du jeu
        Label gameIcon = new Label("🎭");
        gameIcon.setStyle("-fx-font-size: 80px;");
        UIComponents.pulse(gameIcon);
        
        // Titre principal
        Label title = new Label("UNDERCOVER");
        title.setStyle(
            "-fx-font-size: 48px; -fx-font-weight: bold;" +
            "-fx-text-fill: #333333;" +
            "-fx-effect: dropshadow(gaussian, rgba(102, 126, 234, 0.4), 15, 0, 0, 2);"
        );
        
        // Sous-titre avec fond translucide pour lisibilité
        Label subtitle = new Label("Jeu Multijoueur LAN - Trouvez l'Imposteur!");
        subtitle.setStyle(
            "-fx-font-size: 16px; -fx-text-fill: #666666;" +
            "-fx-background-color: rgba(255,255,255,0.8);" +
            "-fx-background-radius: 15; -fx-padding: 8 20;"
        );
        
        // Ligne décorative
        Rectangle line = new Rectangle(200, 3);
        line.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.TRANSPARENT),
            new Stop(0.5, Color.web("#667EEA")),
            new Stop(1, Color.TRANSPARENT)
        ));
        
        section.getChildren().addAll(gameIcon, title, line, subtitle);
        return section;
    }
    
    private VBox createButtonSection() {
        VBox section = new VBox(20);
        section.setAlignment(Pos.CENTER);
        
        // Carte: Créer une partie
        VBox createCard = createModeCard(
            "🏠",
            "Créer une partie",
            "Hébergez un serveur et invitez vos amis à rejoindre",
            "#4CAF50",
            () -> {
                if (onAction != null) onAction.accept(HomeAction.CREATE_SERVER);
            }
        );
        
        // Carte: Rejoindre une partie
        VBox joinCard = createModeCard(
            "🔍",
            "Rejoindre une partie",
            "Recherchez et rejoignez une partie sur le réseau local",
            "#2196F3",
            () -> {
                if (onAction != null) onAction.accept(HomeAction.JOIN_SERVER);
            }
        );
        
        section.getChildren().addAll(createCard, joinCard);
        return section;
    }
    
    private VBox createModeCard(String icon, String title, String description, 
                                String accentColor, Runnable onClick) {
        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(25, 30, 25, 30));
        card.setMaxWidth(450);
        card.setCursor(javafx.scene.Cursor.HAND);
        
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-background-radius: 15;" +
            "-fx-border-color: #E0E0E0;" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 15;" +
            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.08), 10, 0, 0, 3);"
        );
        
        HBox content = new HBox(20);
        content.setAlignment(Pos.CENTER_LEFT);
        
        // Icône
        StackPane iconContainer = new StackPane();
        iconContainer.setMinSize(60, 60);
        iconContainer.setMaxSize(60, 60);
        iconContainer.setStyle(
            "-fx-background-color: " + accentColor + "22;" +
            "-fx-background-radius: 15;"
        );
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 30px;");
        iconContainer.getChildren().add(iconLabel);
        
        // Texte
        VBox textBox = new VBox(5);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #333333;");
        Label descLabel = new Label(description);
        descLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #666666;");
        descLabel.setWrapText(true);
        textBox.getChildren().addAll(titleLabel, descLabel);
        HBox.setHgrow(textBox, Priority.ALWAYS);
        
        // Flèche
        Label arrow = new Label("→");
        arrow.setStyle("-fx-font-size: 24px; -fx-text-fill: " + accentColor + ";");
        
        content.getChildren().addAll(iconContainer, textBox, arrow);
        card.getChildren().add(content);
        
        // Hover effects
        String normalStyle = card.getStyle();
        String hoverStyle = 
            "-fx-background-color: white;" +
            "-fx-background-radius: 15;" +
            "-fx-border-color: " + accentColor + ";" +
            "-fx-border-width: 2;" +
            "-fx-border-radius: 15;" +
            "-fx-effect: dropshadow(gaussian, " + accentColor + "33, 15, 0, 0, 5);";
        
        card.setOnMouseEntered(e -> {
            card.setStyle(hoverStyle);
            ScaleTransition st = new ScaleTransition(Duration.millis(150), card);
            st.setToX(1.02);
            st.setToY(1.02);
            st.play();
        });
        
        card.setOnMouseExited(e -> {
            card.setStyle(normalStyle);
            ScaleTransition st = new ScaleTransition(Duration.millis(150), card);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
        
        card.setOnMouseClicked(e -> onClick.run());
        
        return card;
    }
    
    private VBox createFooterSection() {
        VBox section = new VBox(10);
        section.setAlignment(Pos.CENTER);
        
        Label hint = new Label("💡 Assurez-vous d'être sur le même réseau local");
        hint.setStyle(
            "-fx-font-size: 12px; -fx-text-fill: #888888;" +
            "-fx-background-color: rgba(255,255,255,0.7);" +
            "-fx-background-radius: 10; -fx-padding: 6 12;"
        );
        
        section.getChildren().add(hint);
        return section;
    }
    
    private void animateEntrance(VBox content) {
        content.setOpacity(0);
        content.setTranslateY(30);
        
        Timeline timeline = new Timeline(
            new KeyFrame(Duration.ZERO, 
                new KeyValue(content.opacityProperty(), 0),
                new KeyValue(content.translateYProperty(), 30)
            ),
            new KeyFrame(Duration.millis(600),
                new KeyValue(content.opacityProperty(), 1, Interpolator.EASE_OUT),
                new KeyValue(content.translateYProperty(), 0, Interpolator.EASE_OUT)
            )
        );
        timeline.play();
    }
    
    public void setOnAction(Consumer<HomeAction> handler) {
        this.onAction = handler;
    }
}
