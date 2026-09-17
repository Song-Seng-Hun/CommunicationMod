#Requires -Version 7.0
param(
 [ValidateSet('Prepare','Connect','Status','Stop')][string]$Operation='Prepare',
 [ValidatePattern('^tunnel_[A-Za-z0-9]+$')][string]$TunnelId,
 [string]$KeyFile
)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$toolsDir=Join-Path $root 'target\chat-tunnel-tools'
$readyFile=Join-Path $toolsDir 'ready.json'
$alias='communicationmod-downfall'
$profileDir=Join-Path $env:APPDATA 'tunnel-client\communicationmod'

function Read-Ready {
 if(-not(Test-Path -LiteralPath $readyFile)){throw 'Run devtools/chat-tunnel.ps1 -Operation Prepare first.'}
 $ready=Get-Content -LiteralPath $readyFile -Raw|ConvertFrom-Json
 $toolPath=[IO.Path]::GetFullPath([string]$ready.client)
 if(-not $toolPath.StartsWith(([IO.Path]::GetFullPath($toolsDir)+'\'),[StringComparison]::OrdinalIgnoreCase)){throw 'Tunnel client path is outside the prepared tools directory.'}
 if((Get-FileHash -LiteralPath $toolPath -Algorithm SHA256).Hash -ne $ready.client_sha256){throw 'Tunnel client changed. Prepare again.'}
 return $ready
}

if($Operation -eq 'Prepare') {
 New-Item -ItemType Directory -Path $toolsDir -Force|Out-Null
 # Use the official latest release, with the digest returned by its release API.
 $release=Invoke-RestMethod -Uri 'https://api.github.com/repos/openai/tunnel-client/releases/latest'
 if($release.tag_name -notmatch '^v[0-9]+\.[0-9]+\.[0-9]+$'){throw 'Unsupported tunnel-client release tag.'}
 $asset=@($release.assets|Where-Object name -eq "tunnel-client-$($release.tag_name)-windows-amd64.zip")
 if($asset.Count -ne 1 -or $asset[0].digest -notmatch '^sha256:[a-f0-9]{64}$'){throw 'Official release archive/digest unavailable.'}
 $assetUrl=[string]$asset[0].browser_download_url
 if(-not $assetUrl.StartsWith('https://github.com/openai/tunnel-client/releases/download/',[StringComparison]::Ordinal)){throw 'Unexpected release download source.'}
 $archive=Join-Path $toolsDir $asset[0].name
 $expected=$asset[0].digest.Substring(7)
 if(-not(Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected){
  Invoke-WebRequest -Uri $assetUrl -OutFile $archive
 }
 if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ne $expected){throw 'Official tunnel-client archive digest mismatch.'}
 # A fresh directory avoids overwriting binaries used by a running tunnel.
 $installDir=Join-Path $toolsDir ($release.tag_name+'-'+[guid]::NewGuid().ToString('N'))
 Expand-Archive -LiteralPath $archive -DestinationPath $installDir
 $client=Join-Path $installDir 'tunnel-client.exe'
 & $client --version
 if($LASTEXITCODE -ne 0){throw 'Tunnel client cannot run.'}
 Push-Location (Join-Path $root 'mcp-server')
 try {
  & npm.cmd test
  if($LASTEXITCODE -ne 0){throw 'MCP regression tests failed.'}
  & node scripts/verify-chat-entry.mjs
  if($LASTEXITCODE -ne 0){throw 'Chat stdio entry verification failed.'}
 } finally {Pop-Location}
 $node=(Get-Command node -CommandType Application).Source
 $entry=Join-Path $root 'mcp-server\scripts\chat-entry.mjs'
 if($node.Contains('"') -or $entry.Contains('"')){throw 'Unsupported quote in command path.'}
 $ready=@{client=$client;client_sha256=(Get-FileHash -LiteralPath $client -Algorithm SHA256).Hash;version=$release.tag_name;archive_sha256=$expected;mcp_command=('"'+$node+'" "'+$entry+'"');powershell=(Get-Command pwsh -CommandType Application).Source}
 [IO.File]::WriteAllText($readyFile,($ready|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
 Write-Output 'PREPARED: local MCP verified. ChatGPT registration still requires a dedicated tunnel ID and runtime API key.'
 return
}

$ready=Read-Ready
if($Operation -eq 'Status') {
 & $ready.client runtimes status $alias --json
 if($LASTEXITCODE -ne 0){throw 'No running Downfall tunnel confirmed. Connect it after account setup.'}
 return
}
if($Operation -eq 'Stop') {
 & $ready.client runtimes stop $alias --json
 if($LASTEXITCODE -ne 0){throw 'Tunnel stop failed.'}
 return
}

if(-not $TunnelId){throw 'Connect requires the dedicated Downfall tunnel ID from Platform tunnel settings.'}
if(-not $KeyFile){throw 'Connect requires -KeyFile pointing to your private runtime API key file outside this repository.'}
$keyPath=(Resolve-Path -LiteralPath $KeyFile).Path
if($keyPath.StartsWith($root+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Store the runtime API key outside this repository.'}
if((Get-Item -LiteralPath $keyPath).Length -eq 0){throw 'Runtime API key file is empty.'}
# Use native managed supervision; no custom daemon, scheduled task or game restart.
$previousPwsh=$env:COMMUNICATIONMOD_POWERSHELL
$previousInit=$env:MCP_STDIO_SEND_INITIALIZED_NOTIFICATION
try {
 $env:COMMUNICATIONMOD_POWERSHELL=$ready.powershell
 $env:MCP_STDIO_SEND_INITIALIZED_NOTIFICATION='true'
 & $ready.client runtimes connect --alias $alias --profile $alias --profile-dir $profileDir --tunnel-id $TunnelId --mcp-command $ready.mcp_command --runtime-api-key ('file:'+$keyPath) --json
 if($LASTEXITCODE -ne 0){throw 'Tunnel connection failed. Check tunnel workspace association and runtime key Read + Use permissions.'}
 & $ready.client runtimes status $alias --json
 if($LASTEXITCODE -ne 0){throw 'Unable to verify managed tunnel status.'}
} finally {
 $env:COMMUNICATIONMOD_POWERSHELL=$previousPwsh
 $env:MCP_STDIO_SEND_INITIALIZED_NOTIFICATION=$previousInit
}
