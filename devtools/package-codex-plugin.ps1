$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location (Join-Path $root 'mcp-server')
try { & npm.cmd test; if($LASTEXITCODE -ne 0){throw 'MCP tests failed'} } finally {Pop-Location}
& "$PSScriptRoot\PluginLifecycleTest.ps1"
$parent = Join-Path $root ('target\plugin-package-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $parent | Out-Null
Copy-Item -LiteralPath "$root\plugin\downfall-agent" -Destination $parent -Recurse
$package = Join-Path $parent 'downfall-agent'
New-Item -ItemType Directory -Path "$package\server" | Out-Null
Copy-Item -Path "$root\mcp-server\dist\*.js" -Destination "$package\server"
Copy-Item -LiteralPath "$root\mcp-server\package.json","$root\mcp-server\package-lock.json" -Destination "$package\server"
Copy-Item -LiteralPath "$PSScriptRoot\manage-plugin-game.ps1" -Destination "$package\scripts\manage-plugin-game.ps1"
Push-Location "$package\server"
try { & npm.cmd ci --omit=dev --ignore-scripts --no-audit --no-fund; if($LASTEXITCODE -ne 0){throw 'Plugin runtime dependency packaging failed'} } finally {Pop-Location}
if(Get-ChildItem $package -Recurse -File | Where-Object {$_.Extension -in @('.jar','.autosave','.log','.jsonl')}) {throw 'Private/game artifact in plugin package'}
[IO.File]::WriteAllText("$root\target\plugin-package-ready.json", (@{path=$package} | ConvertTo-Json))
Write-Host "PACKAGED: $package"
