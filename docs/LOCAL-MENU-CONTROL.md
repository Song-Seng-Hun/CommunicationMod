# 로컬 메뉴 제어

출정과 일부 플레이 동작은 별도 `-PlayControl` 옵션으로 확장 중입니다. [로컬 플레이 제어](LOCAL-PLAY-CONTROL.md)를 참고하세요. 이 문서의 `-MenuControl` 범위는 그대로 유지됩니다.

관찰 전용 실행과 구분되는, 메뉴 탐색용 v2 연결입니다. 현재 범위는 메인 메뉴의 **플레이 → 일반 게임 → 본편식 등반/다운폴 → 잠금 해제된 캐릭터 선택**입니다. 캐릭터 선택은 출발과 다릅니다. 출발·이어하기·포기·일일 도전·커스텀·전투·지도·이벤트 행동은 제공하지 않습니다.

## 실행과 명령

테스트 게임이 이미 실행 중이면 먼저 종료합니다. 별도 저장 폴더와 온라인 제출 차단을 갖춘, 해시 검증된 테스트 복사본만 사용합니다. 기본 실행은 여전히 관찰 전용입니다.

```powershell
.\devtools\prepare-local-test.ps1
.\devtools\start-local-test.ps1 -MenuControl
```

새로 준비하면 새 테스트 프로필이 생성됩니다. 기존 테스트 저장/설정을 유지하려면 게임을 종료한 상태에서 이전 테스트 폴더의 preferences/saves 및 appdata/localappdata를 새 테스트 폴더로 옮겨야 합니다. 원본 Steam 저장 파일을 이동하지 마세요.

다른 작업 창에서 현재 상태와 가능한 행동을 확인합니다.

```powershell
.\devtools\send-local-menu-action.ps1
.\devtools\send-local-menu-action.ps1 -ActionId menu.play
```

한 행동 후에는 상태를 다시 확인하고 **그 화면에서 제공된 ID**를 사용합니다. 다운폴에서는 서로 다른 단계의 일반 게임/본편식 등반 선택이 모두 `menu.panel.PLAY_NORMAL`일 수 있습니다. 같은 ID라도 상태 ID가 다르므로 같은 명령을 재전송하지 마세요. 허밋이 현재 목록에 있고 잠금 해제되어 있으면 `menu.character.HERMIT`가 제공됩니다.

## 규격과 안정성

- 연결 응답: `mode=menu_control`, `capabilities`에 `menu_navigation`.
- 상태: `runtime.control=menu_only`, `observation.menu`에 현재 화면·현재 언어의 버튼 이름/패널 설명·캐릭터 선택/잠금 여부.
- 두 번의 완료 프레임이 일치하고 애니메이션·알려진 팝업이 없을 때만 행동을 제공합니다. 수동 조작도 실행 직전에 다시 확인합니다.
- 내용이 같은 메뉴는 상태 ID를 유지하고 초당 한 번 같은 상태를 다시 기록합니다. 사람이 생각하거나 최신 파일의 잠금이 풀리기를 기다렸다고 상태가 무효화되지는 않습니다.
- 명령은 세션 ID, 상태 ID, 고유 요청 ID, 제공된 행동 ID, 빈 `arguments`를 포함합니다. 예전 상태/중복 요청/추가 인자는 거부합니다.
- `result.status=applied`는 기존 버튼 처리 함수를 실행했다는 뜻입니다. 다음 안정 상태의 화면 또는 `selected=true`로 결과를 확인해야 합니다.
- 게임 스레드에서만 UI에 접근합니다. Java/Windows 마우스를 임의 좌표로 움직이지 않으며, 메뉴 바깥에서 전투나 게임 내부 상태를 설정하지 않습니다.
- 미지원 화면은 `unsupported_or_transition`, 빈 행동 목록으로 중단합니다. 새로운 모드의 임의 오버레이까지 지원한다고 보장하지 않습니다.

로컬 클라이언트는 세션 기록 폴더의 `menu-request.json` 한 개를 소비해서 기존 표준 입출력 JSONL로 전달합니다. 게임 응답은 `observations.jsonl`과 요청 ID로 묶인 `menu-response.json`에 남습니다. 중간에 실패한 명령은 자동 재전송하지 않습니다. 명령 도구가 시간 초과하면 결과가 불확실하므로 먼저 기록을 확인하세요. 이 파일 방식은 로컬 연동 도구일 뿐 네트워크 서버가 아닙니다.

## 검증 경계

`MenuControlSessionTest`: 실제 순수 v2 코어의 안정 프레임·상태 유지·수동 변경·팝업·중복·인자·부분 실패 후 재실행 차단.

`MenuInboxTest`: 실제 Java 자식 프로세스의 명령/응답/소비/EOF. 기존 관찰 전용 및 파일 잠금 회귀 테스트도 유지합니다.

`MenuUiBindingTest`: 설치된 게임의 필드·함수 및 다운폴 리다이렉트 경로 확인. 이것만으로 실제 메뉴 이동 통과라고 표시하지 않습니다. 실제 실행 결과는 `DOWNFALL-IMPLEMENTATION.md`에 별도로 기록합니다.
