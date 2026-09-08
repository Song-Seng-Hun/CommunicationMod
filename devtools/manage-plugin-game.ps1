param([Parameter(Mandatory)][ValidateSet('Status','Launch','Validate')][string]$Operation,
      [Parameter(Mandatory)][string]$Workspace)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)

function Get-Profile {
 $workspacePath = (Resolve-Path -LiteralPath $Workspace).Path
 $target = Join-Path $workspacePath 'target'
 $readyPath = Join-Path $target 'local-test-ready.json'
 if (-not (Test-Path -LiteralPath $readyPath)) { throw 'No prepared runtime. Run devtools/prepare-mcp-test.ps1 once.' }
 $ready = Get-Content -LiteralPath $readyPath -Raw | ConvertFrom-Json
 $runtime = [IO.Path]::GetFullPath([string]$ready.runtime)
 if (-not $runtime.StartsWith($target + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Runtime is outside workspace target' }
 if ((Get-Item -LiteralPath $runtime).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Linked runtime is not supported' }
 $launcher = Join-Path $runtime 'launcher\LocalObserverLaunch.class'
 if ((Get-FileHash -LiteralPath $launcher).Hash -ne $ready.launcher_sha256) { throw 'Launcher changed; prepare again' }
 $manifest = Join-Path $runtime 'runtime.properties'
 if (-not (Test-Path -LiteralPath $manifest)) { throw 'Missing runtime manifest' }
 $sourceLine = Get-Content -LiteralPath $manifest | Where-Object { $_.StartsWith('source=') } | Select-Object -First 1
 if (-not $sourceLine) { throw 'Missing source in runtime manifest' }
 $source = [regex]::Replace($sourceLine.Substring(7),'\\u([0-9a-fA-F]{4})',{param($m) [char][Convert]::ToInt32($m.Groups[1].Value,16)})
 $source = [regex]::Replace($source,'\\(.)','$1')
 $java = [IO.Path]::GetFullPath([string]$ready.java)
 if ($java -ne (Join-Path $source 'jre\bin\java.exe')) { throw 'Only the source game bundled Java 8 is allowed' }
 if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'Bundled Java missing' }
 $cp = "$runtime\launcher;$runtime\desktop-1.0-modded.jar;$runtime\package\BaseMod-modded.jar;$runtime\package\StSLib-modded.jar;$runtime\package\EvilWithin-modded.jar;$runtime\CommunicationMod.jar"
 return @{workspace=$workspacePath;target=$target;runtime=$runtime;java=$java;cp=$cp}
}
function Find-Game($gameProfile) {
 # Query failures fail closed; never interpret lack of permissions as 'not running'.
 $all = @(Get-CimInstance Win32_Process -Filter "name='java.exe'" | Where-Object {
  $_.CommandLine -and $_.CommandLine.Contains('LocalObserverLaunch') -and
  $_.CommandLine.IndexOf($gameProfile.target + '\',[StringComparison]::OrdinalIgnoreCase) -ge 0
 })
 if ($all.Count -gt 1) { throw 'Multiple test games active; close unwanted test windows manually' }
 if ($all.Count -eq 0) { return $null }
 $game = $all[0]
 if ($game.CommandLine.IndexOf($gameProfile.runtime + '\launcher;',[StringComparison]::OrdinalIgnoreCase) -lt 0) { throw 'Another test runtime is active; no second game will be launched' }
 if (-not $game.CommandLine.TrimEnd().EndsWith(' --mcp-control')) { throw 'Test game is active in a different control mode; no second game will be launched' }
 return $game
}
function Validate-Game($gameProfile) {
 Push-Location $gameProfile.runtime
 try {
  $check = & $gameProfile.java '-Xmx128m' '-cp' $gameProfile.cp 'LocalObserverLaunch' '--check' 2>&1
  if ($LASTEXITCODE -ne 0) { throw "Prepared runtime verification failed (exit $LASTEXITCODE): $check" }
 } finally { Pop-Location }
}

$gameProfile = Get-Profile
if ($Operation -eq 'Validate') { Validate-Game $gameProfile; @{phase='validated';runtime=$gameProfile.runtime} | ConvertTo-Json -Compress; exit }
$mutex = $null; $held = $false
try {
 if ($Operation -eq 'Launch') {
  $hash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($gameProfile.workspace.ToUpperInvariant())))
  $mutex = [Threading.Mutex]::new($false, "Local\CommunicationMod-$hash")
  try { $held = $mutex.WaitOne(0) } catch [Threading.AbandonedMutexException] { $held = $true }
  if (-not $held) { @{phase='launch_in_progress';retry_launch=$false;next_step='sts_game_status'} | ConvertTo-Json -Compress; exit }
 }
 $game = Find-Game $gameProfile
 if ($game) { @{phase=$(if($Operation -eq 'Launch'){'reused'}else{'running'});pid=$game.ProcessId;runtime=$gameProfile.runtime;test_submissions='disabled_in_test_copy'} | ConvertTo-Json -Compress; exit }
 if ($Operation -eq 'Status') { @{phase='stopped';runtime=$gameProfile.runtime;next_step='sts_start_game'} | ConvertTo-Json -Compress; exit }
 Validate-Game $gameProfile
 $stamp = [guid]::NewGuid().ToString('N')
 $oldLocal=$env:LOCALAPPDATA; $oldRoaming=$env:APPDATA
 try {
  $env:LOCALAPPDATA=Join-Path $gameProfile.runtime 'localappdata'; $env:APPDATA=Join-Path $gameProfile.runtime 'appdata'
  # Start-Process joins ArgumentList on Windows; only the classpath needs quoting.
  if ($gameProfile.cp.Contains('"')) { throw 'Unsupported quote in runtime path' }
  $arguments=@('-Xmx768m','-Dfile.encoding=UTF-8','-cp',('"'+$gameProfile.cp+'"'),'LocalObserverLaunch','--mcp-control')
  $started = Start-Process -FilePath $gameProfile.java -ArgumentList $arguments -WorkingDirectory $gameProfile.runtime -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $gameProfile.runtime "plugin-$stamp.stdout.log") -RedirectStandardError (Join-Path $gameProfile.runtime "plugin-$stamp.stderr.log")
 } finally { $env:LOCALAPPDATA=$oldLocal; $env:APPDATA=$oldRoaming }
 @{phase='running';pid=$started.Id;runtime=$gameProfile.runtime;test_submissions='disabled_in_test_copy'} | ConvertTo-Json -Compress
} finally { if($held){$mutex.ReleaseMutex()}; if($mutex){$mutex.Dispose()} }
