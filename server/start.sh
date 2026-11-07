#!/usr/bin/env bash
# al Shabab dedicated server launcher.
# Downloads Forge + pack contents on first run, self-updates the pack on every restart.
set -euo pipefail
cd "$(dirname "$0")/run" 2>/dev/null || { mkdir -p "$(dirname "$0")/run"; cd "$(dirname "$0")/run"; }

# ── Settings ────────────────────────────────────────────────────────────────
PACK_URL="${PACK_URL:-https://raw.githubusercontent.com/36a5/Modpacks/master/pack-two/pack.toml}"
MC_VERSION="1.20.1"
FORGE_VERSION="47.4.18"
MEMORY="${MEMORY:-8G}"
# ────────────────────────────────────────────────────────────────────────────

BOOTSTRAP_URL="https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest/download/packwiz-installer-bootstrap.jar"
FORGE_INSTALLER_URL="https://maven.minecraftforge.net/net/minecraftforge/forge/${MC_VERSION}-${FORGE_VERSION}/forge-${MC_VERSION}-${FORGE_VERSION}-installer.jar"

# First run: install Forge server
if [ ! -d "libraries/net/minecraftforge/forge/${MC_VERSION}-${FORGE_VERSION}" ]; then
    echo "[al-shabab] Installing Forge ${MC_VERSION}-${FORGE_VERSION}..."
    curl -fsSL -o forge-installer.jar "$FORGE_INSTALLER_URL"
    java -jar forge-installer.jar --installServer
    rm -f forge-installer.jar forge-installer.jar.log
fi

# Fetch/update pack contents (mods, configs) from the pack URL
if [ ! -f packwiz-installer-bootstrap.jar ]; then
    curl -fsSL -o packwiz-installer-bootstrap.jar "$BOOTSTRAP_URL"
fi
echo "[al-shabab] Syncing pack from ${PACK_URL}..."
synced=0
for attempt in 1 2 3; do
    if java -jar packwiz-installer-bootstrap.jar -g -s server "$PACK_URL"; then
        synced=1
        break
    fi
    echo "[al-shabab] Pack sync failed (attempt ${attempt} of 3)."
    [ "$attempt" -lt 3 ] && sleep 20
done
if [ "$synced" -eq 0 ]; then
    # A failed sync is usually GitHub being down or serving a stale index. If an earlier sync
    # already installed the pack, start on that instead of staying offline until GitHub recovers.
    if [ -f packwiz.json ] && compgen -G "mods/*.jar" > /dev/null; then
        echo "[al-shabab] Starting with the mods from the last successful sync."
    else
        echo "[al-shabab] Pack sync FAILED and no earlier install exists - refusing to start without mods."
        exit 1
    fi
fi

# JVM args consumed by Forge's run.sh
echo "-Xms${MEMORY} -Xmx${MEMORY} -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20" > user_jvm_args.txt

echo "eula=true" > eula.txt
exec ./run.sh nogui
