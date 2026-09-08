param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tests = Join-Path $root 'target\map-tests'
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$evil = Join-Path $DownfallPath 'package\EvilWithin-modded.jar'
$java = Join-Path $DownfallPath 'jre\bin\java.exe'
New-Item -ItemType Directory -Force -Path $tests | Out-Null
& javac '-J-Xmx256m' --release 8 -cp $game -d $tests "$root\src\main\java\communicationmod\compat\MapChoicePolicy.java" "$PSScriptRoot\MapChoicePolicyTest.java" "$PSScriptRoot\MapBindingTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Map tests compilation failed' }
& $java '-Xmx128m' -cp $tests MapChoicePolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Map policy regression failed' }
# Build the current project before invoking this standalone script; stale JARs fail the binding check.
& $java '-Xmx256m' -cp "$tests;$game" MapBindingTest "$root\target\CommunicationMod.jar" $evil
if ($LASTEXITCODE -ne 0) { throw 'Map binding regression failed (build the current sources first)' }
