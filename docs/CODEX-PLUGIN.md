# Downfall Agent — 로컬 Codex 플러그인

게임 실행·상태 확인과 기존 CommunicationMod MCP 제어를 하나의 개인 플러그인으로 제공합니다. 게임은 설치/플러그인 로딩만으로 켜지지 않습니다. 사용자가 실행/플레이를 요청하면 `sts_start_game`으로 실행합니다.

## 사용

새 Codex 대화에서 Downfall Agent를 선택하고 **“다운폴을 실행하고 MCP로 연결해줘”**라고 요청합니다. 기존 대화의 도구 목록은 자동 갱신된다고 가정하지 않습니다. 플러그인 설치/업데이트 후 새 대화에서 도구 노출을 확인합니다.

| 도구 | 동작 |
| --- | --- |
| `sts_start_game` | 검증된 테스트 게임 실행 또는 동일 프로세스 재사용, MCP 연결 대기 |
| `sts_game_status` | 게임을 실행하지 않고 프로세스·연결 상태 확인 |
| `sts_get_state` | 현재 화면의 간결한 관찰 |
| `sts_act` | 행동 한 번과 다음 안정 상태 |
| `sts_get_context` | 필요할 때 전체 덱·지도·지난 대사 조회 |
| `sts_get_request` | 시간 초과 명령의 결과 확인, 재전송 없음 |

시작 결과가 `starting`이면 이미 게임을 실행한 것이므로 로딩 후 상태를 조회합니다. `launch_in_progress`이면 다른 호출이 실행 검사를 진행 중입니다. 프로세스 상태 조회에는 Windows 조회 비용이 있으므로 매 카드마다 호출하지 않습니다. 정상 플레이에는 빠른 `sts_act`/`sts_get_state`를 사용합니다.

## 설치와 업데이트

요구 환경: Windows, Node.js 22+, PowerShell 7, Python(패키지 검증용), Codex CLI/기본 plugin-creator 스킬, 기존 CommunicationMod 체크아웃과 검증된 테스트 게임. 게임 JAR를 재배포하지 않습니다. 설치된 독립판과 빌드 선행 조건으로 최초 `devtools/prepare-mcp-test.ps1`을 수행한 환경을 사용합니다. 일반 설치/업데이트는 Java 게임을 재빌드하지 않으므로 기존 준비 경로와 저장을 유지합니다.

저장소 루트에서 한 명령으로 MCP 검사 → 패키징 → 검증 → 개인 플러그인 설치/업데이트:

```powershell
.\devtools\install-codex-plugin.ps1
```

`codex`가 PATH에 없으면 `-CodexCommand '실제 codex.exe 절대 경로'`를 지정합니다. `-UsePreparedPackage`는 직전에 패키징한 결과를 재설치할 때만 사용합니다. 재패키징은 `package-codex-plugin.ps1`이며 저장소/팀 마켓플레이스를 생성하지 않습니다.

소스는 `plugin/downfall-agent`, 개인 배포 소스는 `%USERPROFILE%\plugins\downfall-agent`, 개인 항목은 `%USERPROFILE%\.agents\plugins\marketplace.json`입니다. 공식 스캐폴드/검증/cachebuster/CLI 설치 절차를 사용합니다. 새 버전은 설치 캐시에 별도로 설치됩니다.

기계별 작업 경로와 PowerShell 경로는 `%USERPROFILE%\.communicationmod\plugin.json`에 저장합니다. 설치 프로그램은 설정 변경 전과 개인 소스 변경 전에 `target/plugin-*-backup-*`으로 백업합니다. 플러그인 캐시 안에는 이 설정과 게임·저장이 들어가지 않습니다. 재설치해도 같은 테스트 런타임을 사용합니다. 공유받은 다른 PC에서는 자신의 설치/체크아웃으로 준비와 설치를 수행해야 합니다.

최초 전환 시 기존 수동 `communicationmod` MCP 항목은 설치된 플러그인의 실제 통신 검증 후에만 제거합니다. 설치 스크립트가 임의의 동명 항목을 자동 제거하지는 않습니다. 이 PC에서는 정확한 기존 항목을 `target/communicationmod-legacy-mcp-backup.json`으로 백업 후 제거했습니다. 다른 MCP/플러그인은 유지했습니다.

## 실행·보존 경계

- 게임 JAR, 저장, 로그, 브리지 인증 토큰은 패키지나 Git에 포함하지 않습니다.
- 원본 Steam 설치, Java 11, Steam 시작 옵션은 변경하지 않습니다. 게임은 설치본의 Java 8을 명시적으로 사용합니다.
- 준비된 런처 해시와 복사본/원본 JAR 해시를 확인한 뒤 기존 테스트 런처로 실행합니다. 게임 버전이나 산출물이 바뀌면 실패하고 다시 준비하도록 알립니다.
- 작업 폴더별 Windows 명명 뮤텍스로 조회/검사/실행을 직렬화합니다. 이미 다른 복사본이나 제어 모드로 실행 중이면 새 게임을 켜지 않습니다. 권한 문제로 프로세스 조회가 실패해도 '실행 중 아님'으로 처리하지 않습니다.
- 게임 프로세스 종료/재시작/이어하기/런 포기 도구는 추가하지 않았습니다. MCP 연결 종료나 플러그인 제거가 게임을 종료하거나 저장을 삭제하지 않습니다.
- 기존 온라인 제출 차단, 공개 정보 필터, 손패 완성 대기, 이벤트 읽기 계약을 유지합니다. 모든 계정 위험이나 전체 다운폴 호환을 보장한다는 뜻은 아닙니다.
- `PATHEXT`가 생략되는 MCP 환경에서는 Windows 실행 확장자 기본값만 보충합니다. 전역 환경을 변경하지 않습니다.

## 2026-09-08 검증 결과

- 설치 ID `downfall-agent@personal`, 버전 `0.1.0+codex.20260908123748`; CLI 조회에서 installed/enabled 확인.
- Node 테스트 11개 통과. 기존 10개 고정 평가 질문도 실제 SDK 도구로 정답 접근 가능 확인(별도 LLM 평가 아님).
- `PluginLifecycleTest.ps1`: 작업 폴더 밖 런타임, 런처 해시 변경, 누락된 manifest 거부.
- `PluginLaunchMutexTest.ps1`: 별도 PowerShell 프로세스의 중복 실행 요청 차단.
- 플러그인 manifest와 게임 지침 SKILL 검증 통과. 게임 코드/Java 산출물은 이전 전체 검사 통과 버전을 재사용하고 런처 해시 검사를 실제 재실행함.
- **설치 캐시의 서버**를 공식 MCP SDK로 구동: 처음 상태 `stopped` → `sts_start_game`으로 PID **36848** 시작 → `MAIN_MENU`, 언어 **KOR**, 상태 ID **4**, 온라인 제출 `disabled_in_test_copy` 확인.
- 다시 `sts_start_game`을 호출했을 때 같은 PID 36848 재사용 확인. 검사 클라이언트 종료 후에도 게임 유지. 이어하기/게임 행동 **0회**.
- 세션 `896150ee-c1d8-4459-b2fc-d844c0c28e7d`, 게임 빌드 SHA-256 `3de5454bef9974d8c4a2da7c006c14b75f1c2db245334e87e1d2e8fb67b0c97a`.
- 실제 첫 시작 호출은 검사·로딩 대기 포함 약 60초에 `starting`, 메뉴 수신/재사용 검사 전체 약 75초. 게임 콜드 스타트 시간이며 기존 상태 조회의 밀리초 단위 측정과 다릅니다.
- 로컬 증거: `target/plugin-install-fixed.log`, `target/plugin-live-verification.log`; Git에는 업로드하지 않음.

실행 재현(명시적으로 테스트 게임을 켜며 게임 선택은 하지 않음):

```powershell
node .\mcp-server\scripts\verify-plugin-live.mjs '실제 설치 캐시의 downfall-agent 버전 폴더'
.\devtools\PluginLaunchMutexTest.ps1
```

이번 대화에는 새 플러그인 도구가 아직 노출되지 않았습니다. 설치 캐시의 MCP 실행은 검증됐지만, 새 Codex 대화의 도구 선택 UI는 별도 확인 사항입니다. 전투·특수 화면 전체 실검증과 허밋 클리어는 이 플러그인 작업의 완료 기준이 아닙니다.

제거는 사용자 요청 시 `codex plugin remove downfall-agent@personal`로 수행할 수 있습니다. 개인 소스/게임/저장/별도 설정은 임의 삭제하지 않습니다. 수동 MCP로 되돌릴 때는 보존한 항목의 Node/서버 경로를 확인하고 `codex mcp add`로 복원합니다.

공식 구조 참고: [Build plugins](https://learn.chatgpt.com/docs/build-plugins). 실행 규격과 추가 맥락은 `mcp-server/README.md`, `MCP-LIVE-VERIFICATION.md`를 참고합니다.
