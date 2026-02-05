#!/bin/bash
# =============================================================================
# SEND UDP - Envoyer un message UDP (fire-and-forget)
# =============================================================================
# UDP = User Datagram Protocol
# Contrairement a TCP, UDP n etablit pas de connexion.
# On envoie le paquet et on n attend pas de confirmation.
# Avantages : tres rapide, faible latence
# Inconvenients : le paquet peut se perdre sans qu on le sache
# Ideal pour les messages de jeu frequents (position, timer, etc.)
# =============================================================================
# Usage : send_udp.sh <ip_cible> <port_cible> <message_json>
# =============================================================================

# -----------------------------------------------------------------------------
# ARGUMENTS
# -----------------------------------------------------------------------------
# $1 = IP de la machine destinataire (ex: 192.168.1.10)
# $2 = port UDP sur lequel le destinataire ecoute (ex: 5556)
# $3 = message a envoyer (en JSON pour notre jeu)
# -----------------------------------------------------------------------------
TARGET_IP="$1"
TARGET_PORT="$2"
MESSAGE="$3"

# -----------------------------------------------------------------------------
# VERIFICATION DES PARAMETRES
# -----------------------------------------------------------------------------
# -z "$VAR" retourne vrai si la variable est vide
# On verifie que les 3 arguments sont presents
# -----------------------------------------------------------------------------
if [ -z "$TARGET_IP" ] || [ -z "$TARGET_PORT" ] || [ -z "$MESSAGE" ]; then
    # Retourne un JSON d erreur pour que Java puisse le parser
    echo '{"error":"Missing parameters","success":false}'
    exit 1
fi

# -----------------------------------------------------------------------------
# ENVOI DU MESSAGE UDP
# -----------------------------------------------------------------------------
# On prefere "socat" car il est plus robuste et moderne.
# Si socat n est pas installe, on utilise "nc" (netcat) en fallback.
# -----------------------------------------------------------------------------

# "command -v socat" verifie si socat est disponible dans le PATH
# &>/dev/null redirige stdout et stderr vers /dev/null (on veut juste le code retour)
if command -v socat &> /dev/null; then
    # ---------------------------------------------------------------------
    # SOCAT
    # ---------------------------------------------------------------------
    # socat -u : mode unidirectionnel (on envoie, on ne lit pas la reponse)
    # - : lire depuis stdin (le message passe par pipe)
    # UDP-DATAGRAM:IP:PORT : envoyer en UDP vers cette destination
    # 2>/dev/null : ignorer les erreurs stderr
    # ---------------------------------------------------------------------
    echo "$MESSAGE" | socat -u - UDP-DATAGRAM:$TARGET_IP:$TARGET_PORT 2>/dev/null
    RESULT=$?
else
    # ---------------------------------------------------------------------
    # NETCAT (nc) - fallback
    # ---------------------------------------------------------------------
    # nc -u : mode UDP (par defaut nc utilise TCP)
    # -w 0 : timeout de 0 seconde (fire-and-forget, ne pas attendre)
    # $TARGET_IP $TARGET_PORT : destination
    # 2>/dev/null : ignorer les erreurs
    # ---------------------------------------------------------------------
    echo "$MESSAGE" | nc -u -w 0 "$TARGET_IP" "$TARGET_PORT" 2>/dev/null
    RESULT=$?
fi

# -----------------------------------------------------------------------------
# RESULTAT
# -----------------------------------------------------------------------------
# $? contient le code de retour de la derniere commande
# 0 = succes, autre = erreur
# On retourne un JSON pour que Java puisse savoir si ca a marche
# -----------------------------------------------------------------------------
if [ $RESULT -eq 0 ]; then
    echo '{"success":true}'
else
    echo '{"error":"UDP send failed","success":false}'
    exit 1
fi
