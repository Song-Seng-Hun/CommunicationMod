param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$java = Join-Path $DownfallPath 'jre\bin\java.exe'
$out = Join-Path $root ('target\run-usability-tests\run-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $out | Out-Null
$compiled = Join-Path $out 'binding'
New-Item -ItemType Directory -Path $compiled | Out-Null
$gson = Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'
$gsonClasses = Join-Path $out 'gson'
# Extract the multi-release dependency, avoiding the Java 11 Windows ZIP filesystem close bug.
[IO.Compression.ZipFile]::ExtractToDirectory($gson,$gsonClasses)
$cp = "$compiled;$gsonClasses;$game;$DownfallPath\package\BaseMod-modded.jar;$DownfallPath\package\EvilWithin-modded.jar"
$sources = Get-ChildItem -LiteralPath "$root\src\main\java" -Recurse -Filter '*.java' | ForEach-Object FullName
& javac '-J-Xmx384m' --release 8 -encoding UTF-8 -cp $cp -d $compiled @sources
if ($LASTEXITCODE -ne 0) { throw 'Run usability fresh production compilation failed' }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp $game -d $out "$PSScriptRoot\RunUsabilityPolicyTest.java" "$PSScriptRoot\NativeUiInputTest.java" "$PSScriptRoot\RunUsabilityBindingTest.java" "$PSScriptRoot\DisplayedCostTest.java" "$PSScriptRoot\CardPlayObservationTest.java" "$PSScriptRoot\PlayerMechanicsTest.java" "$PSScriptRoot\NativeUpgradeChoiceTest.java" "$PSScriptRoot\SpecialCostTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Run usability tests compilation failed' }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -cp $game -d $out "$PSScriptRoot\CharacterResourcesTest.java" "$PSScriptRoot\NativeCostObservationTest.java" "$PSScriptRoot\NativeMechanicsBindingTest.java" "$PSScriptRoot\MechanicsCompletenessTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Character/resource binding tests compilation failed' }
& $java '-Xmx128m' -cp "$compiled;$out" MechanicsCompletenessTest
if ($LASTEXITCODE -ne 0) { throw 'Mechanics completeness regression failed' }
& $java '-Xmx128m' -cp "$out;$game" NativeMechanicsBindingTest $game "$DownfallPath\package\EvilWithin-modded.jar" "$DownfallPath\package\BaseMod-modded.jar" "$DownfallPath\package\StSLib-modded.jar"
if ($LASTEXITCODE -ne 0) { throw 'Installed character/resource signature regression failed' }
& $java '-Xmx128m' '-Dfile.encoding=UTF-8' -cp "$out;$game" CharacterResourcesTest $compiled "$DownfallPath\package\EvilWithin-modded.jar" $game
if ($LASTEXITCODE -ne 0) { throw 'Character/resource projection regression failed' }
& $java '-Xmx128m' '-Dfile.encoding=UTF-8' -cp "$out;$game" NativeCostObservationTest $compiled
if ($LASTEXITCODE -ne 0) { throw 'Native passive cost observer regression failed' }
& $java '-Xmx128m' -cp "$compiled;$out;$game" RunUsabilityPolicyTest
if ($LASTEXITCODE -ne 0) { throw 'Run usability policy regression failed' }
& $java '-Xmx128m' -cp "$compiled;$out;$game" NativeUiInputTest $compiled
if ($LASTEXITCODE -ne 0) { throw 'Native input scope regression failed' }
& $java '-Xmx128m' -cp "$compiled;$out;$game" NativeUpgradeChoiceTest $compiled "$DownfallPath\package\StSLib-modded.jar"
if ($LASTEXITCODE -ne 0) { throw 'Native upgrade selection handler regression failed' }
& $java '-Xmx128m' -cp "$compiled;$out" DisplayedCostTest
if ($LASTEXITCODE -ne 0) { throw 'Displayed cost regression failed' }
& $java '-Xmx128m' -cp "$compiled;$out" SpecialCostTest
if ($LASTEXITCODE -ne 0) { throw 'Special cost regression failed' }
foreach ($test in @('CardPlayObservationTest','PlayerMechanicsTest')) {
    & $java '-Xmx128m' '-Dfile.encoding=UTF-8' -cp "$compiled;$out;$game" $test $compiled
    if ($LASTEXITCODE -ne 0) { throw "$test failed" }
}
foreach ($mode in @('reward','grid','room','potion','information','upgrade-choice')) {
    & $java '-Xmx128m' -cp "$out;$game" RunUsabilityBindingTest $mode $game $compiled
    if ($LASTEXITCODE -ne 0) { throw "Run usability $mode binding failed" }
}
Write-Host "PASS: fresh run usability production compilation and regression tests ($out)"
