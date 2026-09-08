param([string]$CodexCommand='codex', [switch]$UsePreparedPackage)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$skillRoot=Join-Path $env:USERPROFILE '.codex\skills\.system\plugin-creator'
$source=Join-Path $env:USERPROFILE 'plugins\downfall-agent'
$market=Join-Path $env:USERPROFILE '.agents\plugins\marketplace.json'
if(-not $UsePreparedPackage){ & "$PSScriptRoot\package-codex-plugin.ps1" }
$package=(Get-Content "$root\target\plugin-package-ready.json" -Raw | ConvertFrom-Json).path
$targetRoot=[IO.Path]::GetFullPath((Join-Path $root 'target'))+'\'
if(-not ([IO.Path]::GetFullPath($package)).StartsWith($targetRoot,[StringComparison]::OrdinalIgnoreCase)){throw 'Package outside workspace target'}
if(-not (Test-Path $market)) {
 & python "$skillRoot\scripts\create_basic_plugin.py" downfall-agent --with-skills --with-scripts --with-mcp --with-marketplace
 if($LASTEXITCODE -ne 0){throw 'Personal plugin scaffold failed'}
}
$marketName=(& python "$skillRoot\scripts\read_marketplace_name.py").Trim()
if($LASTEXITCODE -ne 0){throw 'Invalid personal marketplace'}
$entry=@((Get-Content $market -Raw|ConvertFrom-Json).plugins|Where-Object name -eq 'downfall-agent')
if($entry.Count -ne 1 -or $entry[0].source.path -ne './plugins/downfall-agent'){throw 'Personal plugin source mismatch'}
if(Test-Path $source) {
 $backup=Join-Path $root ('target\plugin-source-backup-'+[guid]::NewGuid().ToString('N'))
 Copy-Item -LiteralPath $source -Destination $backup -Recurse
}
& python "$skillRoot\scripts\update_plugin_cachebuster.py" $package
if($LASTEXITCODE -ne 0){throw 'Plugin version validation failed'}
& python "$skillRoot\scripts\validate_plugin.py" $package
if($LASTEXITCODE -ne 0){throw 'Plugin manifest validation failed'}
& python "$env:USERPROFILE\.codex\skills\.system\skill-creator\scripts\quick_validate.py" "$package\skills\downfall-play"
if($LASTEXITCODE -ne 0){throw 'Gameplay skill validation failed'}
New-Item -ItemType Directory -Path $source -Force|Out-Null
Get-ChildItem -LiteralPath $package -Force|ForEach-Object{Copy-Item -LiteralPath $_.FullName -Destination $source -Recurse -Force}
$settings=Join-Path $env:USERPROFILE '.communicationmod\plugin.json'
New-Item -ItemType Directory -Path (Split-Path $settings) -Force|Out-Null
if(Test-Path $settings){Copy-Item -LiteralPath $settings -Destination "$root\target\plugin-settings-backup-$([guid]::NewGuid().ToString('N')).json"}
$values=@{workspace=$root;powershell=(Get-Command pwsh).Source}
[IO.File]::WriteAllText($settings,($values|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
& $CodexCommand plugin add "downfall-agent@$marketName" --json
if($LASTEXITCODE -ne 0){throw 'Plugin installation failed; previous cache/settings backups retained'}
Write-Host 'INSTALLED: downfall-agent. Verify cached MCP tools before removing legacy communicationmod registration.'
