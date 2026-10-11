param(
    [string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',
    [string]$SourceRoot = (Split-Path -Parent $PSScriptRoot)
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root ('target\native-preview-tests\run-' + [guid]::NewGuid().ToString('N'))
$compiled = Join-Path $out 'binding'
New-Item -ItemType Directory -Path $compiled -Force | Out-Null
Start-Transcript -Path (Join-Path $out 'verification.log') | Out-Null
try {
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$evil = Join-Path $DownfallPath 'package\EvilWithin-modded.jar'
$base = Join-Path $DownfallPath 'package\BaseMod-modded.jar'
$gson = Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'
$gsonClasses = Join-Path $out 'gson'
[IO.Compression.ZipFile]::ExtractToDirectory($gson,$gsonClasses)
$sources = Get-ChildItem -LiteralPath (Join-Path $SourceRoot 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName
& javac '-J-Xmx384m' --release 8 -encoding UTF-8 -cp "$gsonClasses;$game;$base;$evil" -d $compiled @sources 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Fresh production compilation failed' }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp $game -d $out "$PSScriptRoot\NativePreviewTest.java" "$PSScriptRoot\NativePreviewBindingTest.java" 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Native preview test compilation failed' }
# Native binding test reads class-file bytes only; it never defines or initializes native classes.
& java '-Xmx256m' '-Dfile.encoding=UTF-8' -cp "$out;$game" NativePreviewBindingTest $compiled $evil $game 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Native preview bytecode binding regression failed' }
# Existing value tests use child-loader graphics fixtures.
& java '-Xmx256m' '-Dfile.encoding=UTF-8' -cp "$out;$game" NativePreviewTest $compiled 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) { throw 'Public preview projection regression failed' }
Write-Host "PASS: native preview bindings and projection ($out); log: $out\verification.log"
} finally { Stop-Transcript | Out-Null }
