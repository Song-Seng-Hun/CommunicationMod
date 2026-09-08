param(
    [string]$GsonJar = (Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'),
    [string]$Java = 'java'
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tests = Join-Path $root 'target\protocol-tests'
if (-not (Test-Path -LiteralPath $GsonJar -PathType Leaf)) { throw 'Gson 2.8.9 jar missing; build the project first or pass -GsonJar' }
New-Item -ItemType Directory -Force -Path $tests | Out-Null
$sources = @("$root\src\main\java\communicationmod\protocol\ProtocolSession.java", "$root\src\main\java\communicationmod\protocol\StrictJson.java", "$PSScriptRoot\ProtocolSessionTest.java", "$PSScriptRoot\ProtocolFixtureServer.java", "$root\src\main\java\communicationmod\observation\EventReading.java", "$PSScriptRoot\EventReadingProtocolTest.java")
$eventActions = "$root\src\main\java\communicationmod\protocol\EventReadingActions.java"
if (Test-Path -LiteralPath $eventActions) { $sources += $eventActions }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp $GsonJar -d $tests @sources
if ($LASTEXITCODE -ne 0) { throw 'Protocol compilation failed' }
# No game jars or native Steam libraries in this test JVM.
& $Java -cp "$tests;$GsonJar" ProtocolSessionTest
if ($LASTEXITCODE -ne 0) { throw 'Protocol regression failed' }
& $Java '-Xmx128m' '-Dfile.encoding=UTF-8' -cp "$tests;$GsonJar" EventReadingProtocolTest
if ($LASTEXITCODE -ne 0) { throw 'Event reading protocol regression failed' }
$hello = '{"type":"hello","protocol_version":2}'
$output = @($hello | & $Java -cp "$tests;$GsonJar" ProtocolFixtureServer)
if ($LASTEXITCODE -ne 0 -or $output.Count -ne 2) { throw 'JSON-lines fixture transport failed' }
$greeting = $output[0] | ConvertFrom-Json
$state = $output[1] | ConvertFrom-Json
if ($greeting.type -ne 'hello' -or $state.type -ne 'state' -or $state.runtime.kind -ne 'fixture' -or $state.ready) { throw 'Unexpected fixture response' }
Write-Host 'PASS: real stdin/stdout JSON-lines handshake and read-only fixture snapshot (not gameplay)'
