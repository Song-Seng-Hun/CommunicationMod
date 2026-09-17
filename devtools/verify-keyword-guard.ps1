param(
    [string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',
    [switch]$UpdateDevelopmentRuntime
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$base = Join-Path $DownfallPath 'package\BaseMod-modded.jar'
$java = Join-Path $DownfallPath 'jre\bin\java.exe'
$mod = Join-Path $root 'target\CommunicationMod.jar'
$tests = Join-Path $root 'target\keyword-tests'
$overlay = Join-Path $root 'target\keyword-overlay'
$runtime = Join-Path $root 'target\downfall-dev'
$classpath = "$mod;$game;$base"
Push-Location $root
try {
    & "$root\.tools\apache-maven-3.9.11\bin\mvn.cmd" -q package "-Dsts.jar=$game" "-Dmts.jar=$game" "-Dbasemod.jar=$base"
    if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }
    New-Item -ItemType Directory -Force -Path $tests, $overlay | Out-Null
    & javac --release 8 -cp $classpath -d $tests "$PSScriptRoot\KeywordGuardTest.java" "$PSScriptRoot\KeywordGuardHarness.java" "$PSScriptRoot\BuildKeywordGuardOverlay.java"
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    Push-Location $tests
    try {
        & $java -cp "$tests;$classpath" KeywordGuardHarness 2>&1 | Tee-Object test-output.log
        if ($LASTEXITCODE -ne 0) { throw 'Keyword guard regression failed' }
        $log = Get-Content -LiteralPath test-output.log -Raw
        if ([regex]::Matches($log, '\[COMM-KEYWORD-GUARD\]').Count -ne 3) { throw 'Missing diagnostics or repeated-frame log spam' }
        foreach ($expected in @('cardId=test:MalformedShopCard', 'Original description for investigation', 'renderKeywords=[block, null, mod:keyword, null]', 'Detection stack only', 'room=<no-room>')) {
            if (-not $log.Contains($expected)) { throw "Diagnostic field missing: $expected" }
        }
        & $java -cp "$tests;$classpath" BuildKeywordGuardOverlay $overlay
        if ($LASTEXITCODE -ne 0) { throw 'Patch ordering verification failed' }
    } finally { Pop-Location }
    if ($UpdateDevelopmentRuntime) {
        $existing = Join-Path $runtime 'desktop-1.0-communication-patched.jar'
        if (-not (Test-Path -LiteralPath $existing)) { throw 'Build the Downfall development runtime first' }
        # Keep the prior artifact as a local rollback copy; the Steam install is never overwritten.
        $backup = Join-Path $overlay ('before-keyword-guard-' + (Get-Date -Format 'yyyyMMddHHmmss') + '.jar')
        Copy-Item -LiteralPath $existing -Destination $backup
        $staged = Join-Path $overlay 'staged-runtime.jar'
        Copy-Item -LiteralPath $existing -Destination $staged -Force
        & jar uf $staged -C $overlay 'com/megacrit/cardcrawl/helpers/TipHelper.class'
        if ($LASTEXITCODE -ne 0) { throw 'Updating development overlay failed' }
        Copy-Item -LiteralPath $mod -Destination (Join-Path $runtime 'mods\CommunicationMod.jar') -Force
        Copy-Item -LiteralPath $staged -Destination $existing -Force
        Write-Host "Development runtime updated. Previous overlay: $backup"
    }
    Write-Host 'PASS: regression, diagnostic fields, log throttling, prepackaged patch ordering'
} finally { Pop-Location }
