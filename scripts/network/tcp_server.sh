#!/bin/bash
# =============================================================================
# TCP SERVER - Serveur TCP pour recevoir les messages (connexions fiables)
# =============================================================================
# Ce script ecoute en continu sur un port TCP.
# Chaque message recu est affiche sur stdout au format JSON, lu par Java.
# Le protocole utilise est : longueur (4 octets big-endian) + message JSON.
# Utilise pour les connexions initiales (JOIN) et les messages critiques.
# =============================================================================
# Usage : tcp_server.sh <port>
# =============================================================================

# -----------------------------------------------------------------------------
# ARGUMENT
# -----------------------------------------------------------------------------
LISTEN_PORT="$1"

if [ -z "$LISTEN_PORT" ]; then
    echo "Usage: tcp_server.sh <port>"
    exit 1
fi

# -----------------------------------------------------------------------------
# FONCTION DE NETTOYAGE
# -----------------------------------------------------------------------------
# Quand le script se termine, on tue tous les processus enfants.
# pkill -P $$ : tue tous les processus dont le parent est le PID courant ($$)
# -----------------------------------------------------------------------------
cleanup() {
    pkill -P $$ 2>/dev/null
    exit 0
}

trap cleanup SIGTERM SIGINT EXIT

# -----------------------------------------------------------------------------
# BOUCLE D ECOUTE TCP
# -----------------------------------------------------------------------------
# On utilise socat pour ecouter en TCP.
# Pour chaque connexion entrante, socat execute un sous-processus
# qui lit le message et le retransmet sur stdout.
#
# Protocole :
#   - Le client envoie d abord 4 octets (big-endian) = taille du message
#   - Puis le message JSON en UTF-8
#   - Le serveur repond de la meme facon : 4 octets taille + JSON reponse
#
# Comme socat ne gere pas nativement le framing par longueur,
# on utilise un script intermediaire handle_tcp_client.sh.
# 
# Alternative simplifiee : on utilise un delimiteur newline (\n)
# car nos messages JSON sont sur une seule ligne.
# C est plus simple et compatible avec les outils UNIX.
# -----------------------------------------------------------------------------

# On va utiliser socat en mode fork : chaque connexion est geree
# dans un sous-processus. SYSTEM execute une commande shell.
# Le message est lu depuis stdin et ecrit sur stdout.

if command -v socat &>/dev/null; then
    # -----------------------------------------------------------------
    # SOCAT - Mode serveur TCP avec fork
    # -----------------------------------------------------------------
    # TCP-LISTEN:port,reuseaddr,fork : ecouter en TCP, reutiliser le port,
    #   et creer un fork pour chaque client (accepter plusieurs connexions)
    # EXEC: execute un script shell pour chaque connexion
    # -----------------------------------------------------------------
    socat TCP-LISTEN:$LISTEN_PORT,reuseaddr,fork EXEC:"/bin/bash $(dirname "$0")/handle_tcp_client.sh" 2>/dev/null &
    SOCAT_PID=$!

    # Attendre que socat se termine
    wait $SOCAT_PID
else
    # -----------------------------------------------------------------
    # NETCAT (fallback) - Mode serveur TCP basique
    # -----------------------------------------------------------------
    # nc ne supporte pas le fork nativement, on boucle.
    # Chaque connexion est traitee l une apres l autre.
    # -----------------------------------------------------------------
    while true; do
        # nc -l -p PORT : ecouter une connexion TCP sur PORT
        # La connexion est fermee apres chaque client.
        nc -l -p "$LISTEN_PORT" -q 1 2>/dev/null | while IFS= read -r line; do
            [ -n "$line" ] && echo "$line"
        done
    done
fi
