# ============================================================
#  Block RCON (25575) from the network.
#
#  RUN AS ADMINISTRATOR:
#    right-click → "Run with PowerShell (Admin)"
#  or from an elevated terminal:
#    powershell -ExecutionPolicy Bypass -File .\secure-rcon.ps1
#
#  Why this is needed:
#    * Minecraft binds RCON to 0.0.0.0 (every interface) when server-ip is blank.
#      There is no rcon-only bind setting in server.properties.
#    * Java has a blanket inbound ALLOW rule on *any* port on this machine, so
#      Windows Firewall would otherwise accept connections to 25575.
#    * RCON is UNENCRYPTED and gives full console access. Anyone who reaches it
#      and guesses the password owns the server, and the password crosses the
#      network in the clear.
#
#  Safe for the Discord bot: Windows exempts loopback (127.0.0.1) from firewall
#  filtering, so a bot on this machine still connects.
# ============================================================
#Requires -RunAsAdministrator

$ErrorActionPreference = "Stop"
$name = "Block Minecraft RCON (25575) from network"

if (Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue) {
    Write-Host "Rule already exists." -ForegroundColor Green
} else {
    New-NetFirewallRule `
        -DisplayName $name `
        -Direction Inbound `
        -Protocol TCP `
        -LocalPort 25575 `
        -Action Block `
        -Profile Any `
        -Description "RCON is unencrypted and grants full console access. Loopback is exempt, so local bots still work." | Out-Null
    Write-Host "Created block rule for TCP 25575 (all profiles)." -ForegroundColor Green
}

Write-Host ""
Write-Host "Verifying:" -ForegroundColor Cyan
Get-NetFirewallRule -DisplayName $name |
    Format-Table DisplayName, Enabled, Direction, Action -AutoSize

Write-Host "Block rules take precedence over allow rules, so Java's blanket" -ForegroundColor DarkGray
Write-Host "'allow any port' rule can no longer expose RCON." -ForegroundColor DarkGray
Write-Host ""
Write-Host "STILL TO CHECK BY HAND: your router." -ForegroundColor Yellow
Write-Host "  Open your router admin page -> Port Forwarding."
Write-Host "  There must be NO rule forwarding 25575. Only 25565 (the game) should be forwarded."
Write-Host "  If 'DMZ' is enabled for this PC, turn it OFF - DMZ forwards every port."
