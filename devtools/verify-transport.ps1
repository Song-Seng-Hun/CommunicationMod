param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$java8 = Join-Path $DownfallPath 'jre\bin\java.exe'
$tests = Join-Path $root 'target\transport-tests'
New-Item -ItemType Directory -Force -Path $tests | Out-Null
# Uses Log4j from the jar, but never loads game or Steam classes.
& javac --release 8 -encoding UTF-8 -cp $game -d $tests "$root\src\main\java\communicationmod\DataReader.java" "$root\src\main\java\communicationmod\DataWriter.java" "$PSScriptRoot\TransportTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Transport test compilation failed' }
Push-Location $tests
try {
    & $java8 -cp "$tests;$game" TransportTest
    if ($LASTEXITCODE -ne 0) { throw 'Transport regression failed' }
} finally { Pop-Location }
