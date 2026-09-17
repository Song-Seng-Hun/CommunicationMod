<#
Offline ZIP inventory only. No Java classes are loaded or initialized.
Class names are heuristic candidates, NOT verified registered game content.
Workshop directories are reported, never recursively scanned; supply jars explicitly.
Bounds limit input file sizes, archive entry enumeration and metadata expansion.
ZIP central-directory parsing still uses the framework ZIP reader.
#>
[CmdletBinding()]
param(
    [string]$SteamRoot = 'D:\game\Steam',
    [string]$BaseGameJar,
    [string]$StandaloneRoot,
    [string]$WorkshopRoot,
    [ValidateCount(0,128)][string[]]$WorkshopJars = @(),
    [string]$ModTheSpireJar,
    [string]$BaseModJar,
    [string]$StSLibJar,
    [ValidateRange(1,2147483647)][long]$MaxJarBytes = 1073741824,
    [ValidateRange(1,1000000)][int]$MaxEntriesPerJar = 200000,
    [ValidateRange(1,1048576)][int]$MaxMetadataBytes = 65536
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$fileSystemLocation = $ExecutionContext.SessionState.Path.CurrentFileSystemLocation.ProviderPath
function Get-InventoryFullPath([string]$path) {
    # Anchor relative paths to PowerShell's location, which Push-Location changes
    # independently of the process working directory. No drive needs to exist.
    if ($path -match '^[\\/](?![\\/])') {
        $path = [IO.Path]::Combine([IO.Path]::GetPathRoot($fileSystemLocation), $path.TrimStart([char[]]'\/'))
    }
    return [IO.Path]::GetFullPath([IO.Path]::Combine($fileSystemLocation, $path))
}
$SteamRoot = Get-InventoryFullPath $SteamRoot
if (-not $BaseGameJar) { $BaseGameJar = [IO.Path]::Combine($SteamRoot, 'steamapps\common\SlayTheSpire\desktop-1.0.jar') }
if (-not $StandaloneRoot) { $StandaloneRoot = [IO.Path]::Combine($SteamRoot, 'steamapps\common\Downfall - A Slay the Spire Fan Expansion') }
if (-not $WorkshopRoot) { $WorkshopRoot = [IO.Path]::Combine($SteamRoot, 'steamapps\workshop\content\646570') }
$BaseGameJar = Get-InventoryFullPath $BaseGameJar
$StandaloneRoot = Get-InventoryFullPath $StandaloneRoot
$WorkshopRoot = Get-InventoryFullPath $WorkshopRoot
if ($ModTheSpireJar) { $ModTheSpireJar = Get-InventoryFullPath $ModTheSpireJar }
if ($BaseModJar) { $BaseModJar = Get-InventoryFullPath $BaseModJar }
if ($StSLibJar) { $StSLibJar = Get-InventoryFullPath $StSLibJar }
$categories = @('cards','relics','potions','events','characters','screens','modifiers')
function Get-PathReport([string]$path, [string]$kind) {
    $full = Get-InventoryFullPath $path
    [ordered]@{ path = $full; exists = [bool](Test-Path -LiteralPath $full -PathType $kind) }
}
function Get-JarInventory([string]$role, [string]$path) {
    $path = Get-InventoryFullPath $path
    $candidates = [ordered]@{}
    foreach ($category in $categories) { $candidates[$category] = @() }
    $result = [ordered]@{
        role = $role; path = $path; status = 'missing'; sha256 = $null
        archiveStatus = 'missing'; metadataStatus = 'absent'; modTheSpireMetadata = $null
        candidates = $candidates; candidateContentStatus = 'unverified-not-registered'
    }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $result }
    $result.status = 'unverified'
    $stream = $null
    $archive = $null
    try {
        $stream = [IO.File]::Open($path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::Read)
        if ($stream.Length -gt $MaxJarBytes) {
            $result.archiveStatus = 'limit-exceeded'
            return $result
        }
        $hash = [Security.Cryptography.SHA256]::Create()
        try { $result.sha256 = [BitConverter]::ToString($hash.ComputeHash($stream)).Replace('-', '').ToLowerInvariant() }
        finally { $hash.Dispose() }
        $stream.Position = 0
        try {
            $archive = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Read, $true)
            if ($archive.Entries.Count -gt $MaxEntriesPerJar) {
                $result.archiveStatus = 'limit-exceeded'
                return $result
            }
            $sets = @{}
            foreach ($category in $categories) { $sets[$category] = [Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal) }
            $metadataEntries = @()
            foreach ($entry in $archive.Entries) {
                if ($entry.FullName -ceq 'ModTheSpire.json') { $metadataEntries += $entry }
                # Only conventional package segments; no bytecode/superclass inference.
                if ($entry.FullName -cmatch '^(?:[A-Za-z_$][A-Za-z0-9_$]*/)*[A-Za-z_$][A-Za-z0-9_$]*\.class$') {
                    $segments = $entry.FullName.Split('/')
                    $packageSegments = @()
                    if ($segments.Length -gt 1) { $packageSegments = $segments[0..($segments.Length - 2)] }
                    $name = $entry.FullName.Substring(0, $entry.FullName.Length - 6).Replace('/', '.')
                    foreach ($category in $categories) {
                        if ($packageSegments -ccontains $category) { [void]$sets[$category].Add($name) }
                    }
                }
            }
            foreach ($category in $categories) { $candidates[$category] = @($sets[$category]) }
            $result.archiveStatus = 'readable'
            if ($metadataEntries.Count -gt 1) { $result.metadataStatus = 'ambiguous' }
            elseif ($metadataEntries.Count -eq 1) {
                $entry = $metadataEntries[0]
                if ($entry.Length -gt $MaxMetadataBytes) { $result.metadataStatus = 'limit-exceeded' }
                else {
                    $metadataStream = $null
                    try {
                        $metadataStream = $entry.Open()
                        $buffer = New-Object byte[] ($MaxMetadataBytes + 1)
                        $count = 0
                        while ($count -lt $buffer.Length) {
                            $read = $metadataStream.Read($buffer, $count, $buffer.Length - $count)
                            if ($read -eq 0) { break }
                            $count += $read
                        }
                        if ($count -gt $MaxMetadataBytes) { $result.metadataStatus = 'limit-exceeded' }
                        else {
                            $json = [Text.Encoding]::UTF8.GetString($buffer, 0, $count).TrimStart([char]0xFEFF)
                            $metadata = ConvertFrom-Json -InputObject $json -ErrorAction Stop
                            if ($null -eq $metadata -or $metadata -isnot [pscustomobject]) { throw 'Metadata must be a JSON object' }
                            $result.modTheSpireMetadata = $metadata
                            $result.metadataStatus = 'readable'
                        }
                    } catch { $result.metadataStatus = 'invalid' }
                    finally { if ($null -ne $metadataStream) { $metadataStream.Dispose() } }
                }
            }
        } catch { $result.archiveStatus = 'corrupt' }
    } catch { $result.archiveStatus = 'unreadable' }
    finally {
        if ($null -ne $archive) { $archive.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
    }
    return $result
}
$requests = @(
    @{ role = 'base-game'; path = $BaseGameJar }
    @{ role = 'standalone-game'; path = ([IO.Path]::Combine($StandaloneRoot, 'desktop-1.0-modded.jar')) }
    @{ role = 'standalone-basemod'; path = ([IO.Path]::Combine($StandaloneRoot, 'package\BaseMod-modded.jar')) }
    @{ role = 'standalone-stslib'; path = ([IO.Path]::Combine($StandaloneRoot, 'package\StSLib-modded.jar')) }
    @{ role = 'standalone-evilwithin'; path = ([IO.Path]::Combine($StandaloneRoot, 'package\EvilWithin-modded.jar')) }
)
if ($ModTheSpireJar) { $requests += @{ role = 'normal-modthespire'; path = $ModTheSpireJar } }
if ($BaseModJar) { $requests += @{ role = 'normal-basemod'; path = $BaseModJar } }
if ($StSLibJar) { $requests += @{ role = 'normal-stslib'; path = $StSLibJar } }
$workshopPaths = [Collections.Generic.SortedSet[string]]::new([StringComparer]::Ordinal)
foreach ($path in $WorkshopJars) { [void]$workshopPaths.Add((Get-InventoryFullPath $path)) }
foreach ($path in $workshopPaths) { $requests += @{ role = 'workshop'; path = $path } }
$jars = @($requests | ForEach-Object { Get-JarInventory -role $_.role -path $_.path })
$status = 'unverified'
if (@($jars | Where-Object { $_.status -eq 'missing' }).Count -gt 0) { $status = 'missing' }
[ordered]@{
    schemaVersion = 1
    runtimeStatus = $status
    steamSubmissionSafety = 'unverified'
    steamCloudSafety = 'unverified'
    candidateContentStatus = 'unverified-not-registered'
    candidateNote = 'Package-name candidates only; not verified registered content. No game classes loaded. ZIP readability does not verify gameplay or archive payload integrity.'
    limits = [ordered]@{ maxJarBytes = $MaxJarBytes; maxEntriesPerJar = $MaxEntriesPerJar; maxMetadataBytes = $MaxMetadataBytes; maxWorkshopJars = 128 }
    paths = [ordered]@{
        base = (Get-PathReport $BaseGameJar 'Leaf')
        standalone = (Get-PathReport $StandaloneRoot 'Container')
        workshop = (Get-PathReport $WorkshopRoot 'Container')
    }
    normalPrerequisites = [ordered]@{
        modTheSpire = $(if ($ModTheSpireJar -and (Test-Path -LiteralPath $ModTheSpireJar -PathType Leaf)) { 'unverified' } else { 'missing' })
        baseMod = $(if ($BaseModJar -and (Test-Path -LiteralPath $BaseModJar -PathType Leaf)) { 'unverified' } else { 'missing' })
        stSLib = $(if ($StSLibJar -and (Test-Path -LiteralPath $StSLibJar -PathType Leaf)) { 'unverified' } else { 'missing' })
    }
    jars = $jars
} | ConvertTo-Json -Depth 64
