# Offline storage and direct upload bytecode audit

Audit date: 2026-09-08. Scope: installed JAR ZIP entries and Java 11 `javap -c -p` disassembly only. No game launch, target-class execution, native initialization, account access, or network requests. Steam factory/bootstrap selection is deliberately left to the main audit. Only this document was added by this audit.

## Inputs and reproducibility

Installation root: `D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion`.

| JAR relative to installation root | Bytes | SHA-256 |
| --- | ---: | --- |
| `desktop-1.0-modded.jar` | 379999153 | `ABB42EBDC5D3EA66B30D6A1993D80880AB45B21A1DE01F8CE43945BF31B9FEE8` |
| `package\BaseMod-modded.jar` | 7664038 | `BA44FDFDCCD6B0949A862E4555DAF7F91CD5EBB71F24559BFF420D99166A2056` |
| `package\EvilWithin-modded.jar` | 481915937 | `148C422DD26E869FBDC9A63F9AB072373C3D791D3E3FCBE8D87B55AE75A29226` |

Example read-only reproduction; change the JAR and class for the other entries below:

```powershell
& 'C:\Program Files\Microsoft\jdk-11.0.29.7-hotspot\bin\javap.exe' -c -p `
  -classpath 'D:\game\Steam\steamapps\common\Downfall - A Slay the Spire Fan Expansion\desktop-1.0-modded.jar' `
  'com.megacrit.cardcrawl.metrics.Metrics'
```

Evidence references below are class/method names and bytecode offsets, not source line numbers. This matters because the installed desktop classes already contain injected calls into BaseMod and Downfall.

## Storage paths actually observed

Let `R` be the process's local working-directory root, and let `P` be empty for save slot 0 or `<slot>_` for other slots (normally `1_` or `2_`). These are path conventions, not an inventory of the user's existing saves.

| Data | Convention | Concrete bytecode evidence |
| --- | --- | --- |
| Preferences | `R\preferences\P<name>`; `betaPreferences` when beta or GOG | `com.megacrit.cardcrawl.helpers.SaveHelper.getSaveDir()` offsets 0–20 select `betaPreferences` if `Settings.isBeta` or `isGog()`; otherwise `preferences`. `getPrefs(String)` offsets 0–56 add slot prefix, 91–110 construct the path, 230 assign `Prefs.filepath`. `loadJson` uses `Gdx.files.local`. |
| Active save | `R\saves\P<chosenClass.name()>.autosave` | `saveAndContinue.SaveAndContinue.<clinit>` offsets 19–34 set `SAVE_PATH` to `saves` + separator. `getPlayerSavePath(PlayerClass)` offsets 16–48 add slot prefix, enum name, `.autosave`. This installed method does **not** append `BETA`. |
| Backups | Same file path + `.backUp` | `helpers.File.save()` offsets 2–39 prepend `Gdx.files.getLocalStoragePath()`, 43–79 form backup path, 139–147 copy/validate old file then delete old primary, 180–186 write/validate replacement. Save loading and preference corruption recovery also reference `.backUp`. |
| Run history | `R\runs\P<chosenClass.name()>\<epochSeconds>.run`; daily runs use `PDAILY` directory | `metrics.Metrics.gatherAllDataAndSave()` builds `runs`, prefix, character/daily directory and `.run` (normal branch 33–155; daily branch 172–280), then `FileHandle.writeString(json,false)` at 292. `gatherAllData()` sets `lastPlaytimeEnd = currentTimeMillis()/1000` at 334–342. |
| Run-history reads/pruning | Same `R\runs` tree | `screens.runHistory.RunHistoryScreen.refreshData()` reads local `runs`, filters slot directory names, reads JSON. `Metrics.removeExcessRunFiles()` lists local `runs` and deletes excess `.run` files. Sharing this tree permits pruning of shared history. |
| Corruption preservation | Local `sendToDevs` path with original path and `.corrupt` suffix | `SaveHelper.preserveCorruptFile(String)` constructs `sendToDevs` + input path + `.corrupt` and uses local `moveTo`. The name alone is not evidence of upload. |
| Display settings | `info.displayconfig` | `core.DisplayConfig.readDisplayConfFile()` and `writeDisplayConfigFile(...)` contain this filename. Treat it as part of the isolated runtime's writable files. |

`com.badlogic.gdx.backends.lwjgl.LwjglFiles.<clinit>` constructs `localPath` from `new java.io.File("").getAbsolutePath()` plus separator; `externalPath` is `System.getProperty("user.home")` plus separator. `getLocalStoragePath()` returns the cached local path. Set the actual child-process working directory before startup; changing only a JAR location does not isolate these paths. Changing `user.home` alone does not redirect local saves.

Preference names in `SaveHelper.deletePrefs(int)` include `STSDataVagabond`, `STSDataTheSilent`, `STSDataDefect`, `STSDataWatcher`, `STSAchievements`, `STSDaily`, `STSSeenBosses`, `STSSeenCards`, `STSBetaCardPreference`, `STSSeenRelics`, `STSUnlockProgress`, `STSUnlocks`, `STSGameplaySettings`, `STSInputSettings`, `STSInputSettings_Controller`, `STSSound`, `STSPlayer`, and `STSTips`. `Prefs.flush()` serializes its map as JSON and calls `AsyncSaver.save(filepath,json)` at offset 26. These are local achievement/progress records; their names do not prove isolation from account services.

BaseMod's `basemod.abstracts.CustomPlayer.loadPrefs()` obtains preferences using `chosenClass.name()` at offsets 9–15. Thus custom characters also share the above preference root, with enum-name filenames rather than necessarily an `STSData...` name. `saveData.SaveData$SaveDataToFile.addCustomSaveData(...)` in EvilWithin adds fields such as `EVIL_MODE`, consumed keys and merchant state to the existing save map; it is not evidence of a separately isolated save directory. `downfall.patches.MigrateSavePatch.Prefix(CardCrawlGame)` is just `0: return` in this artifact, although an unused-by-that-prefix `copyDirectory` helper exists.

### Mod configuration escapes the working directory

`com.evacipated.cardcrawl.modthespire.lib.ConfigUtils.<clinit>` reads Windows `LOCALAPPDATA` at offsets 6–11, falling back to `APPDATA` if null/empty at 12–28; offsets 142–160 append `\ModTheSpire`. It calls `mkdirs` at 175. It does not use `user.home` for the Windows branch.

`SpireConfig.makeFilePath(mod,name,extension)` constructs `<CONFIG_DIR>\<mod>\<name>.<extension>` (null mod omits the mod directory). The two-argument overload defaults to `properties`. Its constructor calls `createNewFile` at offset 44 and then `load`; merely constructing a configuration is potentially a write.

Observed paths under that root:

| Relative path | Caller |
| --- | --- |
| `BaseMod\basemod-config.properties` | `basemod.BaseMod`, SpireConfig construction at offset 98 |
| `BaseMod\console-history.txt` | `basemod.DevConsole$PriorCommandsList.<clinit>`, `makeFilePath` at 6 |
| `downfall\TutorialsViewed.properties` | `downfall.downfallMod.initialize()`, `saveTutorialsSeen()` |
| `downfall\TrapSaveData.properties` | `downfallMod.saveData()`, `loadOtherData()` |
| `downfall\downfallSaveData.properties` | `downfallMod.saveData()`, `loadConfigData()` |
| `reskinContent\reskinContentSaveData.properties` | `reskinContent.reskinContent.saveSettings()`, `loadSettings()` |

The scan also found SpireConfig references in BaseMod `EasyConfigPanel`, `DraggableUI`, and Downfall `GoldenIdol_Evil`. The table is not an exhaustive inventory of all dynamically supplied configuration names. Redirect the whole configuration root, not just these filenames.

## Direct uploads outside PublisherIntegration

### Confirmed Downfall metrics bypass

1. `com.megacrit.cardcrawl.screens.GameOverScreen.shouldUploadMetricData()` initially combines `Settings.UPLOAD_DATA`, `publisherIntegration.isInitialized()`, and `Settings.isStandardRun()` (offsets 0–28). At **33** it calls `downfall.patches.MetricsPatches$ShouldUploadMetricData.Postfix(boolean)`.
2. That postfix replaces the result with `Settings.UPLOAD_DATA` for Downfall characters (offsets **0–12**). For those characters, an uninitialized publisher or nonstandard run does not preserve the original false result.
3. `DeathScreen` inherits this static guard. Its death/victory metrics methods first call `Metrics.gatherAllDataAndSave()` to retain local history, then conditionally create/start an upload thread. For example, `submitVictoryMetrics()` saves at **12**, checks at **15**, starts the thread at **41**.
4. `Metrics.run()` skips its original sending path when `Settings.isModded` is true, but still reaches injected `MetricsPatches$RunPatch.Postfix(this)` at **127**.
5. `RunPatch.Postfix` checks only request type `UPLOAD_METRICS` and `downfallMod.isDownfallCharacter(player)` (offsets **0–16**). It reflectively invokes private `Metrics.sendPost(String,String)` with `http://downfallstats.atwebpages.com/beta/` and null (URL at **54**, reflection invocation at **61**). It does not recheck `UPLOAD_DATA`, publisher status, or `isModded`.
6. The **one-argument** `Metrics.sendPost(String)` is `0: return`, but the **two-argument** overload is live: it builds JSON, sets POST and URL at **126–132**, and invokes `Gdx.net.sendHttpRequest` at **182**. Its envelope contains `event` = accumulated params, `host` = beta `playerName` or otherwise `alias`, and epoch `time`. Gathered run data includes seed, score, character, deck, relics, path and encounter/progression data.

`Settings.initializeGamePref(boolean)` reads `STSGameplaySettings` and `"Upload Data"` with **default true** (`iconst_1` at 437, `getBoolean(String,boolean)` at 438, assignment at 441). A fresh profile is therefore not an upload opt-out. Setting this false prevents the inspected normal death/victory thread creation, but the downstream postfix is not independently guarded. Disabling Steam/publisher integration alone is insufficient.

### Other observed transport paths

| Class/method | Behavior / boundary |
| --- | --- |
| `metrics.BotDataUploader.<init>`, `sendPost(HashMap)` | Reads environment `STS_DATA_UPLOAD_URL`; `sendPost` returns only when URL is null (offsets 0–7). Reads `STS_DATA_UPLOAD_KEY` into payload, sends JSON with `Gdx.net.sendHttpRequest` at 171. No PublisherIntegration or UPLOAD_DATA check in that method. `uploadDataAsync` starts a worker. Data types include cards, relics, enemies, potions, daily mods, blights and keywords; its log text says leaderboard, but this is not proof of personal score submission. `CardLibrary.uploadCardData()` calls it at 99; other references occur in PotionHelper, RelicLibrary, MonsterHelper, ModHelper, BlightHelper, CharacterSelectScreen and CardCrawlGame. Full caller reachability/guards were not established. |
| `daily.TimeLookup.fetchDailyTimeAsync`, `makeHTTPReq` | Direct request to `https://hyi3lwrhf5.execute-api.us-east-1.amazonaws.com/prod/time`; `sendHttpRequest` at 63 in helper. This is time retrieval, not established score upload, but is still external traffic. |
| `integrations.SteelSeries.sendPost` | Uses `Gdx.net.sendHttpRequest` at 102. Constructor references SteelSeries Engine `coreProps.json` and builds `http://` endpoint. Treat as a separate transport surface; actual destination and activation were not established here. |

ZIP class-byte scans covered all `.class` entries in BaseMod and EvilWithin for the named transport/configuration patterns, and desktop `com/megacrit` and `de/robojumper` namespaces for URL/HTTP/socket/SteamRemoteStorage/BotDataUploader patterns. URL references in mod classes may be classpath/resource inspection rather than network access. EvilWithin also contains browser/store/Discord/tutorial links; no link was opened. Bundled third-party libraries, arbitrary reflection, native transport and dynamically constructed endpoints are not exhaustively resolved by these scans.

## Steam Cloud: established boundary and unknown actual mapping

Only storage-facing methods were inspected in `com.megacrit.cardcrawl.integrations.steam.SteamIntegration`:

- `getAllCloudFiles()` constructs `SteamRemoteStorage`, calls `getFileCount()` at 16, `getFileNameAndSize(...)` at 74 and `fileExists(...)` at 82, then disposes it at 176. Names come from Steam dynamically.
- `deleteAllCloudFiles()` calls that enumeration and `deleteCloudFiles(...)`; the latter calls `SteamRemoteStorage.fileDelete(name)` at 71. Do not invoke these as an isolation or cleanup technique: they act on account cloud files.
- The inspected methods do not supply a fixed local Steam userdata directory or the client's Auto-Cloud rules. The local save writers above use filesystem APIs, not a direct Steam upload call. That does **not** establish that Steam will not sync those files separately.

The exact installed app's Auto-Cloud roots/patterns, account-specific remote cache path, and active client cloud configuration remain unknown under this ZIP-only scope. No claim that a specific `userdata\<account>\<appid>\remote` path applies has been verified. Steam factory/bootstrap and account identity selection remain with the main agent.

## Isolation requirements for the requested offline automation

These are requirements for a later implementation, not changes performed by this audit.

1. Use a dedicated real working directory outside the Steam installation, Steam userdata and cloud-synced document folders. This workspace is under OneDrive, so it should not automatically become the permanent isolated save root. Keep the entire `preferences`, `betaPreferences`, `saves`, `runs`, backup/corruption and display/log output area separate. A different save slot is not isolation.
2. Set child-only `LOCALAPPDATA` and `APPDATA` to dedicated directories before class initialization; also isolate `user.home` for external-path consumers. Do not alter the parent's/user's global environment. Verify canonical paths and absence of junctions/symlinks back to real data. A dedicated configuration root also separates console history and tutorials.
3. Preserve local history functionality while disabling upload transport. Set `"Upload Data"` false in every reachable isolated profile and enforce the offline policy at the active two-argument Metrics sender/Downfall postfix, not just the already-empty one-argument overload. Ensure later UI toggles or direct calls cannot reactivate transport.
4. Remove `STS_DATA_UPLOAD_URL` and `STS_DATA_UPLOAD_KEY` from the child's environment and block BotDataUploader's transport as well. Disable unrelated direct transports such as time lookup, peripheral HTTP, browser launching and account-connected integrations for this automation mode.
5. Combine the main agent's no-account publisher/bootstrap work with an enforceable network boundary for the actual child process and any permitted helpers. A publisher stub alone does not cover these direct HTTP paths. This audit neither installs firewall rules nor authorizes account-linked automation, scores, statistics or achievements.
6. Establish the actual Steam Auto-Cloud rules before certifying the chosen root as outside them. Do not register the isolated output for cloud sync, copy it back into the installation, link it to existing saves, or clear account cloud files. Future validation should prove resolved read/write paths and blocked submission attempts in an approved isolated environment; no runtime validation occurred here.

## Remaining limits

This is static evidence for the exact hashed artifacts, not an absolute security guarantee. Runtime classpath precedence, additional mods/JARs (including StSLib and the development patch), native behavior, Twitch/account integrations, dynamically loaded code and the eventual isolation implementation were not audited exhaustively. Actual Steam client sync behavior is outside these JARs. No claim is made that present launch scripts already meet these requirements or that gameplay was tested.
