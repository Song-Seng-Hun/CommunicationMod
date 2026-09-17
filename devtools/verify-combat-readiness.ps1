param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion', [switch]$PureOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path ([IO.Path]::GetTempPath()) ('combat-readiness-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $out | Out-Null
$sources = @("$PSScriptRoot\CombatDecisionTest.java")
$pure = "$root\src\main\java\communicationmod\observation\CombatDecision.java"
if (Test-Path -LiteralPath $pure) { $sources += $pure }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -d $out @sources
if ($LASTEXITCODE -ne 0) { throw 'Combat test compilation failed' }
& "$DownfallPath\jre\bin\java.exe" '-Xmx128m' -cp $out CombatDecisionTest
if ($LASTEXITCODE -ne 0) { throw 'Combat decision regression failed' }
if ($PureOnly) { return }
$game = "$DownfallPath\desktop-1.0-modded.jar"
$base = "$DownfallPath\package\BaseMod-modded.jar"
$evil = "$DownfallPath\package\EvilWithin-modded.jar"
$gson = Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'
$localGson = Join-Path $out 'gson'
[IO.Compression.ZipFile]::ExtractToDirectory($gson, $localGson)
$compiled = Join-Path $out 'binding'
New-Item -ItemType Directory -Path $compiled | Out-Null
$sources = Get-ChildItem -LiteralPath "$root\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName
$cp = "$compiled;$game;$base;$evil;$localGson"
& javac '-J-Xmx384m' --release 8 -encoding UTF-8 -cp $cp -d $compiled @sources
if ($LASTEXITCODE -ne 0) { throw 'Fresh combat source compilation failed' }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp $cp -d $out "$PSScriptRoot\CombatBindingTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Combat binding test compilation failed' }
& "$DownfallPath\jre\bin\java.exe" '-Xmx256m' -cp "$out;$cp" CombatBindingTest $compiled $game $base $evil
if ($LASTEXITCODE -ne 0) { throw 'Combat binding regression failed' }
