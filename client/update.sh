#!/usr/bin/env bash
# ============================================================
#  al Shabab - modpack installer / updater  (macOS / Linux)
#
#  Works with every launcher. Run it any time: it installs the
#  pack the first time and updates you to the server's exact
#  version every time after.
#
#  Usage:
#    ./update.sh                       (uses the default .minecraft folder)
#    ./update.sh /path/to/game/folder  (any other game folder)
#
#  Requirements: Java 17+, and the Forge 1.20.1 profile/instance
#  already created once in your launcher.
# ============================================================
set -euo pipefail

PACK_URL="https://36a5.github.io/Modpacks/pack.toml"

if [ -n "${1:-}" ]; then
    GAME_DIR="$1"
elif [ "$(uname)" = "Darwin" ]; then
    GAME_DIR="$HOME/Library/Application Support/minecraft"
else
    GAME_DIR="$HOME/.minecraft"
fi

echo "[al-shabab] Game directory: $GAME_DIR"

if ! command -v java >/dev/null 2>&1; then
    echo "ERROR: Java is not installed. Get Java 17 from https://adoptium.net"
    exit 1
fi

if [ ! -d "$GAME_DIR" ]; then
    echo "ERROR: '$GAME_DIR' does not exist."
    echo "       Launch your launcher once with Forge 1.20.1, or pass the folder:"
    echo "           ./update.sh /path/to/your/game/folder"
    exit 1
fi

cd "$GAME_DIR"

if [ ! -f packwiz-installer-bootstrap.jar ]; then
    echo "[al-shabab] Downloading the updater..."
    curl -fsSL -o packwiz-installer-bootstrap.jar \
        https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar
fi

echo "[al-shabab] Installing / updating the pack. First run takes a few minutes..."
java -jar packwiz-installer-bootstrap.jar -g -s client "$PACK_URL"

echo
echo "[al-shabab] Done. Open your launcher, pick the Forge 1.20.1 profile, and play."
