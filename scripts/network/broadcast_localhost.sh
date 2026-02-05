#!/bin/bash
# =============================================================================
# BROADCAST LOCALHOST - Annonce du serveur pour le mode local
# =============================================================================
# Ce script permet de jouer avec plusieurs instances sur le meme PC.
# Au lieu d envoyer en UDP sur le reseau, on ecrit dans un fichier partage.
# Chaque instance peut lire ce fichier pour voir les parties disponibles.
# =============================================================================
# Usage : broadcast_localhost.sh <port> <nom_session> <max_joueurs> <joueurs_actuels> [noms_joueurs]
# =============================================================================

# -----------------------------------------------------------------------------
# ARGUMENTS
# -----------------------------------------------------------------------------
# ${1:-valeur} : si $1 est vide, utilise "valeur" par defaut
# -----------------------------------------------------------------------------
SERVER_PORT="${1:-5000}"        # Port TCP du serveur de jeu
SESSION_NAME="${2:-Game}"       # Nom de la partie (affiche dans la liste)
MAX_PLAYERS="${3:-8}"           # Nombre max de joueurs
CURRENT_PLAYERS="${4:-0}"       # Nombre actuel de joueurs
PLAYER_NAMES="${5:-}"           # Liste des pseudos (separes par des virgules)

INTERVAL="2"                     # Intervalle entre chaque annonce (secondes)

# -----------------------------------------------------------------------------
# FICHIERS PARTAGES
# -----------------------------------------------------------------------------
# /tmp est un repertoire temporaire accessible par tous les utilisateurs.
# On y stocke la liste des serveurs decouverts.
# Le fichier .lock sert a eviter les ecritures simultanees (voir flock).
# -----------------------------------------------------------------------------
REGISTRY_FILE="/tmp/undercover_servers.json"
LOCK_FILE="/tmp/undercover_servers.lock"

# -----------------------------------------------------------------------------
# FONCTION : create_announcement
# -----------------------------------------------------------------------------
# Cree le message JSON d annonce du serveur.
# printf est prefere a echo pour un formatage precis.
# %d = entier, %s = chaine
# -----------------------------------------------------------------------------
create_announcement() {
    local timestamp=$(date +%s)   # Timestamp Unix (secondes depuis 1970)
    printf '{"type":"SERVER_ANNOUNCE","ip":"127.0.0.1","port":%d,"name":"%s","maxPlayers":%d,"currentPlayers":%d,"playerNames":"%s","timestamp":%d}' \
        "$SERVER_PORT" "$SESSION_NAME" "$MAX_PLAYERS" "$CURRENT_PLAYERS" "$PLAYER_NAMES" "$timestamp"
}

# -----------------------------------------------------------------------------
# FONCTION : register_server
# -----------------------------------------------------------------------------
# Enregistre le serveur dans le fichier JSON partage.
# Gere aussi le nettoyage des anciennes entrees (> 10 secondes).
# -----------------------------------------------------------------------------
register_server() {
    local announcement=$(create_announcement)
    
    # -------------------------------------------------------------------------
    # SECTION CRITIQUE avec flock
    # -------------------------------------------------------------------------
    # ( ... ) 200>"$LOCK_FILE" : ouvre le fichier de lock sur le descripteur 200
    # flock -x 200 : prend un verrou exclusif sur ce descripteur
    #   -x = exclusive (un seul processus a la fois)
    # Si un autre processus a deja le verrou, on attend qu il le libere.
    # Cela evite les corruptions si 2 scripts ecrivent en meme temps.
    # -------------------------------------------------------------------------
    (
        flock -x 200 2>/dev/null || true   # On ignore si flock echoue
        
        local current_time=$(date +%s)
        local new_content="["
        local first=true
        
        # ---------------------------------------------------------------------
        # LECTURE ET FILTRAGE DES ANCIENNES ENTREES
        # ---------------------------------------------------------------------
        # grep -oP '\{[^{}]*\}' : extrait chaque objet JSON du fichier
        #   -o : affiche seulement ce qui matche
        #   -P : regex Perl
        #   \{[^{}]*\} : un { suivi de caracteres non-accolade, puis }
        # ---------------------------------------------------------------------
        if [ -f "$REGISTRY_FILE" ]; then
            while IFS= read -r entry; do
                if [ -n "$entry" ]; then
                    # Extraire le port et le timestamp de l entree
                    local entry_port=$(echo "$entry" | grep -oP '"port"\s*:\s*\K\d+' 2>/dev/null)
                    local entry_ts=$(echo "$entry" | grep -oP '"timestamp"\s*:\s*\K\d+' 2>/dev/null)
                    
                    if [ -n "$entry_port" ] && [ -n "$entry_ts" ]; then
                        local age=$((current_time - entry_ts))
                        # ---------------------------------------------------------
                        # On garde l entree si :
                        #   - Ce n est pas notre propre port (on va la remplacer)
                        #   - Elle a moins de 10 secondes (sinon c est un serveur mort)
                        # ---------------------------------------------------------
                        if [ "$entry_port" != "$SERVER_PORT" ] && [ $age -lt 10 ]; then
                            if [ "$first" = true ]; then
                                first=false
                            else
                                new_content+=","
                            fi
                            new_content+="$entry"
                        fi
                    fi
                fi
            done < <(grep -oP '\{[^{}]*\}' "$REGISTRY_FILE" 2>/dev/null)
        fi
        
        # ---------------------------------------------------------------------
        # AJOUTER NOTRE ANNONCE
        # ---------------------------------------------------------------------
        if [ "$first" = true ]; then
            new_content+="$announcement"
        else
            new_content+=",$announcement"
        fi
        new_content+="]"
        
        # Ecrire le nouveau contenu dans le fichier
        echo "$new_content" > "$REGISTRY_FILE"
        
    ) 200>"$LOCK_FILE" 2>/dev/null
}

# -----------------------------------------------------------------------------
# FONCTION : cleanup
# -----------------------------------------------------------------------------
# Appelee quand le script se termine.
# Retire notre serveur du fichier (les autres restent).
# -----------------------------------------------------------------------------
cleanup() {
    (
        flock -x 200 2>/dev/null || true
        if [ -f "$REGISTRY_FILE" ]; then
            local new_content="["
            local first=true
            while IFS= read -r entry; do
                if [ -n "$entry" ]; then
                    local entry_port=$(echo "$entry" | grep -oP '"port"\s*:\s*\K\d+' 2>/dev/null)
                    # On garde toutes les entrees SAUF la notre
                    if [ -n "$entry_port" ] && [ "$entry_port" != "$SERVER_PORT" ]; then
                        if [ "$first" = true ]; then
                            first=false
                        else
                            new_content+=","
                        fi
                        new_content+="$entry"
                    fi
                fi
            done < <(grep -oP '\{[^{}]*\}' "$REGISTRY_FILE" 2>/dev/null)
            new_content+="]"
            echo "$new_content" > "$REGISTRY_FILE"
        fi
    ) 200>"$LOCK_FILE" 2>/dev/null
    exit 0
}

# -----------------------------------------------------------------------------
# TRAP : appeler cleanup a la fermeture
# -----------------------------------------------------------------------------
trap cleanup SIGTERM SIGINT EXIT

# -----------------------------------------------------------------------------
# BOUCLE PRINCIPALE
# -----------------------------------------------------------------------------
# Enregistre le serveur toutes les INTERVAL secondes.
# Le script tourne indefiniment jusqu a etre tue.
# -----------------------------------------------------------------------------
while true; do
    register_server
    sleep "$INTERVAL"
done
