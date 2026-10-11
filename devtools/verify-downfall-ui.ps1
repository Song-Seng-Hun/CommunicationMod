param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',
    [ValidateSet('all','tutorial','reward','wheel','match','base-match')][string]$Mode = 'all')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root ('target\downfall-ui-tests\run-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out -Force | Out-Null
Start-Transcript -Path (Join-Path $out 'verification.log') | Out-Null
try {
    $game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
    $native = Join-Path $DownfallPath 'package\EvilWithin-modded.jar'
    $base = Join-Path $DownfallPath 'package\BaseMod-modded.jar'
    $java = Join-Path $DownfallPath 'jre\bin\java.exe'
    $compiled = Join-Path $out 'classes'
    New-Item -ItemType Directory -Path $compiled | Out-Null
    $gson = Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'
    $gsonClasses = Join-Path $out 'gson'
    [IO.Compression.ZipFile]::ExtractToDirectory($gson,$gsonClasses)
    $sources = Get-ChildItem -LiteralPath "$root\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName
    & javac '-J-Xmx384m' --release 8 -encoding UTF-8 -cp "$gsonClasses;$game;$base;$native" -d $compiled @sources 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Fresh production compilation failed' }
    & javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp "$game;$gsonClasses;$compiled" -d $out "$PSScriptRoot\DownfallUiTest.java" "$PSScriptRoot\NativeRewardCases.java" "$PSScriptRoot\EventReadingTest.java" "$PSScriptRoot\RewardPolicyTest.java" "$PSScriptRoot\NativeUiInputTest.java" 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'UI regression compilation failed' }
    $failed = @()
    # A misspelled/new group must never fall through to an unrelated passing group.
    $invalidOutput = & $java '-Xmx192m' '-Dfile.encoding=UTF-8' -cp "$out;$gsonClasses;$game" DownfallUiTest invalid-group $compiled $native $base $game 2>&1
    if ($LASTEXITCODE -eq 0 -or ($invalidOutput -join "`n") -notmatch 'Unknown UI test group: invalid-group') {
        throw 'UI test dispatcher accepted an unknown group or failed for an unrelated reason'
    }
    Write-Host 'PASS: unknown UI test group rejected (no fallback false-positive)'
    $groups = if ($Mode -eq 'all') { @('tutorial','reward','wheel','match','base-match') } else { @($Mode) }
    foreach ($group in $groups) {
        & $java '-Xmx192m' '-Dfile.encoding=UTF-8' "-Dnative.reward.manifest=$PSScriptRoot\native-rewards.json" -cp "$out;$gsonClasses;$game" DownfallUiTest $group $compiled $native $base $game 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { $failed += $group }
    }
    $guards = if ($Mode -eq 'all') { @('EventReadingTest','RewardPolicyTest','NativeUiInputTest') } else { @() }
    foreach ($test in $guards) {
        & $java '-Xmx192m' '-Dfile.encoding=UTF-8' -cp "$out;$gsonClasses;$game;$compiled" $test $compiled 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) { $failed += $test }
    }
    if ($failed.Count) { throw "Downfall UI regressions failed: $($failed -join ', ')" }
    Write-Host "PASS: fresh production compile; $($groups.Count) Downfall UI groups and $($guards.Count) existing guard regressions. Log: $out\verification.log"
} finally { Stop-Transcript | Out-Null }
