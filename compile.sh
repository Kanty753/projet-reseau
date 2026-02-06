#!/bin/bash
set -e
cd "$(dirname "$0")"

JAVAFX_PATH="lib/fx"
GSON_JAR="lib/gson/gson-2.10.1.jar"

mkdir -p target/classes

find src/main/java -name "*.java" | xargs javac \
  --module-path "$JAVAFX_PATH" \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -cp "$GSON_JAR" \
  -d target/classes

# Copier les ressources (CSS, etc.) dans le classpath
cp -r src/main/resources/* target/classes/ 2>/dev/null || true

# Rendre les scripts reseau executables
chmod +x scripts/network/*.sh 2>/dev/null || true
