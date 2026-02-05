package com.undercover.model;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Session de jeu - Contient toute la logique du jeu Undercover
 * Regles:
 * - 2 roles: CITIZEN et IMPOSTOR
 * - L'imposteur ne recoit PAS de mot, il doit deviner le mot des citoyens
 * - Tour par tour: chaque joueur a 40s pour parler
 * - Apres une ronde: debat (1m30) puis vote
 * - Pas de chat pendant la phase WORD_PHASE
 */
public class GameSession implements Serializable {
    private static final long serialVersionUID = 3L;
    
    public enum State {
        LOBBY("En attente", "[~]"),
        WORD_PHASE("Tour de parole", "[W]"),
        DEBATE("Discussion", "[D]"),
        VOTING("Vote", "[V]"),
        RESULT("Resultat", "[R]"),
        FINISHED("Termine", "[F]");
        
        private final String display;
        private final String icon;
        State(String display, String icon) { 
            this.display = display; 
            this.icon = icon;
        }
        public String getDisplay() { return icon + " " + display; }
    }
    
    private final String id;
    private final String name;
    private final String hostIp;
    private final int hostPort;
    private final int maxPlayers;
    
    private final List<Player> players;
    private final List<GameMessage> messages;
    private final Map<String, Integer> votes;
    
    // Ordre de passage des joueurs (melange a chaque ronde)
    private List<String> turnOrder;
    
    private State state;
    private String secretWord;
    private int currentTurnIndex;  // Index dans turnOrder
    private int round;
    private String winnerId;
    private String winMessage;
    
    // Parametres de jeu (en secondes)
    public static final int WORD_TIME_SECONDS = 40;      // 40s par joueur pour donner un mot
    public static final int DEBATE_TIME_SECONDS = 90;    // 1m30 de debat
    public static final int VOTE_TIME_SECONDS = 30;      // 30s pour voter
    
    public GameSession(String name, String hostIp, int hostPort, int maxPlayers) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.name = name;
        this.hostIp = hostIp;
        this.hostPort = hostPort;
        this.maxPlayers = maxPlayers;
        this.players = new CopyOnWriteArrayList<>();
        this.messages = new CopyOnWriteArrayList<>();
        this.votes = new HashMap<>();
        this.turnOrder = new ArrayList<>();
        this.state = State.LOBBY;
        this.round = 0;
    }
    
    // ===== GESTION DES JOUEURS =====
    
    public boolean addPlayer(Player player) {
        if (players.size() >= maxPlayers) return false;
        if (players.stream().anyMatch(p -> p.getId().equals(player.getId()))) return false;
        
        players.add(player);
        addMessage(new GameMessage(player.getId(), player.getName(), "", GameMessage.Type.JOIN));
        return true;
    }
    
    public void removePlayer(String playerId) {
        players.stream()
            .filter(p -> p.getId().equals(playerId))
            .findFirst()
            .ifPresent(p -> {
                addMessage(new GameMessage(p.getId(), p.getName(), "", GameMessage.Type.LEAVE));
                players.remove(p);
                turnOrder.remove(playerId);
            });
    }
    
    public Player getPlayer(String playerId) {
        return players.stream()
            .filter(p -> p.getId().equals(playerId))
            .findFirst()
            .orElse(null);
    }
    
    public List<Player> getAlivePlayers() {
        return players.stream().filter(Player::isAlive).toList();
    }
    
    // ===== LOGIQUE DE JEU =====
    
    // Minimum de joueurs pour lancer (2 pour tests, 3 en production)
    private static final int MIN_PLAYERS_TO_START = 2;
    
    /**
     * Demarre le jeu
     */
    public void startGame(String secret) {
        if (players.size() < MIN_PLAYERS_TO_START) return;
        
        this.secretWord = secret;
        this.round = 1;
        
        distributeRoles();
        initializeTurnOrder();
        state = State.WORD_PHASE;
        currentTurnIndex = 0;
        
        addMessage(GameMessage.system("La partie commence ! Round " + round));
        addMessage(GameMessage.system("Chaque joueur a " + WORD_TIME_SECONDS + "s pour donner un mot."));
    }
    
    public void startGame(String secret, String unused) {
        startGame(secret);
    }
    
    private void distributeRoles() {
        int playerCount = players.size();
        int impostorCount = playerCount < 6 ? 1 : (playerCount < 10 ? 2 : 3);
        
        List<Player> shuffled = new ArrayList<>(players);
        Collections.shuffle(shuffled);
        
        for (int i = 0; i < players.size(); i++) {
            Player p = shuffled.get(i);
            p.setRole(i < impostorCount ? Role.IMPOSTOR : Role.CITIZEN);
            p.setAlive(true);
            p.resetForNewRound();
        }
    }
    
    /**
     * Initialise/reinitialise l'ordre de passage (melange aleatoire)
     */
    private void initializeTurnOrder() {
        turnOrder = new ArrayList<>();
        // IMPORTANT: Creer une copie mutable car getAlivePlayers() retourne une liste immuable
        List<Player> alive = new ArrayList<>(getAlivePlayers());
        Collections.shuffle(alive);
        for (Player p : alive) {
            turnOrder.add(p.getId());
        }
    }
    
    /**
     * Retourne le mot pour un joueur
     */
    public String getWordForPlayer(Player player) {
        if (player.getRole() == Role.IMPOSTOR) {
            return null;
        }
        return secretWord;
    }
    
    /**
     * Retourne le joueur dont c'est le tour de parler
     */
    public Player getCurrentSpeaker() {
        if (turnOrder.isEmpty() || currentTurnIndex >= turnOrder.size()) {
            return null;
        }
        String playerId = turnOrder.get(currentTurnIndex);
        return getPlayer(playerId);
    }
    
    /**
     * Retourne l'ID du joueur actuel
     */
    public String getCurrentSpeakerId() {
        if (turnOrder.isEmpty() || currentTurnIndex >= turnOrder.size()) {
            return null;
        }
        return turnOrder.get(currentTurnIndex);
    }
    
    /**
     * Verifie si c'est le tour du joueur specifie
     */
    public boolean isPlayerTurn(String playerId) {
        String currentId = getCurrentSpeakerId();
        return currentId != null && currentId.equals(playerId);
    }
    
    /**
     * Un joueur donne son mot (seulement si c'est son tour)
     */
    public boolean speakWord(String playerId, String word) {
        if (state != State.WORD_PHASE) return false;
        if (!isPlayerTurn(playerId)) return false;
        
        Player current = getPlayer(playerId);
        if (current == null || !current.isAlive()) return false;
        if (current.getSpokenWord() != null) return false;  // Deja parle cette ronde
        
        current.setSpokenWord(word);
        addMessage(GameMessage.word(current, word));
        
        return true;
    }
    
    /**
     * Passe au joueur suivant (appele apres qu'un joueur parle ou timeout)
     * Retourne true si la ronde est terminee
     */
    public boolean nextTurn() {
        currentTurnIndex++;
        
        // Verifier si la ronde est terminee
        if (currentTurnIndex >= turnOrder.size()) {
            return true;  // Ronde terminee
        }
        return false;
    }
    
    /**
     * Verifie si tous les joueurs ont parle cette ronde
     */
    public boolean hasEveryoneSpoken() {
        return currentTurnIndex >= turnOrder.size();
    }
    
    /**
     * Demarre la phase de debat
     */
    public void startDebate() {
        state = State.DEBATE;
        addMessage(GameMessage.system("Phase de discussion ! " + DEBATE_TIME_SECONDS + " secondes."));
    }
    
    /**
     * Demarre la phase de vote
     */
    public void startVoting() {
        state = State.VOTING;
        votes.clear();
        getAlivePlayers().forEach(p -> p.setHasVoted(false));
        addMessage(GameMessage.system("Phase de vote ! " + VOTE_TIME_SECONDS + " secondes."));
    }
    
    /**
     * Un joueur vote contre un autre
     */
    public boolean vote(String voterId, String targetId) {
        Player voter = getPlayer(voterId);
        Player target = getPlayer(targetId);
        
        if (voter == null || target == null || !voter.isAlive() || !target.isAlive()) return false;
        if (voter.hasVoted()) return false;
        
        voter.setHasVoted(true);
        voter.setVotedFor(targetId);
        votes.merge(targetId, 1, Integer::sum);
        
        addMessage(GameMessage.vote(voter, target));
        
        return true;
    }
    
    /**
     * Verifie si tous les joueurs vivants ont vote
     */
    public boolean hasEveryoneVoted() {
        return getAlivePlayers().stream().allMatch(Player::hasVoted);
    }
    
    /**
     * Resout les votes et elimine un joueur
     * Retourne le joueur elimine ou null si egalite
     */
    public Player resolveVotes() {
        state = State.RESULT;
        
        if (votes.isEmpty()) {
            addMessage(GameMessage.system("Personne n'a vote. Aucune elimination."));
            return null;
        }
        
        // Trouver le max de votes
        int maxVotes = votes.values().stream().max(Integer::compare).orElse(0);
        
        // Compter combien de joueurs ont ce nombre de votes
        List<String> topVoted = votes.entrySet().stream()
            .filter(e -> e.getValue() == maxVotes)
            .map(Map.Entry::getKey)
            .toList();
        
        // Egalite = pas d'elimination
        if (topVoted.size() > 1) {
            addMessage(GameMessage.system("Egalite dans les votes ! Personne n'est elimine."));
            return null;
        }
        
        // Eliminer le joueur
        String eliminatedId = topVoted.get(0);
        Player eliminated = getPlayer(eliminatedId);
        if (eliminated != null) {
            eliminated.setAlive(false);
            addMessage(GameMessage.elimination(eliminated));
            return eliminated;
        }
        
        return null;
    }
    
    /**
     * Verifie les conditions de victoire
     * Retourne true si le jeu est termine
     */
    public boolean checkWinCondition() {
        long aliveImpostors = getAlivePlayers().stream()
            .filter(p -> p.getRole() == Role.IMPOSTOR)
            .count();
        long aliveCitizens = getAlivePlayers().stream()
            .filter(p -> p.getRole() == Role.CITIZEN)
            .count();
        
        if (aliveImpostors == 0) {
            state = State.FINISHED;
            winMessage = "Victoire des Citoyens ! Tous les imposteurs ont ete elimines !";
            addMessage(GameMessage.victory("Citoyens", winMessage));
            return true;
        } else if (aliveImpostors >= aliveCitizens) {
            state = State.FINISHED;
            winMessage = "Victoire des Imposteurs ! Ils sont maintenant majoritaires !";
            addMessage(GameMessage.victory("Imposteurs", winMessage));
            return true;
        }
        
        return false;
    }
    
    /**
     * Demarre une nouvelle ronde (apres le vote)
     */
    public void newRound() {
        round++;
        currentTurnIndex = 0;
        
        // Reinitialiser les joueurs pour la nouvelle ronde
        getAlivePlayers().forEach(Player::resetForNewRound);
        
        // Nouvel ordre de passage aleatoire
        initializeTurnOrder();
        
        state = State.WORD_PHASE;
        addMessage(GameMessage.system("Round " + round + " - Nouvel ordre de passage !"));
    }
    
    /**
     * L'imposteur tente de deviner le mot secret
     * Si correct: victoire immediate de l'imposteur, partie terminee
     * Si incorrect: l'imposteur est elimine et le message est affiche dans le chat
     */
    public boolean guessWord(String playerId, String guess) {
        Player player = getPlayer(playerId);
        if (player == null || player.getRole() != Role.IMPOSTOR || !player.isAlive()) return false;
        
        addMessage(new GameMessage(playerId, player.getName(), guess, GameMessage.Type.GUESS));
        
        if (guess.equalsIgnoreCase(secretWord)) {
            // VICTOIRE IMMEDIATE DE L'IMPOSTEUR
            state = State.FINISHED;
            winnerId = playerId;
            winMessage = "🎯 VICTOIRE DE L'IMPOSTEUR ! " + player.getName() + " a trouvé le mot secret: \"" + secretWord + "\" !";
            addMessage(GameMessage.victory("Imposteur", winMessage));
            return true;
        } else {
            // ECHEC - L'imposteur est elimine
            player.setAlive(false);
            String failMessage = "❌ MAUVAISE REPONSE ! " + player.getName() + " a propose \"" + guess + "\" mais le mot correct etait \"" + secretWord + "\". Il est elimine !";
            addMessage(GameMessage.system(failMessage));
            checkWinCondition();
        }
        
        return false;
    }
    
    // ===== MESSAGES =====
    
    public void addMessage(GameMessage message) {
        messages.add(message);
        while (messages.size() > 100) {
            messages.remove(0);
        }
    }
    
    public boolean addChatMessage(String playerId, String content) {
        if (state != State.DEBATE && state != State.VOTING) {
            return false;
        }
        
        Player player = getPlayer(playerId);
        if (player != null) {
            addMessage(new GameMessage(playerId, player.getName(), content, GameMessage.Type.CHAT));
            return true;
        }
        return false;
    }
    
    public boolean isChatAllowed() {
        return state == State.DEBATE || state == State.VOTING;
    }
    
    // ===== GETTERS =====
    
    public String getId() { return id; }
    public String getName() { return name; }
    public String getHostIp() { return hostIp; }
    public int getHostPort() { return hostPort; }
    public int getMaxPlayers() { return maxPlayers; }
    public List<Player> getPlayers() { return new ArrayList<>(players); }
    public List<GameMessage> getMessages() { return new ArrayList<>(messages); }
    public State getState() { return state; }
    public String getSecretWord() { return secretWord; }
    public int getRound() { return round; }
    public String getWinMessage() { return winMessage; }
    public boolean isFull() { return players.size() >= maxPlayers; }
    public int getCurrentTurnIndex() { return currentTurnIndex; }
    public List<String> getTurnOrder() { return new ArrayList<>(turnOrder); }
    
    // Constantes de temps (en secondes)
    public int getWordTimeSeconds() { return WORD_TIME_SECONDS; }
    public int getDebateTimeSeconds() { return DEBATE_TIME_SECONDS; }
    public int getVoteTimeSeconds() { return VOTE_TIME_SECONDS; }
    
    public void setState(State state) { this.state = state; }
    public void setCurrentTurnIndex(int index) { this.currentTurnIndex = index; }
    
    /**
     * Met a jour la liste des joueurs (pour les clients qui recoivent la liste du serveur)
     */
    public void setPlayers(List<Player> newPlayers) {
        this.players.clear();
        this.players.addAll(newPlayers);
    }
}