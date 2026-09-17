param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$java8 = Join-Path $DownfallPath 'jre\bin\java.exe'
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$base = Join-Path $DownfallPath 'package\BaseMod-modded.jar'
$mod = Join-Path $root 'target\CommunicationMod.jar'
$tools = Join-Path $root 'target\observer-tools'
$prepared = Join-Path $root ('target\local-test-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [guid]::NewGuid().ToString('N').Substring(0,8))
$gson = Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'
New-Item -ItemType Directory -Force -Path $tools | Out-Null
Push-Location $root
$oldMaven = $env:MAVEN_OPTS
$oldJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:MAVEN_OPTS = '-Xmx384m'
    $env:JAVA_TOOL_OPTIONS = '-Xmx384m'
    & "$PSScriptRoot\verify-compatibility-foundation.ps1" -DownfallPath $DownfallPath
    # Each foundation script throws on failure and includes a fresh Maven build.
    $env:JAVA_TOOL_OPTIONS = $oldJavaOptions
    $cp = "$root\target\classes;$game;$base;$gson"
    & javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp $cp -d $tools `
        "$PSScriptRoot\OfflineBytecode.java" "$PSScriptRoot\BuildLocalObserver.java" "$PSScriptRoot\LocalObserverLaunch.java" `
        "$PSScriptRoot\ObserverSessionTest.java" "$PSScriptRoot\ObserverClientTest.java" "$PSScriptRoot\LocalObserverBuildTest.java" "$PSScriptRoot\LocalObserverLaunchTest.java" "$PSScriptRoot\SteamUtilsStartupTest.java" `
        "$PSScriptRoot\MenuControlSessionTest.java" "$PSScriptRoot\MenuUiBindingTest.java" "$PSScriptRoot\MenuInboxTest.java" "$PSScriptRoot\PlayUiBindingTest.java" "$PSScriptRoot\RewardUiBindingTest.java" "$PSScriptRoot\HandSelectionPolicyTest.java" "$PSScriptRoot\HandSelectionBindingTest.java" "$PSScriptRoot\RewardPolicyTest.java" "$PSScriptRoot\McpBridgeTest.java" "$PSScriptRoot\McpRecordingTest.java"
    if ($LASTEXITCODE -ne 0) { throw 'Local observer tools compilation failed' }
    foreach ($test in @('ObserverSessionTest','LocalObserverLaunchTest','MenuControlSessionTest')) {
        & $java8 '-Xmx128m' -cp "$tools;$cp" $test
        if ($LASTEXITCODE -ne 0) { throw "$test failed" }
    }
    & $java8 '-Xmx128m' -cp "$tools;$cp" ObserverClientTest $mod
    if ($LASTEXITCODE -ne 0) { throw 'Packaged client round trip failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" MenuInboxTest $mod
    if ($LASTEXITCODE -ne 0) { throw 'Menu client round trip failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" MenuInboxTest $mod play
    if ($LASTEXITCODE -ne 0) { throw 'Play client round trip failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" PlayUiBindingTest $game "$root\target\classes" "$DownfallPath\package\EvilWithin-modded.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Play binding checks failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" RewardUiBindingTest $game "$root\target\classes"
    if ($LASTEXITCODE -ne 0) { throw 'Reward binding checks failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" RewardPolicyTest
    if ($LASTEXITCODE -ne 0) { throw 'Reward policy failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" HandSelectionPolicyTest
    if ($LASTEXITCODE -ne 0) { throw 'Hand selection policy failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" HandSelectionBindingTest $game "$root\target\classes"
    if ($LASTEXITCODE -ne 0) { throw 'Hand selection binding failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" McpBridgeTest
    if ($LASTEXITCODE -ne 0) { throw 'MCP bridge checks failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" McpRecordingTest
    if ($LASTEXITCODE -ne 0) { throw 'MCP recording checks failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" MenuUiBindingTest $game "$root\target\classes"
    if ($LASTEXITCODE -ne 0) { throw 'Menu UI binding checks failed' }
    & $java8 '-Xmx384m' -cp "$tools;$cp" LocalObserverBuildTest $DownfallPath $prepared $mod
    if ($LASTEXITCODE -ne 0) { throw 'Prepared runtime bytecode checks failed' }
    & $java8 '-Xmx128m' -cp "$tools;$cp" SteamUtilsStartupTest "$prepared\desktop-1.0-modded.jar"
    if ($LASTEXITCODE -ne 0) { throw 'Offline startup callback regression failed' }
    New-Item -ItemType Directory -Path "$prepared\launcher", "$prepared\localappdata", "$prepared\appdata" | Out-Null
    Copy-Item -LiteralPath "$tools\LocalObserverLaunch.class" -Destination "$prepared\launcher\LocalObserverLaunch.class"
    # Only promote a copy after the launch integrity check. No game window here.
    $runtimeCp = "$prepared\launcher;$prepared\desktop-1.0-modded.jar;$prepared\package\BaseMod-modded.jar;$prepared\package\StSLib-modded.jar;$prepared\package\EvilWithin-modded.jar;$prepared\CommunicationMod.jar"
    Push-Location $prepared
    try {
        & $java8 '-Xmx128m' -cp $runtimeCp LocalObserverLaunch --check
        if ($LASTEXITCODE -ne 0) { throw 'Prepared profile integrity check failed' }
    } finally { Pop-Location }
    @{ runtime = $prepared; java = $java8; launcher_sha256 = (Get-FileHash "$prepared\launcher\LocalObserverLaunch.class" -Algorithm SHA256).Hash } |
        ConvertTo-Json | Set-Content -LiteralPath "$root\target\local-test-ready.json" -Encoding UTF8
    Write-Host "PREPARED (gameplay not yet tested): $prepared"
    Write-Host 'Start from Start-Downfall-Test.cmd in the repository; this does not change Steam Play.'
} finally { $env:MAVEN_OPTS = $oldMaven; $env:JAVA_TOOL_OPTIONS = $oldJavaOptions; Pop-Location }
