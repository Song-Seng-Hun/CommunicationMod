param([string]$DownfallPath = 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion',
      [switch]$PureOnly,
      [string]$Java = '')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Java) { $Java = Join-Path $DownfallPath 'jre\bin\java.exe' }
# Fresh temporary output prevents stale classes from making RED tests pass.
$out = Join-Path ([IO.Path]::GetTempPath()) ('public-description-' + [guid]::NewGuid())
New-Item -ItemType Directory -Path $out | Out-Null
$pure = Join-Path $root 'src\main\java\communicationmod\observation\PublicDescription.java'
$sources = @("$PSScriptRoot\PublicDescriptionTest.java")
if (Test-Path -LiteralPath $pure) { $sources += $pure }
& javac '-J-Xmx256m' --release 8 -encoding UTF-8 -d $out @sources
if ($LASTEXITCODE -ne 0) { throw 'Pure test compilation failed' }
# Deliberately exclude every installed game/mod jar from the execution classpath.
& $Java '-Xmx256m' '-Dfile.encoding=UTF-8' -cp $out PublicDescriptionTest
if ($LASTEXITCODE -ne 0) { throw 'Public description assertions failed' }
if ($PureOnly) { return }
$binding = Join-Path $root 'src\main\java\communicationmod\observation\CardObservation.java'
if (-not (Test-Path -LiteralPath $binding)) { throw 'ASSERT: actual card observation binding is missing' }
$game = Join-Path $DownfallPath 'desktop-1.0-modded.jar'
$base = Join-Path $DownfallPath 'package\BaseMod-modded.jar'
$evil = Join-Path $DownfallPath 'package\EvilWithin-modded.jar'
$cp = "$game;$base;$evil"
function Disassemble([string]$classpath, [string]$name) {
    $text = (& javap '-J-Xmx256m' -classpath $classpath -c -p $name) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "javap failed: $name" }
    return $text
}
function Require([string]$text, [string]$pattern, [string]$reason) {
    if ($text -notmatch $pattern) { throw "ASSERT: $reason" }
}
$render = Disassemble $cp 'basemod.patches.com.megacrit.cardcrawl.cards.AbstractCard.RenderCustomDynamicVariable$Inner'
$renderCN = Disassemble $cp 'basemod.patches.com.megacrit.cardcrawl.cards.AbstractCard.RenderCustomDynamicVariableCN'
foreach ($member in @('cardDynamicVariableMap', 'DynamicVariable.isModified:', 'DynamicVariable.value:', 'DynamicVariable.modifiedBaseValue:')) {
    Require $render ([regex]::Escape($member)) "installed renderer must use $member"
    Require $renderCN ([regex]::Escape($member)) "installed CN renderer must use $member"
}
$card = Disassemble $cp 'com.megacrit.cardcrawl.cards.AbstractCard'
Require $card 'CardModifierOnCreateDescription.calculateRawDescription' 'cached description must include modifier text'
Require $card 'DescriptionLine.getCachedTokenizedText' 'renderer must consume cached description lines'
Require $card 'RenderCustomDynamicVariable\$Inner' 'installed game must be wired to BaseMod renderer'
$dv = Disassemble $cp 'basemod.abstracts.DynamicVariable'
Require $dv 'CardModifierManager.modifiedBaseValue:' 'base values must include modifier numeric policy'
$downfall = Disassemble $cp 'guardian.helpers.SecondaryMagicVariable'
Require $downfall 'String GuardianSecondM' 'verify actual Downfall variable key'
Require $downfall 'Field guardian/cards/AbstractGuardianCard.secondaryM:I' 'verify actual Downfall value field'
# Compilation reads installed class definitions; it does not initialize them.
$compileOut = Join-Path $out 'binding'
New-Item -ItemType Directory -Path $compileOut | Out-Null
$gson = Join-Path $env:USERPROFILE '.m2\repository\com\google\code\gson\gson\2.8.9\gson-2.8.9.jar'
if (-not (Test-Path -LiteralPath $gson -PathType Leaf)) { throw 'Local Gson 2.8.9 dependency missing' }
# Avoid JDK ZIP filesystem close failures resolving restricted Windows JAR paths.
$localGson = Join-Path $out 'gson-classes'
[IO.Compression.ZipFile]::ExtractToDirectory($gson, $localGson)
$gson = $localGson
$sources = Get-ChildItem -LiteralPath "$root\src\main\java" -Filter '*.java' -Recurse | ForEach-Object FullName
$diagnostics = (& javac '-J-Xmx384m' --release 8 -encoding UTF-8 -cp "$cp;$gson" -d $compileOut @sources 2>&1) -join "`n"
if ($LASTEXITCODE -ne 0 -or $diagnostics -match 'exception has occurred|Exception:|error:') {
    throw "Actual installed-JAR compilation failed: $diagnostics"
}
if ($diagnostics) { Write-Host $diagnostics }
$bound = Disassemble "$compileOut;$cp" 'communicationmod.observation.CardObservation'
foreach ($member in @('cardDynamicVariableMap', 'DynamicVariable.isModified:', 'DynamicVariable.value:', 'DynamicVariable.modifiedBaseValue:', 'DescriptionLine.text:')) {
    Require $bound ([regex]::Escape($member)) "compiled observer must use $member"
}
Require $bound 'Settings.lineBreakViaCharacter:' 'CN cache policy must be selected using the actual rendering mode'
if ($bound -match '(?:putfield|putstatic)|(?:Method .*\.(?:initializeDescription|updateDescription|applyPowers|calculateCardDamage|onCreateDescription):)') {
    throw 'ASSERT: observer contains state writes or prohibited recomputation'
}
$converter = Disassemble "$compileOut;$cp" 'communicationmod.GameStateConverter'
Require $converter 'CardObservation.addTo:' 'card converter binding missing'
Require $converter 'AbstractStance.ID:' 'public stance ID missing'
Require $converter 'AbstractStance.name:' 'public stance name missing'
Require $converter 'AbstractStance.description:' 'public stance description missing'
foreach ($kind in @('relics/AbstractRelic', 'potions/AbstractPotion', 'powers/AbstractPower')) {
    Require $converter ([regex]::Escape("$kind.description:")) "$kind description missing"
}
Require $converter 'DrawPileVisibility.orderForPlayer:' 'draw privacy projection missing'
# Pure existing regression; compiled game-dependent classes are never loaded.
& javac '-J-Xmx256m' --release 8 -d $out "$PSScriptRoot\DrawPileVisibilityTest.java"
if ($LASTEXITCODE -ne 0) { throw 'Draw regression compilation failed' }
& $Java '-Xmx256m' -cp "$out;$compileOut;$gson" DrawPileVisibilityTest
if ($LASTEXITCODE -ne 0) { throw 'Draw privacy regression failed' }
& $Java '-Xmx256m' '-Dfile.encoding=UTF-8' -cp "$out;$compileOut;$gson" PublicDescriptionTest --draw
if ($LASTEXITCODE -ne 0) { throw 'Enhanced-field draw privacy regression failed' }
# These launchers use reflection, and this classpath contains no game/native code.
& javac '-J-Xmx256m' --release 8 -d $out "$PSScriptRoot\DevelopmentLaunchSafety.java" "$PSScriptRoot\DevLoader.java" "$PSScriptRoot\DevGameLauncher.java" "$PSScriptRoot\LaunchSafetyTest.java" "$root\src\main\java\communicationmod\safety\AutomationSafety.java"
if ($LASTEXITCODE -ne 0) { throw 'Pure launch-gate test compilation failed' }
& $Java '-Xmx256m' -cp $out LaunchSafetyTest
if ($LASTEXITCODE -ne 0) { throw 'Launch gates changed' }
Write-Host 'PASS: installed rendering bytecode, actual bindings, full-source compilation, draw privacy'
Write-Host "Pure outputs: $out"
