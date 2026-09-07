param(
    [string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',
    [switch]$UpdateDevelopmentRuntime
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$base = Join-Path $DownfallPath 'package\BaseMod-modded.jar'
$java = Join-Path $DownfallPath 'jre\bin\java.exe'
$mod = Join-Path $root 'target\CommunicationMod.jar'
$tests = Join-Path $root 'target\draw-pile-tests'
Push-Location $root
try {
    & "$root\.tools\apache-maven-3.9.11\bin\mvn.cmd" -q package "-Dsts.jar=$game" "-Dmts.jar=$game" "-Dbasemod.jar=$base"
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
    New-Item -ItemType Directory -Force -Path $tests | Out-Null
    & javac --release 8 -d $tests "$PSScriptRoot\DrawPileVisibilityTest.java"
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    & $java -cp "$tests;$mod" DrawPileVisibilityTest
    if ($LASTEXITCODE -ne 0) { throw 'Draw pile visibility regression failed' }
    if ($UpdateDevelopmentRuntime) {
        $runtimeMod = Join-Path $root 'target\downfall-dev\mods\CommunicationMod.jar'
        if (-not (Test-Path -LiteralPath $runtimeMod)) { throw 'Build the Downfall development runtime first' }
        $backup = Join-Path $tests ('before-draw-pile-visibility-' + (Get-Date -Format 'yyyyMMddHHmmss') + '.jar')
        Copy-Item -LiteralPath $runtimeMod -Destination $backup
        Copy-Item -LiteralPath $mod -Destination $runtimeMod -Force
        Write-Host "Development mod updated. Previous mod: $backup"
    }
} finally { Pop-Location }
