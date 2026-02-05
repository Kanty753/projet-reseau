#!/bin/bash
# =============================================================================
# GET LOCAL IP - Recupere l adresse IP locale de la machine
# =============================================================================
# Ce script essaie plusieurs methodes pour trouver l IP locale.
# Utile car differentes machines/distributions ont differentes commandes.
# Retourne 127.0.0.1 si aucune IP n est trouvee.
# =============================================================================

# -----------------------------------------------------------------------------
# METHODE 1 : via "ip route"
# -----------------------------------------------------------------------------
# "ip route get 8.8.8.8" simule une requete vers le DNS Google.
# Le systeme repond avec la route qu il utiliserait.
# Exemple de sortie : "8.8.8.8 via 192.168.1.1 dev eth0 src 192.168.1.50"
# On extrait le champ apres "src" qui est notre IP locale.
# -----------------------------------------------------------------------------
# grep -oP 'src \K[\d.]+' :
#   -o : affiche seulement la partie qui matche
#   -P : utilise les regex Perl (plus puissantes)
#   \K : "oublie" ce qui precede (ici "src "), garde seulement ce qui suit
#   [\d.]+ : un ou plusieurs chiffres ou points (format IP)
# -----------------------------------------------------------------------------
IP=$(ip route get 8.8.8.8 2>/dev/null | grep -oP 'src \K[\d.]+')

# -----------------------------------------------------------------------------
# METHODE 2 : via "hostname -I"
# -----------------------------------------------------------------------------
# "hostname -I" liste toutes les IP de la machine, separees par des espaces.
# On prend la premiere avec awk '{print $1}'.
# Cette methode peut echouer sur certains systemes minimalistes.
# -----------------------------------------------------------------------------
if [ -z "$IP" ]; then
    IP=$(hostname -I 2>/dev/null | awk '{print $1}')
fi

# -----------------------------------------------------------------------------
# METHODE 3 : via "ifconfig" (ancienne commande, deprecie mais presente partout)
# -----------------------------------------------------------------------------
# ifconfig liste les interfaces reseau.
# On cherche les lignes "inet X.X.X.X" et on extrait l IP.
# On exclut 127.0.0.1 (localhost) avec grep -v.
# head -1 prend la premiere IP trouvee.
# -----------------------------------------------------------------------------
if [ -z "$IP" ]; then
    IP=$(ifconfig 2>/dev/null | grep -oP 'inet \K[\d.]+' | grep -v '127.0.0.1' | head -1)
fi

# -----------------------------------------------------------------------------
# FALLBACK : si rien ne marche, on retourne localhost
# -----------------------------------------------------------------------------
if [ -z "$IP" ]; then
    IP="127.0.0.1"
fi

# Affiche l IP trouvee sur stdout (sera lue par Java)
echo "$IP"
