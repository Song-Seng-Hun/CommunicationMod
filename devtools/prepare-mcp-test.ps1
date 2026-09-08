param([string]$DownfallPath='D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion')
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
Push-Location "$root\mcp-server"
try {
    & npm.cmd ci --ignore-scripts --no-audit --no-fund
    if($LASTEXITCODE -ne 0){throw 'MCP dependency installation failed'}
    & npm.cmd test
    if($LASTEXITCODE -ne 0){throw 'MCP build/tests failed'}
} finally {Pop-Location}
& "$PSScriptRoot\prepare-local-test.ps1" -DownfallPath $DownfallPath
