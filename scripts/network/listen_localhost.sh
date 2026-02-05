#!/bin/bash
# =============================================================================
# LISTEN LOCALHOST - Lit les serveurs enregistres en local
# =============================================================================
# Ce script lit le fichier JSON partage cree par broadcast_localhost.sh
# et retourne la liste des serveurs encore "vivants" (timestamp recent).
# =============================================================================
# Usage : listen_localhost.sh [timeout_seconds]
# Output : JSON des serveurs trouves, ex: [{"ip":"127.0.0.1","port":5000,...}]
# =============================================================================

# -----------------------------------------------------------------------------
# FICHIER DES SERVEURS
# -----------------------------------------------------------------------------
REGISTRY_FILE="/tmp/undercover_servers.json"

# -----------------------------------------------------------------------------
# VERIFICATION : si le fichier n existe pas
# -----------------------------------------------------------------------------
# -f "$FILE" : vrai si le fichier existe et est un fichier regulier
# ! : inverse le test
# -----------------------------------------------------------------------------
if [ ! -f "$REGISTRY_FILE" ]; then
    # Aucun serveur enregistre, on retourne un tableau JSON vide
    echo "[]"
    exit 0
fi

# -----------------------------------------------------------------------------
# PARAMETRES DE FILTRAGE
# -----------------------------------------------------------------------------
# date +%s : timestamp Unix actuel (secondes depuis le 1er janvier 1970)
# max_age : on ne garde que les serveurs annonces il y a moins de 15 secondes
#           (un serveur qui n a pas mis a jour son annonce est considere mort)
# -----------------------------------------------------------------------------
current_time=$(date +%s)
max_age=15

# -----------------------------------------------------------------------------
# CONSTRUCTION DU JSON DE SORTIE
# -----------------------------------------------------------------------------
output="["
first=true

# -----------------------------------------------------------------------------
# LECTURE ET FILTRAGE DU FICHIER
# -----------------------------------------------------------------------------
# while IFS= read -r entry : lit ligne par ligne
#   IFS= : ne pas decouper sur les espaces (important pour JSON)
#   -r   : ne pas interpreter les backslashes
# done < <(commande) : "process substitution" - la sortie de commande devient l entree de la boucle
# -----------------------------------------------------------------------------
while IFS= read -r entry; do
    # [ -n "$entry" ] : vrai si entry n est pas vide
    if [ -n "$entry" ]; then
        # -----------------------------------------------------------------
        # EXTRACTION DU TIMESTAMP
        # -----------------------------------------------------------------
        # grep -oP '"timestamp"\s*:\s*\K\d+'
        #   -o : affiche seulement la partie qui matche
        #   -P : regex Perl
        #   \s* : zero ou plusieurs espaces
        #   \K : oublie ce qui precede
        #   \d+ : un ou plusieurs chiffres
        # -----------------------------------------------------------------
        timestamp=$(echo "$entry" | grep -oP '"timestamp"\s*:\s*\K\d+' 2>/dev/null)
        
        if [ -n "$timestamp" ]; then
            # Calculer l age de l annonce
            age=$((current_time - timestamp))
            
            # Garder seulement si l age est inferieur a max_age
            if [ $age -lt $max_age ]; then
                # ---------------------------------------------------------
                # CONSTRUCTION DU TABLEAU JSON
                # ---------------------------------------------------------
                # On ajoute une virgule entre chaque element sauf le premier
                # ---------------------------------------------------------
                if [ "$first" = true ]; then
                    first=false
                else
                    output+=","
                fi
                output+="$entry"
            fi
        fi
    fi
done < <(grep -oP '\{[^{}]*\}' "$REGISTRY_FILE" 2>/dev/null)

# Fermer le tableau JSON
output+="]"

# Afficher sur stdout (sera lu par Java)
echo "$output"
