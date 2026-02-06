#!/bin/bash
# =============================================================================
# HANDLE TCP CLIENT - Gere une connexion TCP entrante
# =============================================================================
# Ce script est execute par socat pour chaque client TCP.
# Il lit un message JSON sur stdin (termine par newline),
# ecrit ce message sur un pipe nomme pour que Java le lise,
# puis renvoie une reponse JSON au client.
# =============================================================================
# Ce script est appele par tcp_server.sh via socat EXEC.
# stdin  = donnees du client
# stdout = reponse vers le client
# =============================================================================

# -----------------------------------------------------------------------------
# FICHIER FIFO (pipe nomme) pour transmettre les messages a Java
# -----------------------------------------------------------------------------
# /tmp/undercover_tcp_inbox_PORT est lu en continu par BashExecutor
# Chaque message recu est ecrit ici, et Java le lit.
# -----------------------------------------------------------------------------
INBOX_DIR="/tmp/undercover_tcp_inbox"
mkdir -p "$INBOX_DIR"

# Lire le message du client (une ligne JSON)
# IFS= : ne pas decouper sur les espaces
# -r   : ne pas interpreter les backslashes
# timeout 10 : abandonner si le client n envoie rien en 10 secondes
IFS= read -r -t 10 message

if [ -z "$message" ]; then
    # Pas de message recu, envoyer une erreur
    echo '{"success":false,"error":"No message received"}'
    exit 0
fi

# -----------------------------------------------------------------------------
# ECRIRE LE MESSAGE DANS LE FICHIER D INBOX
# -----------------------------------------------------------------------------
# On ajoute un timestamp unique pour eviter les collisions
# Le fichier est un fichier texte simple, chaque ligne = un message JSON
# Java lit ce fichier periodiquement
# -----------------------------------------------------------------------------
INBOX_FILE="$INBOX_DIR/messages.jsonl"

# flock pour eviter les ecritures concurrentes
(
    flock -x 200 2>/dev/null || true
    echo "$message" >> "$INBOX_FILE"
) 200>"$INBOX_DIR/.lock" 2>/dev/null

# -----------------------------------------------------------------------------
# ENVOYER LA CONFIRMATION AU CLIENT
# -----------------------------------------------------------------------------
# Le client attend une reponse JSON sur la meme connexion.
# On envoie un simple {"success":true,"status":"received"}
# Le vrai traitement du message se fait cote Java.
# -----------------------------------------------------------------------------
echo '{"success":true,"status":"received"}'
