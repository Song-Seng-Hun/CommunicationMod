# 로컬 플레이 제어 (부분 지원)

기존 관찰 전용/메뉴 전용 실행을 바꾸지 않는 명시적 테스트 옵션입니다.
원본 Steam 설치 대신 해시 검증된 복사본, 별도 저장 폴더, 온라인 제출 차단을 사용합니다.
이 경계는 OS 수준 격리가 아니며 무관한 추가 모드의 네트워크 동작까지 보장하지 않습니다.

```powershell
.\devtools\prepare-local-test.ps1
.\devtools\start-local-test.ps1 -PlayControl
```

다른 터미널에서 현재 관찰과 가능한 행동을 읽습니다. 한 번에 하나만 보내고 다음 안정 상태를 확인합니다.

```powershell
.\devtools\send-local-menu-action.ps1
.\devtools\send-local-menu-action.ps1 -ActionId run.embark
```

`run.embark`는 잠금 해제된 캐릭터가 선택되어 있고 출정 버튼이 보이며 활성화됐을 때만 제공됩니다.
이름이 menu인 파일/도구를 재사용하지만 `runtime.control=partial_run`, 연결 `mode=play_control`로 구분됩니다.
기본 관찰 모드에는 행동이 없고 `-MenuControl`에는 출정/플레이 행동이 없습니다. 기존 텍스트 명령 실행기의 안전 차단도 유지됩니다.

## 연결한 행동과 제한

- 최초 안내: 일반 `FtueTip`의 제목/본문/버튼 이름 또는 `MultiPageFtue`의 현재 페이지 본문을 `observation.tutorial`로 전달합니다. `run.tutorial.confirm`은 해당 안내의 원래 입력 처리기를 호출합니다. 페이지 전환 중에는 행동하지 않으며 다음 페이지 본문을 미리 전달하지 않습니다. 삽화 자체는 전달하지 않아 `illustrations_available=false`로 표시합니다. 사용자 정의 안내 렌더러는 중단합니다.
- 허밋의 자체 2페이지 `HermitTutorials`도 별도 허용 목록으로 연결합니다. 다운폴 클래스를 필수 컴파일 의존성으로 추가하지 않습니다. BaseMod `CustomMultiPageFtue`와 다른 캐릭터의 고유 안내는 아직 연결하지 않았습니다.
- 표준 이벤트: 기존 본문 읽기 계약을 재사용합니다. `acknowledge_event_reading`에는 현재 `reading_id`와 내용을 설명하는 `commentary`가 필요합니다. 그 다음 실제 제공된 `run.event.*`를 사용합니다. 본문이 준비되지 않았거나 특수 렌더러라면 선택을 제공하지 않습니다.
- 지도: 제공된 `run.map.x.y`만 선택합니다. 현재 UI의 후보 노드를 다시 확인하고 기존 지도 입력 경로를 사용합니다. 보스 진입은 아직 제공하지 않습니다.
- 전투: 완성된 손패와 동일한 결정 토큰을 확인한 뒤 `run.play.<uuid>.<target_index>`/`run.end_turn`을 한 번만 적용합니다. 대상을 행동 ID별로 나누며 인자는 비어 있습니다. 드로우/대기 행동/화면 전환 중에는 행동하지 않습니다. 강제 카드 선택 화면은 아직 연결하지 않았습니다.
- 보상·상점·휴식·특수 선택·승리/재개 등의 나머지 화면은 자동 진행하지 않습니다. 전체 지원/클리어를 의미하지 않습니다.

이벤트 확인 인자의 형식 (ID/본문은 반드시 실제 최신 관찰에서 가져옵니다):

```powershell
.\devtools\send-local-menu-action.ps1 -ActionId acknowledge_event_reading -Arguments '{"reading_id":"현재 읽기 ID","commentary":"전달받은 본문과 선택지에 관한 설명"}'
```

같은 관찰은 상태 ID를 유지합니다. 진단용 `game_state.narrative.render_frame`만 안정성 비교에서 제외하며,
실제 대사/카드/자원/화면 변경은 명령을 무효화합니다. 출력에는 원래 공개 관찰 필터가 적용됩니다.
행동 응답은 다음 화면 완료의 증거가 아닙니다. 시간 초과 시 JSONL 기록을 확인하고 자동 재시도하지 않습니다.

## 검증

`prepare-local-test.ps1`은 기존 정보/손패/이벤트/수신/파일 잠금 회귀 검사와 메뉴·플레이 전송,
설치된 버튼/지도 훅 연결, 복사본의 제출 차단/해시 검증을 실행합니다.
실제 실행 결과와 남은 항목은 `DOWNFALL-IMPLEMENTATION.md`의 날짜별 기록을 기준으로 봅니다.
