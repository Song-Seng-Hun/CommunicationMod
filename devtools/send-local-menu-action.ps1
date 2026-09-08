param([string]$ActionId, [ValidateRange(1,60)][int]$TimeoutSeconds = 15)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
function Read-SharedJson([string]$Path) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, ([IO.FileShare]::ReadWrite -bor [IO.FileShare]::Delete))
    $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8)
    try { return ($reader.ReadToEnd() | ConvertFrom-Json) } finally { $reader.Dispose() }
}
$ready = Read-SharedJson "$root\target\local-test-ready.json"
$runtime = [IO.Path]::GetFullPath($ready.runtime)
$targetRoot = [IO.Path]::GetFullPath((Join-Path $root 'target')) + [IO.Path]::DirectorySeparatorChar
if (-not $runtime.StartsWith($targetRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Runtime outside workspace target' }
$recording = Get-ChildItem -LiteralPath "$runtime\recordings" -Directory | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $recording) { throw 'No recording session; start with -MenuControl first' }
$snapshotPath = Join-Path $recording.FullName 'latest-state.json'
$state = Read-SharedJson $snapshotPath
if ($state.runtime.control -ne 'menu_only') { throw 'This recording is observation-only; restart with -MenuControl' }
if (-not $ActionId) { $state | ConvertTo-Json -Depth 25; return }
if ((Get-Item -LiteralPath $snapshotPath).LastWriteTimeUtc -lt [DateTime]::UtcNow.AddSeconds(-10)) { throw 'Recording heartbeat is stale; no action sent' }
if (-not $state.ready -or -not ($state.actions | Where-Object { $_.id -ceq $ActionId })) { throw 'Action is not offered in the latest stable state' }
$requestId = [guid]::NewGuid().ToString()
$request = @{type='act';session_id=$state.session_id;state_id=$state.state_id;request_id=$requestId;action_id=$ActionId;arguments=@{}} | ConvertTo-Json -Compress
$pendingPath = Join-Path $recording.FullName 'menu-request.json'
$tempPath = Join-Path $recording.FullName ("menu-request-$requestId.tmp")
[IO.File]::WriteAllText($tempPath, $request, [Text.UTF8Encoding]::new($false))
# File.Move refuses an existing request. Never replace or retry another command.
[IO.File]::Move($tempPath, $pendingPath)
$replyPath = Join-Path $recording.FullName 'menu-response.json'
$deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
while ([DateTime]::UtcNow -lt $deadline) {
    if (Test-Path -LiteralPath $replyPath) {
        $reply = Read-SharedJson $replyPath
        if ($reply.request_id -eq $requestId) {
            $reply | ConvertTo-Json -Depth 15
            if ($reply.response.type -eq 'error' -or $reply.response.status -ne 'applied') { throw 'Menu command rejected/failed; not retried' }
            return
        }
    }
    Start-Sleep -Milliseconds 100
}
throw "No response for $requestId; outcome unknown. Inspect observations.jsonl before any new action; do not retry automatically."
