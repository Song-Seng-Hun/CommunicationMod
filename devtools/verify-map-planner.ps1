param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tests = Join-Path $root 'target\map-planner-tests'
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$java = Join-Path $DownfallPath 'jre\bin\java.exe'
New-Item -ItemType Directory -Force -Path $tests | Out-Null
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp "$root\target\classes;$game" -d $tests "$PSScriptRoot\MapAnnotationsTest.java" "$PSScriptRoot\MapPlannerBindingTest.java" "$PSScriptRoot\MapPlannerActionTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Map planner tests compilation failed' }
& $java '-Xmx128m' -cp "$tests;$root\target\classes" MapAnnotationsTest
if ($LASTEXITCODE -ne 0) { throw 'Map planner model regression failed' }
& $java '-Xmx256m' -cp "$tests;$root\target\classes;$game" MapPlannerBindingTest $game "$DownfallPath\package\BaseMod-modded.jar" "$root\target\classes"
if ($LASTEXITCODE -ne 0) { throw 'Map planner binding regression failed' }
& $java '-Xmx128m' -cp "$tests;$root\target\classes;$game" MapPlannerActionTest "$root\target\classes"
if ($LASTEXITCODE -ne 0) { throw 'Map planner action regression failed' }
