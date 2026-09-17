param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',
      [switch]$PureOnly)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path ([IO.Path]::GetTempPath()) ('dialogue-observation-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $out | Out-Null
$sources = @("$PSScriptRoot\DialogueHistoryTest.java", "$PSScriptRoot\RenderedWordsTest.java", "$PSScriptRoot\EventReadingTest.java")
$reading = "$root\src\main\java\communicationmod\observation\EventReading.java"
if (Test-Path -LiteralPath $reading) { $sources += $reading }
$pure = "$root\src\main\java\communicationmod\observation\DialogueHistory.java"
if (Test-Path -LiteralPath $pure) { $sources += $pure }
$words = "$root\src\main\java\communicationmod\observation\RenderedWords.java"
if (Test-Path -LiteralPath $words) { $sources += $words }
$visibility = "$root\src\main\java\communicationmod\observation\DialogueVisibility.java"
if (Test-Path -LiteralPath $visibility) { $sources += $visibility }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -d $out @sources
if ($LASTEXITCODE -ne 0) { throw 'Dialogue pure compilation failed' }
& "$DownfallPath\jre\bin\java.exe" '-Xmx128m' '-Dfile.encoding=UTF-8' -cp $out DialogueHistoryTest
if ($LASTEXITCODE -ne 0) { throw 'Dialogue history tests failed' }
& "$DownfallPath\jre\bin\java.exe" '-Xmx128m' '-Dfile.encoding=UTF-8' -cp $out RenderedWordsTest
if ($LASTEXITCODE -ne 0) { throw 'Rendered word tests failed' }
& "$DownfallPath\jre\bin\java.exe" '-Xmx128m' '-Dfile.encoding=UTF-8' -cp $out EventReadingTest
if ($LASTEXITCODE -ne 0) { throw 'Event reading tests failed' }
if ($PureOnly) { return }
$game = "$DownfallPath\desktop-1.0-modded.jar"
$base = "$DownfallPath\package\BaseMod-modded.jar"
$binding = "$root\src\main\java\communicationmod\patches\DialogueRenderPatch.java"
if (-not (Test-Path -LiteralPath $binding)) { throw 'ASSERT: dialogue render hooks missing' }
$compiled = Join-Path $out 'binding'
New-Item -ItemType Directory -Path $compiled | Out-Null
$gson = Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'
$localGson = Join-Path $out 'gson'
[IO.Compression.ZipFile]::ExtractToDirectory($gson, $localGson)
$sources = Get-ChildItem -LiteralPath "$root\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName
$cp = "$compiled;$game;$base;$localGson"
& javac '-J-Xmx384m' --release 8 -encoding UTF-8 -cp $cp -d $compiled @sources
if ($LASTEXITCODE -ne 0) { throw 'Actual dialogue source compilation failed' }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp $cp -d $out "$PSScriptRoot\DialogueBindingTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Dialogue binding test compilation failed' }
& "$DownfallPath\jre\bin\java.exe" '-Xmx256m' -cp "$out;$cp" DialogueBindingTest $compiled $game $base
if ($LASTEXITCODE -ne 0) { throw 'Dialogue binding tests failed' }
