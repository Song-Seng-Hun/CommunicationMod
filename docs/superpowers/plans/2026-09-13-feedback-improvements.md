# Gemini 피드백 반영

작업 경로: `E:\dev\gamemcp\sts`. 기준선: `target/feedback-baseline-20260913`.

## 반영 설계와 범위

기존 전투 요약과 Stasis/Ghostflame 관측 구현을 기준으로 개선했다. 새 도구/view/schema 추가 없이 기본 요약의 작은 결제·대상 정보를 보완하고, Encode/Ghostflame 관찰자를 분리했다. Git 이력 정리는 아래 의존성별 분할표로 준비했다. 현재 변경을 스테이징하거나 커밋하지 않았다.

큰 전투 view 추가는 응답 비용을 늘리므로 채택하지 않았다. 카드 순서대로 추가 정보 예산을 쓰는 구현도 측정에서 마지막 카드 조회 비용이 10.54% 늘어 폐기했다. 최종 설계는 **손패 전체가 예산에 들어오는 경우에만** 추가 정보를 싣는다.

## 전투 정보 조회 개선

기존 `displayed_cost_text`, `is_playable`, 완전성 필드는 그대로 유지한다. 작은 `cost_components`, 비용 범위/실패 이유/가용 자원/X 비용 정보, `target_playability`를 원문 값 그대로 요약에 추가한다.

- 카드당 추가 JSON 최대 384자, 손패 전체 합계 최대 768자.
- 작은 평면 표에 한정: 표당 최대 3행, 행당 최대 8필드, 문자열 120자. 중첩 데이터·초과 데이터는 자르지 않고 기존 카드 상세 ref로 유지.
- 한 카드의 정보가 들어가지 않거나 전체 손패 예산을 넘으면 모든 카드의 추가 정보를 생략한다. 앞 카드만 싣는 편향 없음. 요약에서 손패가 잘리는 경우에도 생략.
- `details_required`는 제거하지 않는다. 키워드·부작용·행동 인자·다른 필수 사실의 상세 확인은 계속 필요하다.
- null/false/0, unknown provider, 비용/설명 완전성을 추측하거나 승인으로 바꾸지 않는다. 불완전한 비용을 한 번에 확인할 수 있어도 행동이 가능해지는 것은 아니다.
- same-state 자료 재사용, refs 배치, receipt 재전송 금지, 이벤트 설명 규칙 유지. map-only는 변경된 손패를 읽지 않는다.

코드: `mcp-server/src/context.ts`. 회귀: `mcp-server/test/combat-summary.test.mjs`.

## 관찰자 구조

`PlayerMechanicsObservation`의 Encode/Ghostflame 분기를 `NativePanelObserver` 계약과 `EncodePanelObserver`, `GhostflamePanelObserver` 구현으로 추출했다. 조정 클래스는 관찰 순서·패널별 실패 처리·기존 자원 합성·전체 완전성만 연결한다. 공개 formatter 진입점은 호환용 위임으로 유지했다.

캐릭터 클래스만으로 관찰을 차단하지 않는다. 다른 캐릭터에게도 실제 표시될 수 있는 Encode 등은 기존 native visibility gate를 따른다. 오류는 기존 unavailable reason을 유지하고 전체 정보 완전성에 전파한다. 한 패널의 실패가 다른 패널 출력을 없애지 않는다. 기존 Inferno 설명의 부작용 있는 getter 호출 회피도 유지했다.

Stasis/orb 상세와 `CharacterResources`의 기존 자원 관찰은 유지했다. 이번 작업이 모든 캐릭터를 완전히 분리하거나 실플레이 검증까지 마쳤다는 뜻은 아니다.

## 변경 이력 분할표

현재 dirty checkout은 이전 여러 기능을 포함한다. 디렉터리째 스테이징하지 말고 아래 순서로 기능·테스트·문서 묶음을 준비한다. 공유 파일은 해당 기능의 hunk만 선택한다.

| 순서 | 기능 묶음 | 주요 파일/패턴 | 선행·검증 |
| --- | --- | --- | --- |
| 1 | 공개 관측·실행 안전 기반 | `CardObservation`, `CombatObservation`, `RunUi`, `NativeUiInput`, `RunUiPolicy`, 관련 devtools 검증 | Java fresh compile + run-usability |
| 2 | 카드 강화 | `CardUpgradeObservation`, `UpgradePreview`, `NativeUpgradeTree`, `UpgradeChoiceUi`, `*Upgrade*Test`, 강화 문서 | 1 + upgrade 검증 |
| 3 | 특수 자원·캐릭터 정보 | `CharacterResources`, `PlayerMechanicsObservation`, 비용/완전성 관측, 관련 Test·문서 | 1·2 + 자원/비용/기믹 검증 |
| 4 | 지도 계획 | `observation`의 map 연계 hunk, `map/`, `MapDrawingPatch`, `*Map*Test`, 지도 문서 | 1 + map planner 검증 |
| 5 | MCP 최소 요약·분할/compact | `context.ts`, `format.ts`, `view.ts`, pagination/format/performance 회귀 및 문서 | 1–4에서 관측 계약 확정 + npm test |
| 6 | 상황별 예제 | `guidance*`, `tool-schemas`, build-guidance, 예제 fixture/회귀/문서 | 5 + 수동 계약 재검토와 예제 build |
| 7 | 대기·계측·평가·스킬 | `session.ts`, `metrics.ts`, `hooks/`, agent-latency/efficiency scripts/test, skill | 6 + npm test, 가상 비용 보고서 |
| 8 | 이번 피드백 개선 | 새 panel observer 3개, facade/test hunk, 전투 evidence hunk/test, measure-feedback, 이 문서 | 3·5·6·7 + 아래 검증 |
| 마지막 | 패키징·수명 주기 연결 | `BuildLocalObserver`, `package-codex-plugin`, `index.ts`, `lifecycle.ts`, package 파일과 사용자 문서의 각 연결 hunk | 관련 기능을 가진 중간 커밋마다 함께 분리; 최종에만 몰아 깨진 중간 상태를 만들지 않음 |

`GameStateConverter.java`, `context.ts`, `index.ts`, 빌드 스크립트, package 파일, `guidance-contract.json`은 여러 묶음이 겹친다. 이를 한 기능에 통째로 넣으면 아직 없는 구현을 참조할 수 있다. 계약 fingerprint는 각 중간 커밋의 실제 소스에 대해 예제를 재검증한 뒤 갱신해야 한다. 위 표는 커밋 후보이며 중간 커밋을 실제 생성/빌드한 결과가 아니다. 나머지 연결 변경은 `git diff`의 용도를 확인해 해당 묶음으로 넣는다.

## 검증과 비용

새 전투 회귀는 구현 전 3건 실패를 확인했고, 큰 손패 편향은 측정 후 재현 테스트가 실패하는 것을 확인한 뒤 고쳤다. Java는 분리 전후 fresh production compile과 기존 fixture/binding suite를 실행했다. 관찰자 오류 격리와 전체 완전성 전파 assertion도 추가했다.

최종 전체 `npm test`: **168/168 통과**, 실패/취소/스킵 0, exit 0, 테스트 본체 8.62초. 빌드에서 51개 기능·153개 예제를 재검증했다. Java 분리 후 `verify-run-usability.ps1`도 exit 0으로 통과했다. 14개 native player type, 40개 읽기 전용 getter body, 36개 Inferno 설명 사례 및 비용·선택·공개 정보 fixture를 검증했다. Java 빌드의 기존 deprecated API 안내는 유지되었다.

독립 검토 Aristotle: 최신 코드 기준 열린 지적 없음, 기존 build에 대한 핵심 Node 테스트 **65/65** 독립 실행. Java는 검토자가 소스를 확인했으며 실행 결과는 구현 담당자의 별도 증거다. 검토 중 안내했던 계약 repin은 완료되었고, 최종 fingerprint는 `95551d30dfbfb852903f20cd25546edcc0a9bea467a6af1e25ce6dcc3935e7ef` / 101개 파일이다.

재현:

```powershell
# 저장소 루트
.\devtools\verify-run-usability.ps1
# mcp-server
npm.cmd test
node scripts/measure-feedback.mjs
```

보고서: `target/feedback-review/token-report.json`. `o200k` 기준 동일 스킬·입력 스키마를 한 번, 모든 요청·text+structured 응답을 포함한다. 도구 설명은 양쪽에서 같지만 수치에 포함하지 않았다. 필수 비용·대상 사실을 얻는 조회 수를 측정하며 실제 행동 전체/에이전트 추론속도/호스트 청구 토큰을 측정하지 않는다.

일부 Steam/LibGDX 바이트코드 경로 차단은 전체 OS/네이티브/세이브/클라우드 격리를 보장하지 않는다. TOON의 최소 10% 감소는 채택된 개별 `o200k` 응답 기준이다. 기존 문서의 검증 한계를 유지한다.

| 필수 정보 조회 사례 | 조회 전→후 | 정적 토큰 전→후 | 변화 |
| --- | --- | --- | --- |
| 작은 결제·대상 정보 | 2→1 | 2532→2320 | -8.37% |
| 불완전 결제 정보 확인 | 2→1 | 2530→2318 | -8.38% |
| 긴 사용 불가 이유 | 2→2 | 2748→2748 | 0% |
| 큰 손패의 마지막 카드 | 2→2 | 3493→3493 | 0% |

기존 고정 워크플로 5개(전투 카드·이벤트·지도·상점 강화·수집 목록)는 요청/응답 전체 동일성 검사 통과, 정적 토큰 증가 0%. 새로운 네 사례는 각각 증가 5% 이하를 assert한다. 일반적인 모든 손패·모델에 대한 절감률을 보장하는 수치는 아니다. 작은 요약에서도 다른 필수 정보가 필요하면 상세 조회를 해야 한다.

완료: 소스 개선·Java/Node 회귀·독립 검토·비용 보고서·커밋 후보 분할표. 설치본 교체·훅 활성화·실제 플레이·실제 모델 A/B·Git 커밋/푸시는 수행하지 않았다. 원래 미커밋 작업과 새 기준선을 보존했다.
