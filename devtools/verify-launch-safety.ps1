$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tests = Join-Path $root 'target\safety-tests'
New-Item -ItemType Directory -Force -Path $tests | Out-Null
& javac --release 8 -d $tests "$PSScriptRoot\DevelopmentLaunchSafety.java" "$PSScriptRoot\DevLoader.java" "$PSScriptRoot\DevGameLauncher.java" "$PSScriptRoot\LaunchSafetyTest.java" "$root\src\main\java\communicationmod\safety\AutomationSafety.java"
if ($LASTEXITCODE -ne 0) { throw 'Safety test compilation failed' }
# No game, mod, Steam classes or native libraries are on the classpath.
& java -cp $tests LaunchSafetyTest
if ($LASTEXITCODE -ne 0) { throw 'Safety regression failed' }
foreach ($smoke in @($false, $true)) {
    $blocked = $false
    try { & "$PSScriptRoot\build-and-run-downfall.ps1" -SmokeTest:$smoke }
    catch {
        if (-not $_.Exception.Message.Contains('COMM-SAFETY-BLOCK')) { throw }
        $blocked = $true
    }
    if (-not $blocked) { throw 'Unverified PowerShell launch was allowed' }
}
Write-Host 'PASS: normal/smoke script entrypoints refuse before build or game launch'
