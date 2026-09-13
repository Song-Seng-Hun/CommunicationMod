param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tests = Join-Path $root 'target\upgrade-preview-tests'
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$java = Join-Path $DownfallPath 'jre\bin\java.exe'
$evil = Join-Path $DownfallPath 'package\EvilWithin-modded.jar'
New-Item -ItemType Directory -Force -Path $tests | Out-Null
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp "$root\target\classes;$game" -d $tests "$PSScriptRoot\UpgradePreviewTest.java" "$PSScriptRoot\UpgradePreviewBindingTest.java" "$PSScriptRoot\UpgradePreviewGameTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Upgrade tests compilation failed' }
# Keep production classes first: an earlier RED/GREEN pure build may exist in tests.
& $java '-Xmx128m' -cp "$root\target\classes;$tests" UpgradePreviewTest
if ($LASTEXITCODE -ne 0) { throw 'Upgrade model regression failed' }
& $java '-Xmx256m' -cp "$root\target\classes;$tests;$game" UpgradePreviewBindingTest "$root\target\classes" $game "$DownfallPath\package\StSLib-modded.jar"
if ($LASTEXITCODE -ne 0) { throw 'Upgrade production binding regression failed' }
& $java '-Xmx256m' '-Dfile.encoding=UTF-8' -cp "$root\target\classes;$tests;$game" UpgradePreviewGameTest "$root\target\classes" $game $evil
if ($LASTEXITCODE -ne 0) { throw 'Upgrade actual-method regression failed' }
