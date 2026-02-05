package com.undercover.gui;

import com.undercover.controller.GameController;
import com.undercover.gui.screens.*;
import com.undercover.model.*;
import com.undercover.network.NetworkBridge;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.util.List;

/**
 * Application principale UNDERCOVER avec interface moderne
 */
public class App extends Application implements GameController.GameEventListener {
    
    private Stage primaryStage;
    private StackPane rootPane;
    private GameController controller;
    
    // Écrans
    private HomeScreen homeScreen;
    private CreateServerScreen createServerScreen;
    private JoinServerScreen joinServerScreen;
    private LobbyScreen lobbyScreen;
    private GameScreen gameScreen;
    
    public static void main(String[] args) {
        launch(args);
    }
    
    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        this.controller = new GameController();
        this.controller.addListener(this);
        
        // Container racine
        rootPane = new StackPane();
        
        // Appliquer le thème
        Scene scene = new Scene(rootPane, 1200, 800);
        String css = ModernTheme.getGlobalCSS().replace("\n", " ").replace("\"", "'");
        scene.getStylesheets().add("data:text/css," + css.replace(" ", "%20"));
        
        primaryStage.setTitle("UNDERCOVER - Jeu Multijoueur LAN");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1000);
        primaryStage.setMinHeight(700);
        
        primaryStage.setOnCloseRequest(e -> shutdown());
        
        // Afficher l'écran d'accueil
        showHomeScreen();
        
        primaryStage.show();
    }
    
    // ===== NAVIGATION =====
    
    private void showHomeScreen() {
        homeScreen = new HomeScreen();
        homeScreen.setOnAction(action -> {
            switch (action) {
                case CREATE_SERVER -> showCreateServerScreen();
                case JOIN_SERVER -> showJoinServerScreen();
            }
        });
        setScreen(homeScreen);
    }
    
    private void showCreateServerScreen() {
        createServerScreen = new CreateServerScreen();
        createServerScreen.setOnBack(this::showHomeScreen);
        createServerScreen.setOnCreateServer(config -> {
            controller.createServer(
                config.playerName(),
                config.sessionName(),
                config.port(),
                config.maxPlayers(),
                config.localhostMode()
            );
        });
        setScreen(createServerScreen);
    }
    
    private void showJoinServerScreen() {
        joinServerScreen = new JoinServerScreen();
        joinServerScreen.setOnBack(this::showHomeScreen);
        joinServerScreen.setOnJoinServer((playerName, server) -> {
            controller.joinServer(playerName, server);
        });
        
        // Callback pour rafraîchir quand l'option localhost change
        joinServerScreen.setOnRefreshRequest(() -> {
            controller.startServerDiscovery(joinServerScreen.isIncludeLocalhost());
        });
        
        // Démarrer la découverte de serveurs (avec localhost par défaut)
        controller.startServerDiscovery(joinServerScreen.isIncludeLocalhost());
        
        setScreen(joinServerScreen);
    }
    
    private void showLobbyScreen(boolean isHost, String sessionName, String address) {
        lobbyScreen = new LobbyScreen(isHost, sessionName, address);
        lobbyScreen.setOnLeave(this::handleLeave);
        lobbyScreen.setOnStartGame(() -> controller.startGame());
        
        // Mettre à jour avec les joueurs actuels
        if (controller.getSession() != null) {
            lobbyScreen.updatePlayers(
                controller.getSession().getPlayers(),
                controller.getSession().getMaxPlayers()
            );
        }
        
        setScreen(lobbyScreen);
    }
    
    private void showGameScreen(Role role, String word) {
        gameScreen = new GameScreen(controller.getLocalPlayer(), word, role);
        
        gameScreen.setOnSendChat(controller::sendChat);
        gameScreen.setOnSpeakWord(controller::speakWord);
        gameScreen.setOnVote(controller::vote);
        gameScreen.setOnGuess(controller::guessWord);
        gameScreen.setOnLeave(this::handleLeave);
        
        // Mettre à jour la liste des joueurs
        if (controller.getSession() != null) {
            gameScreen.updatePlayers(controller.getSession().getPlayers());
        }
        
        setScreen(gameScreen);
    }
    
    private void setScreen(javafx.scene.Node screen) {
        Platform.runLater(() -> {
            rootPane.getChildren().clear();
            rootPane.getChildren().add(screen);
        });
    }
    
    private void handleLeave() {
        controller.shutdown();
        controller = new GameController();
        controller.addListener(this);
        showHomeScreen();
    }
    
    private void shutdown() {
        if (controller != null) {
            controller.shutdown();
        }
        Platform.exit();
    }
    
    // ===== GAME EVENT LISTENER =====
    
    @Override
    public void onPlayersUpdated(List<Player> players) {
        Platform.runLater(() -> {
            if (lobbyScreen != null && controller.getSession() != null) {
                lobbyScreen.updatePlayers(players, controller.getSession().getMaxPlayers());
            }
            if (gameScreen != null) {
                gameScreen.updatePlayers(players);
            }
        });
    }
    
    @Override
    public void onGameStarted(Role role, String word) {
        Platform.runLater(() -> showGameScreen(role, word));
    }
    
    @Override
    public void onPhaseChanged(GameSession.State state) {
        Platform.runLater(() -> {
            if (gameScreen != null) {
                int seconds = switch (state) {
                    case DEBATE -> controller.getSession() != null ? 
                        controller.getSession().getDebateTimeSeconds() : 90;
                    case VOTING -> controller.getSession() != null ? 
                        controller.getSession().getVoteTimeSeconds() : 30;
                    default -> 60;
                };
                gameScreen.updatePhase(state, seconds);
            }
        });
    }
    
    @Override
    public void onMessageReceived(GameMessage message) {
        Platform.runLater(() -> {
            if (gameScreen != null) {
                gameScreen.addMessage(message);
            }
        });
    }
    
    @Override
    public void onGameEnded(String message) {
        Platform.runLater(() -> {
            if (gameScreen != null) {
                gameScreen.showVictory(message);
            }
        });
    }
    
    @Override
    public void onServerDiscovered(List<NetworkBridge.ServerInfo> servers) {
        Platform.runLater(() -> {
            if (joinServerScreen != null) {
                joinServerScreen.updateServerList(servers);
            }
        });
    }
    
    @Override
    public void onConnectionStatusChanged(boolean connected, String message) {
        System.out.println("=== onConnectionStatusChanged ===");
        System.out.println("connected: " + connected);
        System.out.println("message: " + message);
        System.out.println("controller.isHost(): " + controller.isHost());
        System.out.println("controller.getSession(): " + (controller.getSession() != null ? "OK" : "NULL"));
        if (controller.getSession() != null) {
            System.out.println("session.getPlayers().size(): " + controller.getSession().getPlayers().size());
        }
        
        Platform.runLater(() -> {
            if (connected && controller.getSession() != null) {
                // Aller au lobby
                System.out.println("=== Affichage du Lobby ===");
                showLobbyScreen(
                    controller.isHost(),
                    controller.getSession().getName(),
                    controller.getSession().getHostIp() + ":" + controller.getSession().getHostPort()
                );
            } else if (!connected) {
                // Afficher une erreur
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR
                );
                alert.setTitle("Erreur de connexion");
                alert.setContentText(message);
                alert.showAndWait();
            }
        });
    }
    
    @Override
    public void onTurnChanged(String currentPlayerId, int remainingSeconds, java.util.List<String> turnOrder) {
        Platform.runLater(() -> {
            if (gameScreen != null) {
                gameScreen.updateCurrentTurn(currentPlayerId, remainingSeconds, turnOrder);
            }
        });
    }
    
    @Override
    public void onTimerSync(int remainingSeconds) {
        Platform.runLater(() -> {
            if (gameScreen != null) {
                gameScreen.syncTimer(remainingSeconds);
            }
        });
    }
}
