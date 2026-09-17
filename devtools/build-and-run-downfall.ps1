param(
    [string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',
    [switch]$SmokeTest
)

$ErrorActionPreference = 'Stop'
# This applies to -SmokeTest too: the legacy loader can initialize Steam even
# without opening a game window. Never infer isolation from LOCALAPPDATA alone.
throw '[COMM-SAFETY-BLOCK] Legacy game launch is disabled until Steam submission blocking and save/cloud isolation are verified. Use headless verification or devtools/runtime-inventory.ps1. No override is available.'

$repoRoot = Split-Path -Parent $PSScriptRoot
$maven = Join-Path $repoRoot '.tools\apache-maven-3.9.11\bin\mvn.cmd'
$java8 = Join-Path $DownfallPath 'jre\bin\java.exe'
$gameJar = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$baseModJar = Join-Path $DownfallPath 'package\BaseMod-modded.jar'
$stsLibJar = Join-Path $DownfallPath 'package\StSLib-modded.jar'
$downfallModJar = Join-Path $DownfallPath 'package\EvilWithin-modded.jar'
$devRoot = Join-Path $repoRoot 'target\downfall-dev'
$patchMods = Join-Path $devRoot 'patch-mods'
$runtimeMods = Join-Path $devRoot 'mods'
$patchJar = Join-Path $patchMods 'CommunicationMod-patch.jar'
$runtimeCommunicationJar = Join-Path $runtimeMods 'CommunicationMod.jar'
$patchedGameJar = Join-Path $devRoot 'desktop-1.0-communication-patched.jar'
$localAppData = Join-Path $devRoot 'localappdata'

foreach ($required in @($maven, $java8, $gameJar, $baseModJar, $stsLibJar, $downfallModJar)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required file was not found: $required"
    }
}

New-Item -ItemType Directory -Force -Path $patchMods, $runtimeMods, $localAppData | Out-Null

Write-Host 'Building CommunicationMod with the current JDK (Java 11 is acceptable for compilation)...'
& $maven '-q' '-DskipTests' 'package' `
    "-Dsts.jar=$gameJar" `
    "-Dmts.jar=$gameJar" `
    "-Dbasemod.jar=$baseModJar" `
    "-Ddeploy.dir=$devRoot\dev-deploy"
if ($LASTEXITCODE -ne 0) {
    throw "Maven build failed with exit code $LASTEXITCODE"
}

Copy-Item -LiteralPath (Join-Path $repoRoot 'target\CommunicationMod.jar') -Destination $runtimeCommunicationJar -Force
Copy-Item -LiteralPath (Join-Path $repoRoot 'target\CommunicationMod.jar') -Destination $patchJar -Force
jar uf $patchJar -C (Join-Path $repoRoot 'devtools\metadata\CommunicationMod-test') ModTheSpire.json

Write-Host 'Compiling the Java 8-compatible development launchers...'
& javac '--release' '8' '-d' (Join-Path $repoRoot 'devtools\classes') `
    (Join-Path $repoRoot 'devtools\DevLoader.java') `
    (Join-Path $repoRoot 'devtools\DevelopmentLaunchSafety.java') `
    (Join-Path $repoRoot 'devtools\DevGameLauncher.java')
if ($LASTEXITCODE -ne 0) {
    throw "Development launcher compilation failed with exit code $LASTEXITCODE"
}

$env:LOCALAPPDATA = $localAppData
$patchClasspath = @(
    (Join-Path $repoRoot 'devtools\classes'),
    $gameJar,
    $baseModJar,
    $stsLibJar,
    $downfallModJar
) -join [IO.Path]::PathSeparator

Write-Host 'Applying only CommunicationMod patches to the prepackaged Downfall runtime...'
Push-Location $DownfallPath
try {
    & $java8 `
        "-Ddev.sts.jar=$gameJar" `
        "-Ddev.mods.dir=$patchMods" `
        "-Ddev.output.jar=$patchedGameJar" `
        '-cp' $patchClasspath `
        'DevLoader' '--mods' 'CommunicationMod' '--out-jar' '--debug' '--skip-launcher'
    if ($LASTEXITCODE -ne 0) {
        throw "ModTheSpire patch step failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}

$runtimeClasspath = @(
    (Join-Path $repoRoot 'devtools\classes'),
    $patchedGameJar,
    $gameJar,
    $baseModJar,
    $stsLibJar,
    $downfallModJar,
    $runtimeCommunicationJar
) -join [IO.Path]::PathSeparator

$launcherArguments = @(
    "-Ddev.basemod.jar=$baseModJar",
    "-Ddev.stslib.jar=$stsLibJar",
    "-Ddev.downfall.jar=$downfallModJar",
    "-Ddev.communication.jar=$runtimeCommunicationJar",
    '-cp', $runtimeClasspath,
    'DevGameLauncher'
)
if ($SmokeTest) {
    $launcherArguments = @('-Ddev.no-game=true') + $launcherArguments
}

Write-Host 'Starting the development runtime with the bundled Java 8 (the system JDK is unchanged)...'
Push-Location $DownfallPath
try {
    & $java8 @launcherArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Development runtime failed with exit code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
