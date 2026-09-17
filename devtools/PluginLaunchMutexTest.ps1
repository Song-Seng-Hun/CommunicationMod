param([string]$Workspace=(Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference='Stop'
$workspacePath=(Resolve-Path -LiteralPath $Workspace).Path
$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($workspacePath.ToUpperInvariant())))
$guard=[Threading.Mutex]::new($false,"Local\CommunicationMod-$hash")
$held=$false
try {
 $held=$guard.WaitOne(0)
 if(-not $held){throw 'Another launcher is active; run this lock test after startup completes'}
 $result=(& pwsh -NoProfile -NonInteractive -File "$PSScriptRoot\manage-plugin-game.ps1" -Operation Launch -Workspace $workspacePath | ConvertFrom-Json)
 if($LASTEXITCODE -ne 0 -or $result.phase -ne 'launch_in_progress'){throw 'Concurrent launch was not blocked'}
 'PASS: second process launch blocked by workspace mutex, no game action sent'
} finally {if($held){$guard.ReleaseMutex()};$guard.Dispose()}
