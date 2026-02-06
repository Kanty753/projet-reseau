#!/bin/bash
# =============================================================================
# SEND TCP - Envoyer un message TCP et recevoir une reponse
# =============================================================================
# TCP = Transmission Control Protocol
# Contrairement a UDP, TCP etablit une connexion fiable.
# On est garanti que le message arrive (ou qu on recoit une erreur).
# Ideal pour les messages critiques : JOIN, confirmations, etc.
# =============================================================================
# Usage : send_tcp.sh <ip_cible> <port_cible> <message_json>
# Output : la reponse JSON du serveur
# =============================================================================

# -----------------------------------------------------------------------------
# ARGUMENTS
# -----------------------------------------------------------------------------
TARGET_IP="$1"
TARGET_PORT="$2"
MESSAGE="$3"

# -----------------------------------------------------------------------------
# VERIFICATION DES PARAMETRES
# -----------------------------------------------------------------------------
if [ -z "$TARGET_IP" ] || [ -z "$TARGET_PORT" ] || [ -z "$MESSAGE" ]; then
    echo '{"error":"Missing parameters","success":false}'
    exit 1
fi

# -----------------------------------------------------------------------------
# TIMEOUT pour eviter de bloquer indefiniment
# -----------------------------------------------------------------------------
TIMEOUT=5

# -----------------------------------------------------------------------------
# ENVOI TCP ET RECEPTION DE LA REPONSE
# -----------------------------------------------------------------------------
# On envoie le message JSON (termine par newline) et on lit la reponse.
# Le serveur (tcp_server.sh) repond aussi par une ligne JSON.
# -----------------------------------------------------------------------------

RESPONSE=""

# socat (prefere) - plus robuste et gere bien les timeouts
if command -v socat &>/dev/null; then
    # -----------------------------------------------------------------
    # SOCAT
    # -----------------------------------------------------------------
    # socat - TCP:ip:port,connect-timeout=5 :
    #   - : lire depuis stdin (notre message)
    #   TCP:ip:port : se connecter en TCP
    #   connect-timeout : timeout de connexion en secondes
    # On envoie le message, puis on lit la reponse
    # -----------------------------------------------------------------
    RESPONSE=$(echo "$MESSAGE" | socat -T $TIMEOUT - TCP:$TARGET_IP:$TARGET_PORT,connect-timeout=$TIMEOUT 2>/dev/null)
    RESULT=$?
else
    # -----------------------------------------------------------------
    # NETCAT (fallback)
    # -----------------------------------------------------------------
    # nc -w TIMEOUT : timeout global en secondes
    # -q 1 : fermer 1 seconde apres EOF (pour lire la reponse)
    # On tente d abord le style OpenBSD, puis GNU
    # -----------------------------------------------------------------
    RESPONSE=$(echo "$MESSAGE" | nc -w $TIMEOUT -q 1 "$TARGET_IP" "$TARGET_PORT" 2>/dev/null)
    RESULT=$?

    # Fallback GNU netcat si le premier echoue
    if [ $RESULT -ne 0 ] || [ -z "$RESPONSE" ]; then
        RESPONSE=$(echo "$MESSAGE" | nc -w $TIMEOUT "$TARGET_IP" "$TARGET_PORT" 2>/dev/null)
        RESULT=$?
    fi
fi

# -----------------------------------------------------------------------------
# RESULTAT
# -----------------------------------------------------------------------------
if [ $RESULT -eq 0 ] && [ -n "$RESPONSE" ]; then
    echo "$RESPONSE"
else
    echo '{"error":"TCP connection failed","success":false}'
    exit 1
fi
