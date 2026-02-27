#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SERVER_DIR="$SCRIPT_DIR/testing-server"
PLUGINS_DIR="$SERVER_DIR/plugins"

cd "$SCRIPT_DIR"

echo "==> Building..."
mvn -B clean package -q

JAR=$(ls target/Nightfall-*.jar | head -1)
JAR_NAME=$(basename "$JAR")

echo "==> Deploying $JAR_NAME..."
rm -f "$PLUGINS_DIR"/Nightfall*.jar
rm -rf "$PLUGINS_DIR/Nightfall"
cp "$JAR" "$PLUGINS_DIR/"

echo "==> Starting server..."
cd "$SERVER_DIR"
java -Xms512M -Xmx2G -jar paper.jar --nogui
