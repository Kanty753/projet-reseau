#!/bin/bash
# =============================================================================
# LISTEN SERVERS - Ecoute les annonces de serveurs sur le reseau LAN
# =============================================================================
# Ce script ecoute les paquets UDP broadcast envoyes par broadcast_server.sh.
# Quand un serveur annonce sa presence, on l ajoute a la liste des serveurs.
# A la fin du timeout, on retourne la liste complete en JSON.
# =============================================================================
# Usage : listen_servers.sh [timeout_seconds]
# Output : JSON des serveurs decouverts
# =============================================================================

# -----------------------------------------------------------------------------
# CONFIGURATION
# -----------------------------------------------------------------------------
LISTEN_PORT="5555"               # Port UDP sur lequel ecouter (meme que broadcast)
TIMEOUT="${1:-3}"                # Duree d ecoute en secondes (defaut: 3)
OUTPUT_FILE="/tmp/discovered_lan_servers.json"   # Fichier temporaire

# -----------------------------------------------------------------------------
# INITIALISATION
# -----------------------------------------------------------------------------
# On commence avec un tableau JSON vide
# -----------------------------------------------------------------------------
echo "[]" > "$OUTPUT_FILE"

# -----------------------------------------------------------------------------
# TABLEAU ASSOCIATIF pour eviter les doublons
# -----------------------------------------------------------------------------
# declare -A : cree un tableau associatif (cle => valeur)
# La cle sera "ip:port", la valeur sera le JSON complet du serveur
# Si on recoit deux fois le meme serveur, on ecrase simplement l ancienne entree
# -----------------------------------------------------------------------------
declare -A SERVERS

# -----------------------------------------------------------------------------
# FONCTION : cleanup
# -----------------------------------------------------------------------------
# Appelee a la fin du script (timeout ou interruption)
# Affiche le contenu du fichier JSON puis quitte
# -----------------------------------------------------------------------------
cleanup() {
    cat "$OUTPUT_FILE"
    exit 0
}

# trap : intercepter les signaux pour appeler cleanup
trap cleanup SIGTERM SIGINT

# -----------------------------------------------------------------------------
# FONCTION : update_servers
# -----------------------------------------------------------------------------
# Ajoute ou met a jour un serveur dans notre liste
# -----------------------------------------------------------------------------
update_servers() {
    local json="$1"
    
    # -------------------------------------------------------------------------
    # EXTRAIRE IP ET PORT du JSON
    # -------------------------------------------------------------------------
    # grep -oP '"ip"\s*:\s*"\K[^"]+' : extrait la valeur du champ "ip"
    #   \K : oublie "ip": et les guillemets
    #   [^"]+ : tout sauf guillemet (= la valeur)
    # -------------------------------------------------------------------------
    local ip=$(echo "$json" | grep -oP '"ip"\s*:\s*"\K[^"]+')
    local port=$(echo "$json" | grep -oP '"port"\s*:\s*\K\d+')
    local key="${ip}:${port}"   # Cle unique pour ce serveur
    
    # Stocker dans le tableau associatif (ecrase si deja present)
    SERVERS["$key"]="$json"
    
    # -------------------------------------------------------------------------
    # RECONSTRUIRE LE FICHIER JSON
    # -------------------------------------------------------------------------
    local output="["
    local first=true
    
    # ${SERVERS[@]} : toutes les valeurs du tableau
    for srv in "${SERVERS[@]}"; do
        if [ "$first" = true ]; then
            first=false
        else
            output+=","
        fi
        output+="$srv"
    done
    output+="]"
    
    # Ecrire dans le fichier temporaire
    echo "$output" > "$OUTPUT_FILE"
}

# -----------------------------------------------------------------------------
# BOUCLE PRINCIPALE
# -----------------------------------------------------------------------------
# On calcule l heure de fin = maintenant + timeout
# Tant qu on n a pas atteint cette heure, on ecoute les paquets UDP
# -----------------------------------------------------------------------------
timeout_end=$(($(date +%s) + TIMEOUT))

while [ $(date +%s) -lt $timeout_end ]; do
    # -------------------------------------------------------------------------
    # RECEPTION UDP avec timeout court (1 seconde)
    # -------------------------------------------------------------------------
    # timeout 1 commande : execute la commande avec un timeout de 1 seconde
    # -------------------------------------------------------------------------
    message=""
    
    # SOCAT (prefere)
    # -------------------------------------------------------------------------
    # socat -u : mode unidirectionnel (recevoir seulement)
    # UDP-RECV:port,reuseaddr : ecouter sur ce port, reutiliser l adresse si occupee
    # - : ecrire sur stdout
    # -------------------------------------------------------------------------
    if command -v socat &>/dev/null; then
        message=$(timeout 1 socat -u UDP-RECV:$LISTEN_PORT,reuseaddr - 2>/dev/null)
    fi
    
    # -------------------------------------------------------------------------
    # NETCAT (fallback) - compatible OpenBSD et GNU netcat
    # -------------------------------------------------------------------------
    # -u : mode UDP
    # -l : mode listen (serveur)
    # Pour OpenBSD nc: nc -u -l port (sans -p)
    # Pour GNU nc: nc -u -l -p port
    # -------------------------------------------------------------------------
    if [ -z "$message" ]; then
        # Essayer d abord le style OpenBSD (nc -u -l port)
        message=$(timeout 1 nc -u -l $LISTEN_PORT 2>/dev/null)
    fi
    
    if [ -z "$message" ]; then
        # Fallback style GNU (nc -u -l -p port)
        message=$(timeout 1 nc -u -l -p $LISTEN_PORT 2>/dev/null)
    fi
    
    # -------------------------------------------------------------------------
    # TRAITER LE MESSAGE S IL CONTIENT UNE ANNONCE
    # -------------------------------------------------------------------------
    # grep -q : mode quiet (pas d affichage, juste le code retour)
    # On verifie que le message contient "SERVER_ANNOUNCE"
    # -------------------------------------------------------------------------
    if [ -n "$message" ] && echo "$message" | grep -q "SERVER_ANNOUNCE"; then
        update_servers "$message"
    fi
done

# -----------------------------------------------------------------------------
# SORTIE FINALE
# -----------------------------------------------------------------------------
# Afficher le contenu du fichier JSON
# -----------------------------------------------------------------------------
cat "$OUTPUT_FILE"
