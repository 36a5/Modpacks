Add-Type -AssemblyName System.IO.Compression.FileSystem
New-Item -ItemType Directory -Force "$PSScriptRoot\..\.cache" | Out-Null
$mods = "$PSScriptRoot\..\.cache\client-mods"
$rows = @()

foreach ($jar in Get-ChildItem $mods -Filter *.jar) {
    try { $z = [IO.Compression.ZipFile]::OpenRead($jar.FullName) } catch { continue }
    foreach ($e in $z.Entries) {
        if ($e.FullName -match '^assets/([^/]+)/lang/en_us\.json$') {
            $ns = $Matches[1]
            $sr = New-Object IO.StreamReader($e.Open()); $txt = $sr.ReadToEnd(); $sr.Close()
            try { $json = $txt | ConvertFrom-Json } catch { continue }
            foreach ($p in $json.PSObject.Properties) {
                # KeyMapping translation keys look like "key.<something>" but skip the
                # vanilla category headers ("key.categories.*") and vanilla's own binds.
                if ($p.Name -like 'key.*' -and $p.Name -notlike 'key.categories.*') {
                    $rows += [pscustomobject]@{
                        Mod       = $jar.Name
                        Namespace = $ns
                        KeyId     = $p.Name          # goes into options.txt as key_<KeyId>
                        Label     = $p.Value
                    }
                }
            }
        }
    }
    $z.Dispose()
}

# vanilla binds also live in minecraft's own lang; ignore those namespaces
$rows = $rows | Where-Object { $_.Namespace -ne 'minecraft' } | Sort-Object Namespace, KeyId -Unique

$out = "$PSScriptRoot\..\.cache\keybinds.csv"
$rows | Export-Csv $out -NoTypeInformation
Write-Host ("mod keybinds found: {0} across {1} mods" -f $rows.Count, ($rows | Select-Object -Expand Namespace -Unique).Count)
Write-Host ""
$rows | Group-Object Namespace | Sort-Object Count -Descending | ForEach-Object {
    "{0,-26} {1}" -f $_.Name, ($_.Group.Label -join ' | ')
}
