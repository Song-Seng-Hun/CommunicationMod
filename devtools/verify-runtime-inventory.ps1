$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$inventory = Join-Path $PSScriptRoot 'runtime-inventory.ps1'
function Assert($condition, [string]$message) {
    if (-not $condition) { throw "FAIL: $message" }
}
Assert (Test-Path -LiteralPath $inventory) 'runtime inventory implementation exists'
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$target = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\target'))
$fixture = Join-Path $target ('runtime-inventory-test-' + [Guid]::NewGuid().ToString('N'))
function New-FakeJar([string]$path, $entries) {
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($path)) | Out-Null
    $zip = [IO.Compression.ZipFile]::Open($path, 'Create')
    try {
        foreach ($name in $entries.Keys) {
            $entry = $zip.CreateEntry($name)
            $writer = New-Object IO.StreamWriter($entry.Open())
            try { $writer.Write([string]$entries[$name]) } finally { $writer.Dispose() }
        }
    } finally { $zip.Dispose() }
}
try {
    [IO.Directory]::CreateDirectory($fixture) | Out-Null
    $missing = (& $inventory -SteamRoot $fixture) | ConvertFrom-Json
    Assert ($missing.runtimeStatus -eq 'missing') 'missing runtime'
    Assert ($missing.jars.Count -eq 5) 'five default prerequisite jars'
    Assert (@($missing.jars | Where-Object status -ne 'missing').Count -eq 0) 'missing jar paths reported'
    Assert ($missing.paths.workshop.path -eq (Join-Path $fixture 'steamapps\workshop\content\646570')) 'workshop default path'
    Assert (-not $missing.paths.workshop.exists) 'missing workshop directory'
    foreach ($jar in $missing.jars) {
        New-FakeJar $jar.path ([ordered]@{ 'irrelevant.txt' = 'fixture' })
    }
    $a = Join-Path $fixture 'a.jar'
    $z = Join-Path $fixture 'z.jar'
    $bad = Join-Path $fixture 'corrupt.jar'
    New-FakeJar $a ([ordered]@{
        'demo/cards/Zeta.class' = 'not executable bytecode'
        'demo/cards/Alpha.class' = ''
        'demo/cards/Alpha$Inner.class' = ''
        'demo/relics/Relic.class' = ''
        'demo/potions/Potion.class' = ''
        'demo/events/Event.class' = ''
        'demo/characters/Character.class' = ''
        'demo/screens/Screen.class' = ''
        'demo/modifiers/Modifier.class' = ''
        'ModTheSpire.json' = '{"modid":"fixture","name":"Fixture"}'
    })
    New-FakeJar $z ([ordered]@{ 'nested/ModTheSpire.json' = '{"modid":"ignored"}' })
    [IO.File]::WriteAllText($bad, 'invalid ZIP')
    $argsForInventory = @{ SteamRoot = $fixture; WorkshopJars = @($z, $bad, $a); ModTheSpireJar = $z; BaseModJar = $a; StSLibJar = $z }
    $raw = & $inventory @argsForInventory
    $report = $raw | ConvertFrom-Json
    Assert ($report.runtimeStatus -eq 'unverified') 'existing jars never imply readiness'
    Assert ($report.steamSubmissionSafety -eq 'unverified' -and $report.steamCloudSafety -eq 'unverified') 'Steam safety remains unverified'
    Assert ($report.candidateContentStatus -eq 'unverified-not-registered') 'candidate disclaimer'
    $item = @($report.jars | Where-Object { $_.path -eq $a })[0]
    Assert ($item.sha256 -eq (Get-FileHash -LiteralPath $a -Algorithm SHA256).Hash.ToLowerInvariant()) 'SHA256 matches file bytes'
    Assert ($item.modTheSpireMetadata.modid -eq 'fixture') 'root metadata read'
    Assert (($item.candidates.cards -join ',') -ceq 'demo.cards.Alpha,demo.cards.Alpha$Inner,demo.cards.Zeta') 'ordinal sorted class candidates'
    foreach ($category in 'relics','potions','events','characters','screens','modifiers') {
        Assert ($item.candidates.$category.Count -eq 1) "$category candidate"
    }
    $corrupt = @($report.jars | Where-Object { $_.path -eq $bad })[0]
    Assert ($corrupt.archiveStatus -eq 'corrupt' -and $corrupt.sha256.Length -eq 64) 'corrupt archives reported and hashed'
    $nested = @($report.jars | Where-Object { $_.path -eq $z })[0]
    Assert ($null -eq $nested.modTheSpireMetadata) 'nested metadata ignored'
    Assert ($nested.candidates.cards -is [Array] -and $nested.candidates.cards.Count -eq 0) 'empty candidates remain arrays'
    $argsForInventory.WorkshopJars = @($a, $z, $bad)
    Assert (($raw -join "`n") -ceq ((& $inventory @argsForInventory) -join "`n")) 'repeat output and input ordering deterministic'
    $bounded = (& $inventory -SteamRoot $fixture -WorkshopJars @($a) -MaxEntriesPerJar 2) | ConvertFrom-Json
    $limited = @($bounded.jars | Where-Object { $_.path -eq $a })[0]
    Assert ($limited.archiveStatus -eq 'limit-exceeded') 'entry inventory bound reported'
    $bounded = (& $inventory -SteamRoot $fixture -WorkshopJars @($a) -MaxMetadataBytes 2) | ConvertFrom-Json
    $limited = @($bounded.jars | Where-Object { $_.path -eq $a })[0]
    Assert ($limited.metadataStatus -eq 'limit-exceeded') 'metadata decompression bounded'
    $bounded = (& $inventory -SteamRoot $fixture -WorkshopJars @($a) -MaxJarBytes 1) | ConvertFrom-Json
    Assert (@($bounded.jars | Where-Object archiveStatus -ne 'limit-exceeded').Count -eq 0) 'file size bound reported'
    $invalid = Join-Path $fixture 'invalid-metadata.jar'
    New-FakeJar $invalid ([ordered]@{ 'ModTheSpire.json' = '{broken'; 'demo/cards/StillCandidate.class' = '' })
    $invalidReport = (& $inventory -SteamRoot $fixture -WorkshopJars @($invalid)) | ConvertFrom-Json
    $invalidItem = @($invalidReport.jars | Where-Object { $_.path -eq $invalid })[0]
    Assert ($invalidItem.metadataStatus -eq 'invalid' -and $invalidItem.candidates.cards.Count -eq 1) 'invalid metadata preserves candidates'
    $optionalMissing = (& $inventory -SteamRoot $fixture -ModTheSpireJar (Join-Path $fixture 'missing-mts.jar')) | ConvertFrom-Json
    Assert ($optionalMissing.normalPrerequisites.modTheSpire -eq 'missing') 'explicit missing normal prerequisite'
    Assert ((Get-FileHash -LiteralPath $a -Algorithm SHA256).Hash.ToLowerInvariant() -eq $item.sha256) 'source archive unchanged'
    $pathFailures = @()
    Push-Location -LiteralPath $fixture
    try {
        $relative = (& $inventory -SteamRoot '.' -WorkshopJars @('a.jar','z.jar') -ModTheSpireJar 'z.jar' -BaseModJar 'a.jar' -StSLibJar 'z.jar') | ConvertFrom-Json
        Assert ($relative.runtimeStatus -eq 'unverified') 'relative Steam root uses Push-Location'
        foreach ($role in 'normal-modthespire','normal-basemod','normal-stslib','workshop') {
            foreach ($jar in @($relative.jars | Where-Object role -eq $role)) {
                Assert ($jar.status -eq 'unverified' -and $jar.archiveStatus -eq 'readable') "relative $role jar is readable"
                Assert ($jar.path.StartsWith($fixture + '\', [StringComparison]::OrdinalIgnoreCase)) "relative $role path uses PowerShell location"
            }
        }
        Assert ($relative.normalPrerequisites.modTheSpire -eq 'unverified') 'relative normal summary agrees with jar status'
        $explicit = (& $inventory -SteamRoot '.' -BaseGameJar 'a.jar' -StandaloneRoot 'steamapps\common\Downfall - A Slay the Spire Fan Expansion' -WorkshopRoot '.') | ConvertFrom-Json
        Assert ($explicit.paths.base.path -eq $a -and $explicit.paths.base.exists) 'relative explicit base path'
        Assert ($explicit.paths.workshop.path -eq $fixture -and $explicit.paths.workshop.exists) 'relative explicit workshop root'
        Assert ($explicit.runtimeStatus -eq 'unverified') 'relative explicit standalone root'
    } catch { $pathFailures += $_.Exception.Message }
    finally { Pop-Location }
    try {
        # Pick an absent drive; never create files outside target.
        $absentRoot = $null
        foreach ($letter in 'ZYXWVUTSRQPONMLKJIHGFED'.ToCharArray()) {
            if (-not (Get-PSDrive -Name ([string]$letter) -ErrorAction SilentlyContinue) -and -not [IO.Directory]::Exists("${letter}:\")) {
                $absentRoot = "${letter}:\nonexistent"
                break
            }
        }
        Assert ($null -ne $absentRoot) 'an absent drive is available for regression'
        $absent = (& $inventory -SteamRoot $absentRoot) | ConvertFrom-Json
        Assert ($absent.runtimeStatus -eq 'missing' -and $absent.jars.Count -eq 5) 'absent drive returns JSON missing inventory'
        foreach ($jar in $absent.jars) {
            Assert ($jar.status -eq 'missing' -and $jar.path.StartsWith($absentRoot, [StringComparison]::Ordinal)) 'absent drive jar path preserved'
        }
        Assert (-not $absent.paths.base.exists -and -not $absent.paths.standalone.exists -and -not $absent.paths.workshop.exists) 'absent drive prerequisite paths missing'
    } catch { $pathFailures += $_.Exception.Message }
    Assert ($pathFailures.Count -eq 0) ($pathFailures -join '; ')
    Write-Output 'PASS: missing prerequisites, SHA256, root metadata, seven candidate categories, corrupt ZIP, deterministic arrays/output, bounds, unverified safety, Push-Location relative paths, absent drive.'
} finally {
    $resolved = [IO.Path]::GetFullPath($fixture)
    if (-not $resolved.StartsWith($target + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe fixture cleanup path' }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
