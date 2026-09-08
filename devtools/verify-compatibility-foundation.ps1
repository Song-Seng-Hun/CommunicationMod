param(
    [string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',
    [switch]$UpdateDevelopmentRuntime
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$java8 = Join-Path $DownfallPath 'jre\bin\java.exe'
$tests = Join-Path $root 'target\safety-tests'
$mod = Join-Path $root 'target\CommunicationMod.jar'

# Headless only. The keyword script builds without invoking the game launcher.
& "$PSScriptRoot\verify-keyword-guard.ps1" -DownfallPath $DownfallPath
& "$PSScriptRoot\verify-draw-pile-visibility.ps1" -DownfallPath $DownfallPath
& "$PSScriptRoot\verify-launch-safety.ps1"
& "$PSScriptRoot\verify-protocol.ps1" -Java $java8
& "$PSScriptRoot\verify-transport.ps1" -DownfallPath $DownfallPath
& "$PSScriptRoot\verify-runtime-inventory.ps1"
& "$PSScriptRoot\verify-offline-bytecode.ps1" -DownfallPath $DownfallPath
& "$PSScriptRoot\verify-public-descriptions.ps1" -DownfallPath $DownfallPath
& "$PSScriptRoot\verify-dialogue-observation.ps1" -DownfallPath $DownfallPath
& "$PSScriptRoot\verify-combat-readiness.ps1" -DownfallPath $DownfallPath
& "$PSScriptRoot\verify-map-compatibility.ps1" -DownfallPath $DownfallPath
& javac --release 8 -cp $game -d $tests "$PSScriptRoot\SafetyWiringTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Safety wiring test compilation failed' }
& $java8 -cp "$tests;$game" SafetyWiringTest $mod
if ($LASTEXITCODE -ne 0) { throw 'Built mod safety wiring check failed' }

if ($UpdateDevelopmentRuntime) {
    $runtimeMod = Join-Path $root 'target\downfall-dev\mods\CommunicationMod.jar'
    if (-not (Test-Path -LiteralPath $runtimeMod)) { throw 'No existing development mod to refresh' }
    $backup = Join-Path $tests ('before-automation-hold-' + (Get-Date -Format 'yyyyMMddHHmmssfff') + '.jar')
    Copy-Item -LiteralPath $runtimeMod -Destination $backup
    Copy-Item -LiteralPath $mod -Destination $runtimeMod -Force
    # Replace only our generated launchers, so prior compiled copies also refuse.
    & javac --release 8 -d "$PSScriptRoot\classes" "$PSScriptRoot\DevelopmentLaunchSafety.java" "$PSScriptRoot\DevLoader.java" "$PSScriptRoot\DevGameLauncher.java"
    if ($LASTEXITCODE -ne 0) { throw 'Blocked launcher compilation failed' }
    & $java8 -cp "$tests;$game" SafetyWiringTest $runtimeMod
    if ($LASTEXITCODE -ne 0) { throw 'Refreshed mod verification failed' }
    Write-Host "Updated only blocked local development artifacts. Rollback copy: $backup"
}
Write-Host 'PASS: compatibility foundation only; native isolation, game integration and gameplay remain unverified'
