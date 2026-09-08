param(
    [string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',
    [switch]$PrepareArtifacts
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tests = Join-Path $root 'target\offline-tests'
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$java = Join-Path $DownfallPath 'jre\bin\java.exe'
New-Item -ItemType Directory -Force -Path $tests | Out-Null
& javac --release 8 -cp $game -d $tests "$PSScriptRoot\OfflineBytecode.java" "$PSScriptRoot\OfflineBytecodeTest.java" "$PSScriptRoot\PrepareOfflineRuntime.java" "$PSScriptRoot\PrepareOfflineRuntimeTest.java" "$PSScriptRoot\PrepareOfflineRuntimePolicyTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Offline bytecode test compilation failed' }
& $java -cp "$tests;$game" OfflineBytecodeTest $game
if ($LASTEXITCODE -ne 0) { throw 'Offline bytecode regressions failed' }
& $java -cp "$tests;$game" PrepareOfflineRuntimePolicyTest $tests
if ($LASTEXITCODE -ne 0) { throw 'Offline preparation path/hash policy failed' }
if ($PrepareArtifacts) {
    $output = Join-Path $root ('target\offline-prepared-' + [Guid]::NewGuid().ToString('N'))
    & $java -cp "$tests;$game" PrepareOfflineRuntime $DownfallPath $output
    if ($LASTEXITCODE -ne 0) { throw 'Offline artifact preparation failed' }
    & $java -cp "$tests;$game" PrepareOfflineRuntimeTest $game (Join-Path $output 'desktop-1.0-modded.jar')
    if ($LASTEXITCODE -ne 0) { throw 'Prepared artifact verification failed' }
    Write-Host "Prepared, not activated: $output"
}
Write-Host 'PASS: Steam bytecode boundary only; no game launch, no native/OS isolation claim'
