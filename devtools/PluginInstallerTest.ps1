param([string]$InstallerPath=(Join-Path $PSScriptRoot 'install-codex-plugin.ps1'),[switch]$SuccessOnly)
$ErrorActionPreference='Stop'
# Regression intent: removed helper dependency, package/source boundary errors, and stale overlay files.
# All cases use disposable profiles/packages and a function mock; no real install, settings or gameplay.
$root=Split-Path -Parent $PSScriptRoot
$out=Join-Path $root ('target\plugin-installer-tests\run-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $out -Force|Out-Null
Start-Transcript -Path (Join-Path $out 'verification.log')|Out-Null
$priorProfile=$env:USERPROFILE
$global:PluginInstallerMockCalls=0
$global:PluginInstallerMockExit=0
function Invoke-PluginInstallerMock {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments)
    if(($Arguments -join '|') -cne 'plugin|add|downfall-agent@personal|--json'){throw 'Unexpected Codex install command'}
    $global:PluginInstallerMockCalls++
    $global:LASTEXITCODE=$global:PluginInstallerMockExit
}
function Write-Fixture([string]$Path,[string]$Text) {
    New-Item -ItemType Directory -Path (Split-Path -Parent $Path) -Force|Out-Null
    [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))
}
function New-Case([string]$Name) {
    $caseRoot=Join-Path $out $Name
    $workspace=Join-Path $caseRoot 'workspace'
    $profile=Join-Path $caseRoot 'profile'
    $package=Join-Path $workspace 'target\package\downfall-agent'
    New-Item -ItemType Directory -Path "$workspace\devtools","$package\server\node_modules" -Force|Out-Null
    Copy-Item -LiteralPath $InstallerPath -Destination "$workspace\devtools\install-codex-plugin.ps1"
    Write-Fixture "$profile\plugins\downfall-agent\obsolete.js" 'original source bytes'
    Write-Fixture "$profile\.communicationmod\plugin.json" '{"original":"settings bytes"}'
    Write-Fixture "$profile\.agents\plugins\marketplace.json" '{"name":"personal","plugins":[{"name":"downfall-agent","source":{"source":"local","path":"./plugins/downfall-agent"}}]}'
    Write-Fixture "$workspace\target\plugin-package-ready.json" (@{path=$package}|ConvertTo-Json)
    Write-Fixture "$package\.codex-plugin\plugin.json" '{"name":"downfall-agent","version":"0.1.0+old.cache","description":"fixture","skills":"./skills/","mcpServers":"./.mcp.json"}'
    Write-Fixture "$package\.mcp.json" '{"mcpServers":{"communicationmod":{"command":"node","args":["${CLAUDE_PLUGIN_ROOT}/scripts/start.mjs"]}}}'
    foreach($file in @('scripts\start.mjs','scripts\manage-plugin-game.ps1','server\index.js','server\guidance-bundle.json','server\package.json','server\package-lock.json')){Write-Fixture (Join-Path $package $file) 'fixture'}
    Write-Fixture "$package\skills\downfall-play\SKILL.md" "---`r`nname: downfall-play`r`ndescription: Launch the configured game.`r`n---`r`n# Play`r`nUser-authorized gameplay only.`r`n"
    return @{root=$caseRoot;workspace=$workspace;profile=$profile;package=$package;installer="$workspace\devtools\install-codex-plugin.ps1"}
}
function Install-Case($Case,[int]$Exit=0) {
    $env:USERPROFILE=$Case.profile
    $global:PluginInstallerMockCalls=0
    $global:PluginInstallerMockExit=$Exit
    & $Case.installer -UsePreparedPackage -CodexCommand Invoke-PluginInstallerMock
}
function Assert([bool]$Condition,[string]$Message){if(-not $Condition){throw $Message}}
function Reject-Case($Case,[string]$Expected) {
    try{Install-Case $Case;throw 'Invalid fixture accepted'}catch{if($_.Exception.Message -notmatch $Expected){throw}}
    Assert ($global:PluginInstallerMockCalls -eq 0) 'Validation failure called Codex'
    Assert ((Get-Content -LiteralPath "$($Case.profile)\plugins\downfall-agent\obsolete.js" -Raw) -ceq 'original source bytes') 'Validation failure changed source'
    Assert ((Get-Content -LiteralPath "$($Case.profile)\.communicationmod\plugin.json" -Raw) -ceq '{"original":"settings bytes"}') 'Validation failure changed settings'
    Assert (@(Get-ChildItem -LiteralPath "$($Case.workspace)\target" -Filter 'plugin-*-backup-*').Count -eq 0) 'Validation failure created backups'
}
try {
    if(-not $SuccessOnly) {
        $case=New-Case 'missing-package'
        Write-Fixture "$($case.workspace)\target\plugin-package-ready.json" (@{path="$($case.workspace)\target\absent"}|ConvertTo-Json)
        Reject-Case $case 'Missing prepared plugin package'
        $case=New-Case 'market-source-mismatch'
        Write-Fixture "$($case.profile)\.agents\plugins\marketplace.json" '{"name":"personal","plugins":[{"name":"downfall-agent","source":{"source":"local","path":"./plugins/other"}}]}'
        Reject-Case $case 'Personal plugin source mismatch'
        $case=New-Case 'outside-package'
        Write-Fixture "$($case.workspace)\target\plugin-package-ready.json" (@{path=$case.profile}|ConvertTo-Json)
        Reject-Case $case 'Path outside approved root'
        $case=New-Case 'manifest-mismatch'
        Write-Fixture "$($case.package)\.codex-plugin\plugin.json" '{"name":"other","version":"0.1.0","skills":"./skills/","mcpServers":"./.mcp.json"}'
        Reject-Case $case 'Plugin manifest path/name mismatch'
        $case=New-Case 'invalid-version'
        Write-Fixture "$($case.package)\.codex-plugin\plugin.json" '{"name":"downfall-agent","version":"0.1.0-01","skills":"./skills/","mcpServers":"./.mcp.json"}'
        Reject-Case $case 'Invalid plugin semantic version'
        $case=New-Case 'invalid-skill'
        Write-Fixture "$($case.package)\skills\downfall-play\SKILL.md" '# Missing frontmatter'
        Reject-Case $case 'Invalid downfall-play skill'
        Assert (((Get-Content -LiteralPath "$($case.package)\.codex-plugin\plugin.json" -Raw|ConvertFrom-Json).version) -ceq '0.1.0+old.cache') 'Validation failure stamped version'
    }
    $case=New-Case 'success'
    Install-Case $case
    Assert ($global:PluginInstallerMockCalls -eq 1) 'Install command was not called once'
    $source="$($case.profile)\plugins\downfall-agent"
    Assert (-not (Test-Path -LiteralPath "$source\obsolete.js")) 'Replacement retained stale legacy file'
    $installed=Get-Content -LiteralPath "$source\.codex-plugin\plugin.json" -Raw|ConvertFrom-Json
    Assert ($installed.version -cmatch '^0\.1\.0\+codex\.[0-9]{14}\.[a-f0-9]{8}$') 'Invalid or nonunique cache version stamp'
    Assert ((Get-Content -LiteralPath "$source\.codex-plugin\plugin.json" -Raw) -ceq (Get-Content -LiteralPath "$($case.package)\.codex-plugin\plugin.json" -Raw)) 'Installed manifest differs from stamped package'
    $backups=@(Get-ChildItem -LiteralPath "$($case.workspace)\target" -Directory -Filter 'plugin-source-backup-*')
    Assert ($backups.Count -eq 1 -and (Get-Content -LiteralPath "$($backups[0].FullName)\obsolete.js" -Raw) -ceq 'original source bytes') 'Source backup lost original bytes'
    $settingsBackups=@(Get-ChildItem -LiteralPath "$($case.workspace)\target" -File -Filter 'plugin-settings-backup-*')
    Assert ($settingsBackups.Count -eq 1 -and (Get-Content -LiteralPath $settingsBackups[0].FullName -Raw) -ceq '{"original":"settings bytes"}') 'Settings backup lost original bytes'
    $settings=Get-Content -LiteralPath "$($case.profile)\.communicationmod\plugin.json" -Raw|ConvertFrom-Json
    Assert ($settings.workspace -eq $case.workspace -and $settings.powershell -eq (Get-Command pwsh).Source) 'Installed workspace settings changed contract'
    Assert (@(Get-ChildItem -LiteralPath "$($case.profile)\plugins" -Directory -Filter 'downfall-agent-*').Count -eq 0) 'Temporary replacement directories retained'
    if(-not $SuccessOnly) {
        $case=New-Case 'codex-failure'
        try{Install-Case $case 17;throw 'Failed Codex installation accepted'}catch{if($_.Exception.Message -notmatch 'Plugin installation failed'){throw}}
        Assert ($global:PluginInstallerMockCalls -eq 1) 'Codex failure not exercised'
        Assert (@(Get-ChildItem -LiteralPath "$($case.workspace)\target" -Filter 'plugin-*-backup-*').Count -eq 2) 'Codex failure did not retain both backups'
    }
    Write-Host "PASS: isolated installer validation, version stamp, backups, stale replacement and CLI failure. Log: $out\verification.log"
} finally {
    $env:USERPROFILE=$priorProfile
    Remove-Item Function:\Invoke-PluginInstallerMock -ErrorAction SilentlyContinue
    Remove-Variable PluginInstallerMockCalls,PluginInstallerMockExit -Scope Global -ErrorAction SilentlyContinue
    $global:LASTEXITCODE=0
    Stop-Transcript|Out-Null
}
