param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tests = Join-Path $root ('target\upgrade-preview-tests\run-'+[guid]::NewGuid().ToString('N'))
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$java = Join-Path $DownfallPath 'jre\bin\java.exe'
$evil = Join-Path $DownfallPath 'package\EvilWithin-modded.jar'
New-Item -ItemType Directory -Force -Path $tests | Out-Null
$compiled=Join-Path $tests 'classes'
New-Item -ItemType Directory -Path $compiled | Out-Null
$gsonClasses=Join-Path $tests 'gson'
[IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'),$gsonClasses)
$sources=Get-ChildItem -LiteralPath "$root\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName
& javac '-J-Xmx384m' --release 8 -encoding UTF-8 -cp "$gsonClasses;$game;$evil;$DownfallPath\package\BaseMod-modded.jar" -d $compiled @sources
if ($LASTEXITCODE -ne 0) { throw 'Fresh production compilation failed' }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp "$compiled;$game" -d $tests "$PSScriptRoot\UpgradePreviewTest.java" "$PSScriptRoot\UpgradePreviewBindingTest.java" "$PSScriptRoot\UpgradePreviewGameTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Upgrade tests compilation failed' }
# Keep production classes first: an earlier RED/GREEN pure build may exist in tests.
& $java '-Xmx128m' -cp "$compiled;$tests" UpgradePreviewTest
if ($LASTEXITCODE -ne 0) { throw 'Upgrade model regression failed' }
& $java '-Xmx256m' -cp "$compiled;$tests;$game;$gsonClasses" UpgradePreviewBindingTest $compiled $game "$DownfallPath\package\StSLib-modded.jar"
if ($LASTEXITCODE -ne 0) { throw 'Upgrade production binding regression failed' }
& $java '-Xmx256m' '-Dfile.encoding=UTF-8' -cp "$compiled;$tests;$game;$gsonClasses" UpgradePreviewGameTest $compiled $game $evil
if ($LASTEXITCODE -ne 0) { throw 'Upgrade actual-method regression failed' }
Write-Host "PASS: fresh upgrade projection/native integration ($tests)"
