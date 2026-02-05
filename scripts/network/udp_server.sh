#!/bin/bash
# =============================================================================
# UDP SERVER - Serveur UDP pour recevoir les messages de jeu
# =============================================================================
# Ce script ecoute en continu sur un port UDP.
# Chaque message recu est affiche sur stdout, lu par Java.
# Utilise pour les evenements temps reel : tours, timers, mots prononces, etc.
# =============================================================================
# Usage : udp_server.sh <port>
# =============================================================================

# -----------------------------------------------------------------------------
# ARGUMENT
# -----------------------------------------------------------------------------
# $1 = port sur lequel ecouter (ex: 5556)
# -----------------------------------------------------------------------------
LISTEN_PORT="$1"

# Verification : le port est obligatoire
if [ -z "$LISTEN_PORT" ]; then
    echo "Usage: udp_server.sh <port>"
    exit 1
fi

# -----------------------------------------------------------------------------
# FONCTION DE NETTOYAGE (cleanup)
# -----------------------------------------------------------------------------
# Quand le script se termine (signal SIGTERM, SIGINT ou EXIT normal),
# on tue tous les processus enfants pour eviter des zombies.
# -----------------------------------------------------------------------------
# pkill -P $$ : tue tous les processus dont le parent est le PID courant ($$)
# 2>/dev/null : ignore les erreurs si aucun enfant n existe
# -----------------------------------------------------------------------------
cleanup() {
    pkill -P $$ 2>/dev/null
    exit 0
}

# -----------------------------------------------------------------------------
# TRAP - Intercepter les signaux
# -----------------------------------------------------------------------------
# trap <fonction> <signaux> : appelle la fonction quand un signal arrive
# SIGTERM : signal de terminaison (kill <pid>)
# SIGINT  : signal d interruption (Ctrl+C)
# EXIT    : sortie normale du script
# -----------------------------------------------------------------------------
trap cleanup SIGTERM SIGINT EXIT

# -----------------------------------------------------------------------------
# BOUCLE D ECOUTE UDP
# -----------------------------------------------------------------------------
# On prefere socat car il gere mieux les paquets UDP en continu.
# Sinon, on utilise netcat (nc) en fallback.
# -----------------------------------------------------------------------------

if command -v socat &> /dev/null; then
    # -------------------------------------------------------------------------
    # SOCAT
    # -------------------------------------------------------------------------
    # socat -u : mode unidirectionnel (recevoir seulement, pas de reponse)
    # UDP-RECV:<port> : ecouter les paquets UDP sur ce port
    # - : ecrire sur stdout
    # -------------------------------------------------------------------------
    # | while IFS= read -r line : lire ligne par ligne sans modifier
    #     IFS= : ne pas decouper sur les espaces
    #     -r   : ne pas interpreter les backslashes
    # -------------------------------------------------------------------------
    # [ -n "$line" ] && echo "$line" : afficher seulement si non vide
    # -------------------------------------------------------------------------
    socat -u UDP-RECV:$LISTEN_PORT - 2>/dev/null | while IFS= read -r line; do
        [ -n "$line" ] && echo "$line"
    done
else
    # -------------------------------------------------------------------------
    # NETCAT (nc) - fallback
    # -------------------------------------------------------------------------
    # nc -u : mode UDP
    # -l    : mode listen (serveur)
    # -p    : port d ecoute
    # -------------------------------------------------------------------------
    # Contrairement a socat, nc peut se fermer apres un paquet.
    # On met donc une boucle while true pour relancer.
    # -------------------------------------------------------------------------
    while true; do
        nc -u -l -p "$LISTEN_PORT" 2>/dev/null | while IFS= read -r line; do
            [ -n "$line" ] && echo "$line"
        done
    done
fi
