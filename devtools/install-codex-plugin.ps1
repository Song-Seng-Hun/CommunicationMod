param([string]$CodexCommand='codex', [switch]$UsePreparedPackage)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$profileRoot=[IO.Path]::GetFullPath($env:USERPROFILE)
$source=Join-Path $profileRoot 'plugins\downfall-agent'
$market=Join-Path $profileRoot '.agents\plugins\marketplace.json'
$settings=Join-Path $profileRoot '.communicationmod\plugin.json'

function Assert-LocalPath([string]$Path,[string]$Boundary) {
    $full=[IO.Path]::GetFullPath($Path)
    $base=[IO.Path]::GetFullPath($Boundary).TrimEnd('\')
    if($full -ne $base -and -not $full.StartsWith($base+'\',[StringComparison]::OrdinalIgnoreCase)){throw 'Path outside approved root'}
    for($cursor=$full;$cursor.Length -ge $base.Length;$cursor=Split-Path -Parent $cursor) {
        if(Test-Path -LiteralPath $cursor) {
            if((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Linked plugin path is unsupported'}
        }
        if($cursor -eq $base){break}
    }
    return $full
}
function Assert-NoLinkedContents([string]$Path) {
    if(Get-ChildItem -LiteralPath $Path -Force -Recurse | Where-Object {$_.Attributes -band [IO.FileAttributes]::ReparsePoint}){throw 'Linked plugin contents are unsupported'}
}
function Read-Json([string]$Path) {
    if(-not (Test-Path -LiteralPath $Path -PathType Leaf)){throw "Missing plugin file: $Path"}
    return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
}

# The marketplace must already be configured by the user. Never scaffold or redirect it.
[void](Assert-LocalPath $market $profileRoot)
[void](Assert-LocalPath $source $profileRoot)
[void](Assert-LocalPath $settings $profileRoot)
$marketData=Read-Json $market
if($marketData.name -cne 'personal'){throw 'Invalid personal marketplace name'}
$entry=@($marketData.plugins | Where-Object name -eq 'downfall-agent')
if($entry.Count -ne 1 -or $entry[0].name -cne 'downfall-agent' -or $entry[0].source.source -cne 'local' -or $entry[0].source.path -cne './plugins/downfall-agent'){throw 'Personal plugin source mismatch'}
if((Test-Path -LiteralPath $settings) -and -not (Test-Path -LiteralPath $settings -PathType Leaf)){throw 'Plugin settings is not a file'}
if(Test-Path -LiteralPath $source){
    if(-not (Test-Path -LiteralPath $source -PathType Container)){throw 'Plugin source is not a directory'}
    Assert-NoLinkedContents $source
}
$pwsh=(Get-Command pwsh -ErrorAction Stop).Source
[void](Get-Command $CodexCommand -ErrorAction Stop)
if(-not $UsePreparedPackage){ & "$PSScriptRoot\package-codex-plugin.ps1" }
$ready=Read-Json (Join-Path $root 'target\plugin-package-ready.json')
if($ready.path -isnot [string] -or -not [IO.Path]::IsPathRooted($ready.path)){throw 'Invalid prepared package path'}
$targetRoot=[IO.Path]::GetFullPath((Join-Path $root 'target'))
$package=Assert-LocalPath $ready.path $targetRoot
if($package -eq $targetRoot -or -not (Test-Path -LiteralPath $package -PathType Container)){throw 'Missing prepared plugin package'}
Assert-NoLinkedContents $package
$manifestPath=Join-Path $package '.codex-plugin\plugin.json'
$manifest=Read-Json $manifestPath
if($manifest.name -cne 'downfall-agent' -or $manifest.skills -cne './skills/' -or $manifest.mcpServers -cne './.mcp.json'){throw 'Plugin manifest path/name mismatch'}
$releaseIdentifier='(?:0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)'
$versionPattern='^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-'+$releaseIdentifier+'(?:\.'+$releaseIdentifier+')*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?$'
if($manifest.version -isnot [string] -or $manifest.version -notmatch $versionPattern){throw 'Invalid plugin semantic version'}
$mcp=Read-Json (Join-Path $package '.mcp.json')
$server=$mcp.mcpServers.communicationmod
if($server.command -cne 'node' -or @($server.args).Count -ne 1 -or $server.args[0] -cne '${CLAUDE_PLUGIN_ROOT}/scripts/start.mjs'){throw 'Plugin MCP entrypoint mismatch'}
foreach($file in @('scripts\start.mjs','scripts\manage-plugin-game.ps1','server\index.js','server\guidance-bundle.json','server\package.json','server\package-lock.json','skills\downfall-play\SKILL.md')) {
    if(-not (Test-Path -LiteralPath (Join-Path $package $file) -PathType Leaf)){throw "Missing plugin package file: $file"}
}
if(-not (Test-Path -LiteralPath (Join-Path $package 'server\node_modules') -PathType Container)){throw 'Missing packaged server dependencies'}
$skill=Get-Content -LiteralPath (Join-Path $package 'skills\downfall-play\SKILL.md') -Raw
$front=[regex]::Match($skill,'\A---\r?\n(?<fields>.*?)\r?\n---\r?\n(?<body>.+)\z',[Text.RegularExpressions.RegexOptions]::Singleline)
if(-not $front.Success -or $front.Groups['fields'].Value -notmatch '(?m)^name:\s*downfall-play\s*$' -or $front.Groups['fields'].Value -notmatch '(?m)^description:[ \t]*\S[^\r\n]*\r?$' -or $front.Groups['fields'].Value -match '\[TODO:' -or [string]::IsNullOrWhiteSpace($front.Groups['body'].Value)){throw 'Invalid downfall-play skill'}

# All validation precedes package/source/settings mutation. A unique build suffix refreshes the cache.
$manifest.version=($manifest.version -replace '\+.*$','')+'+codex.'+(Get-Date -Format 'yyyyMMddHHmmss')+'.'+[guid]::NewGuid().ToString('N').Substring(0,8)
[IO.File]::WriteAllText($manifestPath,($manifest | ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
$sourceParent=Split-Path -Parent $source
New-Item -ItemType Directory -Path $sourceParent -Force|Out-Null
$staged=Join-Path $sourceParent ('downfall-agent-new-'+[guid]::NewGuid().ToString('N'))
[void](Assert-LocalPath $staged $sourceParent)
Copy-Item -LiteralPath $package -Destination $staged -Recurse -Force
$retired=$null
if(Test-Path -LiteralPath $source) {
    $backup=Join-Path $root ('target\plugin-source-backup-'+[guid]::NewGuid().ToString('N'))
    Copy-Item -LiteralPath $source -Destination $backup -Recurse -Force
    $retired=Join-Path $sourceParent ('downfall-agent-old-'+[guid]::NewGuid().ToString('N'))
    [void](Assert-LocalPath $retired $sourceParent)
    Move-Item -LiteralPath $source -Destination $retired
}
try { Move-Item -LiteralPath $staged -Destination $source }
catch { if($retired){Move-Item -LiteralPath $retired -Destination $source}; throw }
if($retired){[void](Assert-LocalPath $retired $sourceParent);Remove-Item -LiteralPath $retired -Recurse -Force}
New-Item -ItemType Directory -Path (Split-Path -Parent $settings) -Force|Out-Null
if(Test-Path -LiteralPath $settings){Copy-Item -LiteralPath $settings -Destination "$root\target\plugin-settings-backup-$([guid]::NewGuid().ToString('N')).json"}
$values=@{workspace=$root;powershell=$pwsh}
[IO.File]::WriteAllText($settings,($values|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
& $CodexCommand plugin add 'downfall-agent@personal' --json
if($LASTEXITCODE -ne 0){throw 'Plugin installation failed; previous source/settings backups retained'}
Write-Host 'INSTALLED: downfall-agent. Verify cached MCP tools before removing legacy communicationmod registration.'
