#!/bin/bash
set -e
cd "$(dirname "$0")"

JAVAFX_PATH="lib/fx"
GSON_JAR="lib/gson/gson-2.10.1.jar"

java \
  --module-path "$JAVAFX_PATH" \
  --add-modules javafx.controls,javafx.fxml,javafx.graphics \
  -cp "target/classes:$GSON_JAR" \
  com.undercover.Main
