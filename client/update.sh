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

PACK_URL="https://raw.githubusercontent.com/36a5/Modpacks/master/pack/pack.toml"

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

# --- Enforce exact mod parity ---------------------------------------------
#  packwiz removes mods it installed, but NOT jars a player added by hand.
#  Delete any jar in mods/ that the pack manifest does not list, so every
#  player ends up with the identical mod set (no stray/duplicate mods).
MANIFEST=".packwiz-installer manifest"
if [ -f "$MANIFEST" ] && [ -d mods ]; then
    echo "[al-shabab] Removing any extra mods not in the pack..."
    # Managed jar filenames (mods/* entries) pulled from the manifest JSON.
    keep="$(grep -oE '"mods/[^"]+\.jar"' "$MANIFEST" | sed -E 's#"mods/##; s#"$##' | sort -u)"
    for jar in mods/*.jar; do
        [ -e "$jar" ] || continue
        name="$(basename "$jar")"
        if ! printf '%s\n' "$keep" | grep -qxF "$name"; then
            echo "[al-shabab] removing extra mod: $name"
            rm -f "$jar"
        fi
    done
fi

echo
echo "[al-shabab] Done. Open your launcher, pick the Forge 1.20.1 profile, and play."
