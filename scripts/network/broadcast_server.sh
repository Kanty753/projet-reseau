#!/bin/bash
# =============================================================================
# BROADCAST SERVER - Annonce UDP du serveur sur le reseau LAN
# =============================================================================
# Ce script envoie regulierement des paquets UDP en "broadcast" sur le LAN.
# Tous les autres PC du reseau local peuvent recevoir ces paquets.
# Cela permet aux joueurs de decouvrir les parties disponibles.
# =============================================================================
# Usage : broadcast_server.sh <ip> <port> <nom_session> <max_joueurs> <joueurs_actuels> [noms_joueurs]
# =============================================================================

# -----------------------------------------------------------------------------
# ARGUMENTS
# -----------------------------------------------------------------------------
SERVER_IP="${1:-localhost}"      # IP du serveur (notre IP locale)
SERVER_PORT="${2:-5000}"         # Port TCP du serveur de jeu
SESSION_NAME="${3:-Game}"        # Nom de la partie
MAX_PLAYERS="${4:-8}"            # Nombre max de joueurs
CURRENT_PLAYERS="${5:-0}"        # Nombre actuel de joueurs
PLAYER_NAMES="${6:-}"            # Liste des pseudos (virgules)

# -----------------------------------------------------------------------------
# CONFIGURATION DU BROADCAST
# -----------------------------------------------------------------------------
# BROADCAST_PORT : port UDP sur lequel on envoie les annonces
#                  tous les clients ecoutent sur ce port
# INTERVAL : frequence d envoi des annonces (toutes les 5 secondes)
# -----------------------------------------------------------------------------
BROADCAST_PORT="5555"
INTERVAL="1"

# -----------------------------------------------------------------------------
# FONCTION : create_announcement
# -----------------------------------------------------------------------------
# Cree le message JSON d annonce.
# Contient toutes les infos necessaires pour qu un client puisse se connecter.
# -----------------------------------------------------------------------------
create_announcement() {
    local timestamp=$(date +%s)   # Timestamp pour detecter les serveurs "morts"
    echo "{\"type\":\"SERVER_ANNOUNCE\",\"ip\":\"$SERVER_IP\",\"port\":$SERVER_PORT,\"name\":\"$SESSION_NAME\",\"maxPlayers\":$MAX_PLAYERS,\"currentPlayers\":$CURRENT_PLAYERS,\"playerNames\":\"$PLAYER_NAMES\",\"timestamp\":$timestamp}"
}

# -----------------------------------------------------------------------------
# FONCTION : get_broadcast_ip
# -----------------------------------------------------------------------------
# Calcule l adresse de broadcast du reseau local.
# Une adresse broadcast permet d envoyer un paquet a TOUS les PC du reseau.
# -----------------------------------------------------------------------------
# On utilise "ip addr" pour recuperer la vraie adresse de broadcast
# -----------------------------------------------------------------------------
get_broadcast_ip() {
    # ip addr : affiche les interfaces reseau avec leurs adresses
    # grep "brd" : lignes contenant une adresse broadcast
    # grep -v "127.0.0" : exclure localhost
    # grep -v "docker" : exclure les interfaces docker
    # grep -oP 'brd \K[\d.]+' : extraire l adresse apres "brd "
    # head -1 : prendre la premiere
    local brd=$(ip addr | grep "inet " | grep " brd " | grep -v "127.0.0" | grep -v "docker" | grep -oP 'brd \K[\d.]+' | head -1)
    
    # Si on n a pas trouve, fallback sur l ancienne methode
    if [ -z "$brd" ]; then
        brd=$(ip route | grep -oP 'src \K[\d.]+' | head -1 | sed 's/\.[0-9]*$/.255/')
    fi
    
    echo "$brd"
}

# -----------------------------------------------------------------------------
# CALCUL DE L ADRESSE BROADCAST
# -----------------------------------------------------------------------------
BROADCAST_IP=$(get_broadcast_ip)

# Si on n a pas trouve d adresse, on utilise le broadcast global
# 255.255.255.255 = broadcast sur tous les reseaux (peut etre bloque par routeurs)
[ -z "$BROADCAST_IP" ] && BROADCAST_IP="255.255.255.255"

# Debug : afficher les parametres
echo "=== Broadcast Server ===" >&2
echo "IP Serveur: $SERVER_IP" >&2
echo "Port: $SERVER_PORT" >&2
echo "Broadcast IP: $BROADCAST_IP" >&2
echo "Broadcast Port: $BROADCAST_PORT" >&2
echo "========================" >&2

# -----------------------------------------------------------------------------
# BOUCLE D ANNONCE
# -----------------------------------------------------------------------------
# On envoie le message toutes les INTERVAL secondes, indefiniment.
# Le script tourne jusqu a etre tue (quand le serveur ferme).
# -----------------------------------------------------------------------------
while true; do
    MESSAGE=$(create_announcement)
    SENT=false
    
    # -------------------------------------------------------------------------
    # ENVOI UDP BROADCAST avec socat (prefere)
    # -------------------------------------------------------------------------
    # socat - : lire depuis stdin (le message)
    # UDP-DATAGRAM:IP:PORT,broadcast : envoyer en UDP broadcast
    #   broadcast : flag qui indique que c est une adresse de broadcast
    # -------------------------------------------------------------------------
    if command -v socat &>/dev/null; then
        echo "$MESSAGE" | socat - UDP-DATAGRAM:$BROADCAST_IP:$BROADCAST_PORT,broadcast 2>/dev/null && SENT=true
    fi
    
    # -------------------------------------------------------------------------
    # FALLBACK avec netcat si socat n est pas disponible ou echoue
    # -------------------------------------------------------------------------
    # nc -u : mode UDP
    # -w 1  : timeout 1 seconde
    # Note: OpenBSD netcat ne supporte pas -b, on envoie directement
    # -------------------------------------------------------------------------
    if [ "$SENT" = false ]; then
        echo "$MESSAGE" | nc -u -w 1 "$BROADCAST_IP" "$BROADCAST_PORT" 2>/dev/null
    fi
    
    # Attendre avant la prochaine annonce
    sleep "$INTERVAL"
done
