# About Bash in Undercover

Ce document explique, pour des debutants en administration systeme, les commandes Bash utilisees dans les scripts `.sh` du projet.

## 1. Commandes de base

### 1.1 `#!/bin/bash`

C est la **shebang line**. Elle indique au systeme que ce fichier doit etre execute avec l interpreteur `bash`.

### 1.2 `set -e`

- `set -e` dit a Bash : "si une commande retourne une erreur (code different de 0), arrete tout le script".
- Utile pour eviter de continuer avec un etat incoherent.

### 1.3 Variables d environnement

- `PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"`
  - `"$0"` : chemin du script en cours.
  - `dirname "$0"` : dossier du script.
  - `cd ... && pwd` : se place dans ce dossier et affiche le chemin complet.
  - `$( ... )` : substitution de commande, remplace l expression par son resultat.

### 1.4 `cd`

- `cd "$PROJECT_DIR"` : change le repertoire courant pour celui du projet.
- Toujours faire `cd` en debut de script pour etre sur que les chemins relatifs (`src/...`) pointent au bon endroit.

### 1.5 `echo`

- `echo` affiche du texte.
- Exemple : `echo "Compilation en cours"`.
- Avec `-e`, `echo -e` permet d interpreter des sequences comme `\n` (nouvelle ligne) ou des couleurs ANSI.

---

## 2. Gestion des fichiers et dossiers

### 2.1 `mkdir -p`

- `mkdir -p lib/gson` : cree le dossier `lib/gson` et ne provoque pas d erreur si le dossier existe deja.

### 2.2 `rm -rf`

- `rm -rf target/classes` : supprime recursivement un dossier sans demander de confirmation.
- `-r` : recursif.
- `-f` : force (ne se plaint pas si le fichier/dossier n existe pas).

### 2.3 `chmod +x`

- `chmod +x scripts/network/*.sh` : rend les fichiers executables.
- Necessaire pour pouvoir les lancer avec `./script.sh`.

### 2.4 `find`

- `find "$PROJECT_DIR/src/main/java" -name "*.java"` : liste tous les fichiers `.java` dans l arborescence.
- Utilise pour donner a `javac` la liste complete des fichiers a compiler.

---

## 3. Commandes reseau et systeme

### 3.1 `ip route` et `grep`

Dans `broadcast_server.sh` :

- `ip route` : affiche les routes reseau de la machine.
- `grep -oP 'src \\K[\d.]+'` :
  - `grep` filtre les lignes.
  - `-o` : affiche uniquement la partie qui matche.
  - `-P` : utilise les regex Perl.
  - `\\K` : ignore ce qui est avant, ne garde que ce qui suit.
- Cette combinaison sert a recuperer l adresse IP locale.

### 3.2 `nc` (netcat) ou `socat` (selon implementation)

Suivant la version de scripts, on peut utiliser :

- `nc -u` : pour envoyer / recevoir des paquets UDP.
- `socat` : outil plus avance pour relier des flux (TCP, UDP, fichiers...).

L idee :

- Ecouter sur un port UDP : `nc -lu 5555` ou `socat -u UDP-RECV:5555 STDOUT`.
- Envoyer un paquet : `echo "texte" | nc -u 192.168.0.10 5555`.

Dans le projet, ces commandes sont encapsulees dans `send_udp.sh` et `udp_server.sh`.

---

## 4. Manipulation de JSON en Bash

Les scripts ne font pas de vrai parsing JSON avance (c est trop complique en pur Bash). Ils utilisent des astuces simples :

- Lorsqu un script doit **produire** du JSON, il fait juste un `printf` avec les champs connus.
- Exemple dans `broadcast_localhost.sh` :

```bash
printf '{"type":"SERVER_ANNOUNCE","ip":"127.0.0.1","port":%d,"name":"%s"}' \\
       "$SERVER_PORT" "$SESSION_NAME"
```

- Pour **relire** les entrees, les scripts se reposent principalement sur Java qui parse le JSON avec GSON.

---

## 5. Redirection et pipes

### 5.1 Redirection

- `> fichier` : ecrit la sortie standard dans un fichier (ecrase).
- `>> fichier` : ajoute a la fin du fichier.
- `2>/dev/null` : envoie la sortie d erreur vers /dev/null (la jette).

Exemples :

- `ls *.jar &>/dev/null` : teste s il y a des fichiers `.jar` sans afficher les messages.

### 5.2 Pipes `|`

- `commande1 | commande2` : la sortie de `commande1` devient l entree de `commande2`.
- Exemple : `ip route | grep -oP 'src \\K[\d.]+'`.

---

## 6. Substitutions et guillemets

### 6.1 Substitution de commande `$(...)`

- Permet d injecter le resultat d une commande dans une variable.
- Exemple : `NOW="$(date +%s)"`.

### 6.2 Guillemets simples et doubles

- `'texte'` : tout est pris au pied de la lettre (pas de variables, pas d echappement special).
- `"texte"` : les variables `$VAR` et les sequences comme `\n` sont interpretees.

Dans les scripts, on prefere `"..."` pour inclure des variables et proteger les espaces.

---

## 7. Bonne pratiques vues dans le projet

- Toujours se placer dans le dossier du projet avec `PROJECT_DIR`.
- Toujours verifier la presence de Java / JavaFX avant de lancer la compilation.
- Toujours proteger les variables avec des guillemets (`"$VAR"`).
- Utiliser `set -e` pour eviter les etats partiels.
- Commenter le haut de chaque script (but, usage, parametres).
