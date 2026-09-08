param([switch]$CheckOnly, [switch]$SmokeTest, [switch]$MenuControl)
$ErrorActionPreference = 'Stop'
if ($MenuControl -and ($CheckOnly -or $SmokeTest)) { throw 'MenuControl cannot be combined with check/smoke' }
$root = Split-Path -Parent $PSScriptRoot
$ready = Get-Content -LiteralPath "$root\target\local-test-ready.json" -Raw | ConvertFrom-Json
$runtime = [IO.Path]::GetFullPath($ready.runtime)
$expectedRoot = [IO.Path]::GetFullPath((Join-Path $root 'target')) + [IO.Path]::DirectorySeparatorChar
if (-not $runtime.StartsWith($expectedRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Runtime is outside this workspace target directory' }
if ((Get-FileHash "$runtime\launcher\LocalObserverLaunch.class" -Algorithm SHA256).Hash -ne $ready.launcher_sha256) { throw 'Launcher changed; prepare again' }
$cp = "$runtime\launcher;$runtime\desktop-1.0-modded.jar;$runtime\package\BaseMod-modded.jar;$runtime\package\StSLib-modded.jar;$runtime\package\EvilWithin-modded.jar;$runtime\CommunicationMod.jar"
$javaArgs = @('-Xmx768m', '-Dfile.encoding=UTF-8', '-cp', $cp, 'LocalObserverLaunch')
if ($CheckOnly) { $javaArgs += '--check' } elseif ($SmokeTest) { $javaArgs += '--smoke' } elseif ($MenuControl) { $javaArgs += '--menu-control' }
$oldLocal = $env:LOCALAPPDATA
$oldRoaming = $env:APPDATA
Push-Location $runtime
try {
    $env:LOCALAPPDATA = "$runtime\localappdata"
    $env:APPDATA = "$runtime\appdata"
    Write-Host "Local observer profile: $runtime"
    & $ready.java @javaArgs 2>&1 | Tee-Object -FilePath (Join-Path $runtime ('launch-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.log'))
    if ($LASTEXITCODE -ne 0) { throw "Local observer exited with code $LASTEXITCODE" }
} finally { $env:LOCALAPPDATA = $oldLocal; $env:APPDATA = $oldRoaming; Pop-Location }
