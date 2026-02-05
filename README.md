verison abony 17 

### 1 JavaFX sous Linux

Selon la distribution :

- Na `openjfx` est installe dans `/usr/share/openjfx/lib`.
- Na vous avez un `javafx-sdk` quelque part (par exemple `~/javafx-sdk-21/lib`).
ref tsis de ts mettyyeeeeeee

Le script `compile.sh` et `play.sh` essaient plusieurs chemins possibles :

- `/usr/share/openjfx/lib`
- `/usr/lib/jvm/*/lib`
- `./lib/fx`
- `~/javafx-sdk*/lib`


 Scripts Bash du projet

Les scripts sont dans :

- `scripts/` : scripts generaux.
- `scripts/network/` : scripts reseau (UDP, decouverte de serveurs, etc.).

### 4.1 Roles principaux

- `scripts/network/broadcast_server.sh`
  - Lance un broadcast UDP regulier pour annoncer une partie sur le LAN.
  - Envoie des messages JSON avec : IP, port TCP, nom de la session, liste des joueurs.

- `scripts/network/listen_servers.sh`
  - Ecoute les annonces UDP et collecte la liste des serveurs decouverts.
  - Transmet les entrees au backend Java via `stdin` ou fichiers temporaires.

- `scripts/network/broadcast_localhost.sh`
  - Variante pour travailler uniquement en local (localhost), en ecrivant un fichier JSON dans `/tmp`.

- `scripts/network/listen_localhost.sh`
  - Lit le fichier JSON local et notifie l application Java des serveurs locaux disponibles.

- `scripts/network/get_local_ip.sh`
  - Utilise `ip route` et `grep` pour deduire l adresse IP principale de la machine.

- `scripts/network/send_tcp.sh` / `scripts/network/receive_tcp.sh`
  - Encapsulent l envoi / la reception de messages TCP sous forme JSON.

- `scripts/network/send_udp.sh` / `scripts/network/udp_server.sh`
  - Encapsulent l envoi / la reception UDP pour les evenements de jeu (tour, mots, timer, etc.).

Tous ces scripts sont lances par la couche Java (`NetworkBridge` + `BashExecutor`) et non directement par l utilisateur.

Pour des explications plus detaillees des commandes Bash utilisees, voir :

- `AboutBASH.md`

---

## 5. Architecture backend Java

Les packages principaux :

- `com.undercover.model`
- `com.undercover.controller`
- `com.undercover.network`
- `com.undercover.gui` (JavaFX)

Un resume des classes clefs est dans :

- `BackendJavaOverview.md`

### 5.1 Flux general

1. L utilisateur lance `./play.sh`.
2. `Main` initialise `App` (JavaFX) et `GameController`.
3. L ecran d accueil (Home) propose :
   - Creer une partie (host).
   - Rejoindre une partie (client).
4. `GameController` :
   - Cote host :
     - Cree une `GameSession`.
     - Lance un serveur TCP/UDP.
     - Lance un script de broadcast reseau.
   - Cote client :
     - Lance la decouverte de serveurs.
     - Affiche les serveurs trouves.
     - Envoie une requete de join au serveur choisi.
5. Une fois la partie commencee, le host pilote toutes les phases et diffuse l etat aux clients.


## 6. LALAN ny dje

Version simplifiee :

- Il y a des citoyens et un imposteur.
- Tous recoivent un mot, sauf l imposteur qui recoit un mot voisin mais different.
- A tour de role, chaque joueur dit un mot en rapport avec son mot secret.
- Le but des citoyens :
  - Devoiler l imposteur.
  - Garder le mot secret le plus flou possible.
- Le but de l imposteur :
  - Ne pas se faire decouvrir.
  - Essayer de deviner le mot des citoyens.

Phases :

1. **Phase des mots**
   - Chacun parle a son tour.
   - Timer pour chaque prise de parole.

2. **Debat**
   - Tout le monde discute librement.
   - Timer global.

3. **Vote**
   - Chaque joueur vote pour la personne qu il soupconne d etre l imposteur.

4. **Resultat**
   - Si l imposteur est majoritaire : les citoyens gagnent, sauf si l imposteur trouve le mot.
   - Sinon : l imposteur gagne.




## Jeu en LAN

Pour jouer en reseau local :

1. Choisir une machine qui sera le **host** (serveur).
2. Sur cette machine :
   - Lancer `./play.sh`.
   - Dans l ecran d accueil, choisir **Creer une partie**.
3. Sur les autres machines :
   - Lancer `./play.sh`.
   - Dans l ecran d accueil, choisir **Rejoindre une partie**.
   - Attendre la liste des serveurs detectes, puis cliquer sur celui du host.

Conseils :

## Verifier que le pare feu autorise les paquets UDP/TCP sur les ports utilises (configurable dans `config.properties`).
- Tester d abord en **localhost** (tout sur une seule machine) avant d etendre au LAN.

---

Pour approfondir le code, parcourez les sous-dossiers :

- `src/main/java/com/undercover/model`
- `src/main/java/com/undercover/controller`
- `src/main/java/com/undercover/network`
- `src/main/java/com/undercover/gui`

caveee