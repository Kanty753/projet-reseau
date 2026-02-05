# Backend Java - Vue d ensemble

Ce document resume les classes et methodes Java les plus importantes du projet Undercover LAN Game.

## 1. Controleur principal

### 1.1 `com.undercover.controller.GameController`

Responsable de :

- Creation / gestion de la session (`GameSession`).
- Gestion du joueur local (`localPlayer`).
- Communication reseau (TCP/UDP) via `NetworkBridge`.
- Diffusion des evenements vers l UI via `GameEventListener`.

Methodes clefs :

- `createSession(sessionName, maxPlayers, localhostMode)`
- `joinSession(serverInfo, playerName)`
- `startGame()`
- `speakWord(word)`
- `sendChat(message)`
- `vote(targetPlayerId)`
- `guessWord(guess)`

Handlers reseau importants :

- `handleJoinRequest(JsonObject msg)`
- `handleJoinAccepted(JsonObject msg)`
- `handlePlayerList(JsonObject msg)`
- `handleGameStart(JsonObject msg)`
- `handlePhaseChange(JsonObject msg)`
- `handleWordSpoken(JsonObject msg)`
- `handleTurnStart(JsonObject msg)`
- `handleTimerSync(JsonObject msg)`
- `handleTurnTimeout(JsonObject msg)`

## 2. Modele de jeu

### 2.1 `com.undercover.model.GameSession`

Stocke tout l etat de la partie :

- Liste des joueurs (`players`).
- Etat courant (`state` : WORD_PHASE, DEBATE, VOTING, RESULT, FINISHED).
- Ordre des tours (`turnOrder`).
- Index du tour courant (`currentTurnIndex`).
- Parametres de temps (WORD_TIME_SECONDS, DEBATE_TIME_SECONDS, VOTE_TIME_SECONDS).

Methodes clefs :

- `addPlayer(Player)`
- `startGame(citizenWord, impostorWord)`
- `startWordPhase()` / `startDebate()` / `startVoting()`
- `speakWord(playerId, word)`
- `vote(voterId, targetId)`
- `resolveVotes()`
- `checkWinCondition()`
- `newRound()`
- `isPlayerTurn(playerId)`
- `getCurrentSpeakerId()`

### 2.2 `com.undercover.model.Player`

Represente un joueur :

- `id`, `name`, `ipAddress`, `port`, `udpPort`
- `role` (CITIZEN / IMPOSTOR)
- `alive`, `hasVoted`, `votedFor`

Methodes utilies :

- Getters simples (`getId()`, `getName()`, ...).
- `resetForNewRound()` : efface vote et mot prononce.

## 3. Couche reseau

### 3.1 `com.undercover.network.NetworkBridge`

Fait le lien entre :

- Scripts Bash (decouverte / broadcast des serveurs).
- Sockets TCP/UDP Java pour les messages de jeu.

Methodes importantes et utilite :

- `startServerBroadcast(serverIp, serverPort, sessionName, maxPlayers, currentPlayers, playerNames)`
  - Lance un script Bash de broadcast.
  - En mode LAN : envoie regulierement en UDP les infos de la partie (IP, port TCP, nom, nombre de joueurs, pseudos).
  - En mode localhost : ecrit les memes infos dans un fichier partage pour plusieurs instances locales.

- `stopServerBroadcast()`
  - Arrete le process de broadcast lance precedemment.
  - Utilise quand le host ferme le lobby ou quitte la partie.

- `discoverServers(timeoutSeconds)`
  - Lance un script d ecoute (LAN ou localhost selon le mode).
  - Attend pendant `timeoutSeconds` des annonces de serveurs.
  - Retourne une liste de `ServerInfo` construite a partir du JSON recu.

- `discoverAllServers(timeoutSeconds)`
  - Combine deux sources : localhost et LAN.
  - Permet de detecter a la fois les parties locales (fichier) et les parties en broadcast UDP.

- `startServerDiscovery(onServersFound, includeLocalhost)`
  - Planifie regulierement des appels a `discoverServers` ou `discoverAllServers`.
  - A chaque resultat, appelle le callback `onServersFound` pour mettre a jour l ecran JoinServer.

- `startTcpServer(port, handler)`
  - Demarre un serveur TCP Java natif sur `port`.
  - Accepte les connexions des clients (join, messages importants).
  - Pour chaque message JSON recu, renvoie une reponse de confirmation et delegue au `handler`.

- `sendMessage(targetIp, targetPort, json)`
  - Ouvre une connexion TCP vers un host.
  - Envoie le JSON avec une longueur prefixee, attend une reponse.
  - Retourne la reponse parsee en `JsonObject` dans un `CompletableFuture`.

- `startUdpServer(port, handler)`
  - Demarre un listener UDP pour les messages de jeu temps reel.
  - Chaque message recu est parse en JSON et transmis au `handler`.

- `sendUdpMessage(targetIp, targetPort, json)`
  - Envoie un message JSON en UDP (fire-and-forget).
  - Utilise pour les evenements frequents ou la perte de quelques paquets est acceptable (tour, timer, etc.).

- `sendUdpMessageWithResponse(targetIp, targetPort, json, timeoutMs)`
  - Variante UDP qui attend une reponse dans un delai court.
  - Utile pour des confirmations critiques tout en restant en UDP.

- `sendUdpResponse(targetIp, targetPort, response)`
  - Methode utilitaire pour renvoyer une reponse a partir des infos `_senderIp` / `_senderPort` d un message recu.

- `stopUdpServer()` / `stopTcpServer()` / `shutdown()`
  - Libere proprement les sockets, threads et processus Bash.
  - Appele quand l application se ferme ou change de role (host -> client, etc.).

### 3.2 `com.undercover.network.BashExecutor`

Petit utilitaire pour lancer des scripts :

- `executeAsync(script, args...)`
  - Lance un script Bash et collecte toute sa sortie standard.
  - Retourne un `CompletableFuture<String>` avec le texte de sortie (par exemple la liste JSON des serveurs).

- `startBackground(script, callback, args...)`
  - Lance un script Bash en arriere plan (process long ou repetitif).
  - A chaque ligne de sortie, appelle le `callback` fourni.
  - Retourne un identifiant de process pour pouvoir l arreter plus tard.

- `stopBackground(processId)`
  - Termine un script Bash lance avec `startBackground`.

- `getLocalIp()`
  - Appelle le script de detection d IP locale.
  - Utilise typiquement pour le host afin de connaitre son adresse sur le LAN.

## 4. Interface graphique (JavaFX)

### 4.1 `com.undercover.gui.App`

- Classe JavaFX principale.
- Gere les transitions entre ecrans :
  - Home
  - CreateServerScreen
  - JoinServerScreen
  - LobbyScreen
  - GameScreen
- Ecoute les evenements de `GameController` et met a jour l UI.

### 4.2 `com.undercover.gui.screens.GameScreen`

Affichage principal de la partie :

- Cartes des joueurs (nom, role deduit, statut vivant/mort, badge de tour de parole).
- Champ pour dire un mot.
- Chat.
- Zone d action (parler, voter, deviner).

Methodes clefs :

- `updatePlayers(List<Player> players)`
- `updateCurrentTurn(String currentPlayerId, int remainingSeconds, List<String> turnOrder)`
- `updatePhase(GameSession.State state, int seconds)`
- `addMessage(GameMessage message)`
- `recordSpokenWord(String playerId, String word)`

## 5. Evenements UI

`GameEventListener` (interface interne a GameController) expose :

- `onPlayersUpdated(List<Player> players)`
- `onGameStarted(Role role, String word)`
- `onPhaseChanged(GameSession.State state)`
- `onMessageReceived(GameMessage message)`
- `onGameEnded(String message)`
- `onTurnChanged(String currentPlayerId, int remainingSeconds, List<String> turnOrder)`
- `onTimerSync(int remainingSeconds)`

L implementation dans `App` transfere ces evenements vers l ecran courant (Lobby, Game, etc.).
