$ErrorActionPreference = 'Stop'
$manager = Join-Path $PSScriptRoot 'manage-plugin-game.ps1'
if (-not (Test-Path $manager)) { throw 'Missing scoped plugin game launcher' }
# The user-facing game is not a background helper. Inspect the actual launch AST
# without starting another game or disturbing the user's current process.
$parseErrors = $null; $parseTokens = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($manager, [ref]$parseTokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Invalid launcher syntax' }
$launches = @($ast.FindAll({param($node) $node -is [Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Start-Process'}, $true))
if ($launches.Count -ne 1) { throw 'Expected exactly one game process launch' }
$elements = $launches[0].CommandElements
$style = @($elements | Where-Object {$_ -is [Management.Automation.Language.CommandParameterAst] -and $_.ParameterName -eq 'WindowStyle'})
if ($style.Count -ne 1) { throw 'Game launch must explicitly request a visible window' }
$styleIndex = [array]::IndexOf($elements, $style[0])
if ($elements[$styleIndex + 1].Extent.Text -ne 'Normal') { throw 'Game window must use WindowStyle Normal, not Hidden' }
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('comm-plugin-test-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path "$fixture\target\copy\launcher" -Force | Out-Null
[IO.File]::WriteAllText("$fixture\target\copy\launcher\LocalObserverLaunch.class", 'fixture')
$hash = (Get-FileHash "$fixture\target\copy\launcher\LocalObserverLaunch.class").Hash
function Ready($runtime, $launcherHash) {
 [IO.File]::WriteAllText("$fixture\target\local-test-ready.json", (@{runtime=$runtime;launcher_sha256=$launcherHash;java='C:\invalid\java.exe'} | ConvertTo-Json))
}
function Reject($expected) {
 $text = (& pwsh -NoProfile -File $manager -Operation Validate -Workspace $fixture 2>&1 | Out-String)
 if ($LASTEXITCODE -eq 0 -or $text -notmatch $expected) { throw "Expected rejection $expected, got: $text" }
}
Ready $fixture $hash
Reject 'outside.*target'
Ready "$fixture\target\copy" ('0' * 64)
Reject 'Launcher changed'
Ready "$fixture\target\copy" $hash
Reject 'manifest'
# Tests only create disposable synthetic files; no real game is started or modified.
'PASS: outside-runtime, hash mismatch and missing manifest fail before process launch'
