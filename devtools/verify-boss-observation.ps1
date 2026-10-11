param([string]$DownfallPath='D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',[switch]$BindingOnly)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
$out=Join-Path $root ('target\boss-observation-tests\run-'+[guid]::NewGuid().ToString('N'))
$compiled=Join-Path $out 'binding'
New-Item -ItemType Directory -Force $compiled | Out-Null
Start-Transcript -Path (Join-Path $out 'verification.log') | Out-Null
try {
$game=Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$evil=Join-Path $DownfallPath 'package\EvilWithin-modded.jar'
$base=Join-Path $DownfallPath 'package\BaseMod-modded.jar'
$gson=Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'
$gsonClasses=Join-Path $out 'gson'
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::ExtractToDirectory($gson,$gsonClasses)
$sources=Get-ChildItem -LiteralPath (Join-Path $root 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName
& javac '-J-Xmx384m' --release 8 -encoding UTF-8 -cp "$gsonClasses;$game;$base;$evil" -d $compiled @sources 2>&1 | Out-Host
if($LASTEXITCODE -ne 0){throw 'Fresh production compilation failed'}
$tests=@("$PSScriptRoot\BossObservationBindingTest.java")
if(-not $BindingOnly){$tests+="$PSScriptRoot\BossObservationTest.java"}
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp $game -d $out @tests 2>&1 | Out-Host
if($LASTEXITCODE -ne 0){throw 'Boss test compilation failed'}
& java '-Xmx256m' '-Dfile.encoding=UTF-8' -cp "$out;$game" BossObservationBindingTest $compiled $evil $game $base 2>&1 | Out-Host
if($LASTEXITCODE -ne 0){throw 'Boss native binding/converter regression failed'}
if(-not $BindingOnly){
& java '-Xmx256m' '-Dfile.encoding=UTF-8' -cp "$out;$game" BossObservationTest $compiled 2>&1 | Out-Host
if($LASTEXITCODE -ne 0){throw 'Boss projection/privacy regression failed'}
}
Get-FileHash -LiteralPath $game,$evil -Algorithm SHA256 | Format-Table | Out-Host
Write-Host "PASS: boss observation verification ($out)"
}finally{Stop-Transcript | Out-Null}

