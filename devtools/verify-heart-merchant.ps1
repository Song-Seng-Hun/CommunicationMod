param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root ('target\heart-merchant-tests\run-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out -Force | Out-Null
Start-Transcript -Path (Join-Path $out 'verification.log') | Out-Null
try {
    $game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
    $native = Join-Path $DownfallPath 'package\EvilWithin-modded.jar'
    $base = Join-Path $DownfallPath 'package\BaseMod-modded.jar'
    $java = Join-Path $DownfallPath 'jre\bin\java.exe'
    $compiled = Join-Path $out 'classes'
    New-Item -ItemType Directory -Path $compiled | Out-Null
    $gsonClasses = Join-Path $out 'gson'
    [IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'),$gsonClasses)
    $sources = Get-ChildItem -LiteralPath "$root\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName
    & javac '-J-Xmx384m' --release 8 -encoding UTF-8 -cp "$gsonClasses;$game;$base;$native" -d $compiled @sources 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Fresh production compilation failed' }
    & javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp "$game;$gsonClasses;$compiled" -d $out "$PSScriptRoot\HeartMerchantNativeTest.java" "$PSScriptRoot\RunUsabilityBindingTest.java" "$PSScriptRoot\NativeUiInputTest.java" 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Heart merchant regression compilation failed' }
    & $java '-Xmx192m' '-Dfile.encoding=UTF-8' -cp "$out;$gsonClasses;$game;$compiled" HeartMerchantNativeTest $compiled $native $game 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Heart merchant native-body regression failed' }
    & $java '-Xmx128m' -cp "$out;$game;$compiled" NativeUiInputTest $compiled 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Native input scope regression failed' }
    & $java '-Xmx128m' -cp "$out;$game" RunUsabilityBindingTest room $game $compiled 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Existing room binding regression failed' }
    Write-Host "PASS: fresh compile, Heart native body, existing input/room bindings. Log: $out\verification.log"
} finally { Stop-Transcript | Out-Null }
