# Downfall 상황별 도구 예제: 선택적 컨텍스트 설계

작성: 2026-09-12. 상태: 혼합형 방향 승인 후 작성한 설계; 구현 승인 전 검토본.

## 1. 목적과 범위

현재 Downfall 플러그인의 모든 **노출된 기능**에 사용 상황, 필수 관측, 대표 one-shot, 대조 few-shot, 중단 조건을 제공한다. 모든 Downfall 모드 기능이 지원된다는 뜻은 아니다. 예제는 행동 판단을 돕는 자료이며 실행 권한이나 게임 사실의 출처가 아니다.

승인된 혼합형: **현재 상황의 짧은 목차 + 대표 예제 최대 1개; 추가 예제는 필요한 조각만 조회.** 모든 예제 상시 주입은 컨텍스트 증가, 모든 예제 조회 전용은 왕복 증가 때문에 채택하지 않는다. Embedding-friendly 구조를 만들되 초기 구현은 로컬 결정적 선택이다. 외부 embedding 서비스·모델 다운로드·새 MCP 도구는 추가하지 않는다.

이번 산출물은 설계 문서뿐이다. 코드·설치본·전역 모델 설정·게임 로직·세이브 변경, 실제 게임 실행/연결/조작, 모델 평가, 커밋·푸시는 하지 않는다. 이전 호출 지연 평가의 채택 보류를 해제하지 않는다.

## 2. 현재 계약과 설계 경계

- 기준: `mcp-server/src/index.ts`, `context.ts`, `format.ts`, 플러그인 `downfall-play/SKILL.md`, Java 관측·행동 제공자. 과거 문서의 `section` 예제는 현재 API 근거로 쓰지 않는다.
- 기존 도구 6개와 입력 스키마 유지. `sts_get_context`는 현재 `session_id/state_id`, `refs` 1–8개, 공통 `offset`, `limit` 1–30, `json|compact`를 사용한다.
- JSON 호출 키·자료형 유지. 간결한 문장과 표는 설명에 사용한다. compact는 기존 손실 없는 TOON/JSON 선택기에 맡기며 작은 예제는 JSON 기본값이다.
- 새 `guidance`는 아래에 정의하는 **제안된 응답 루트**다. 아직 현재 API에 존재하지 않는다. 기존 `rules`의 안전 규칙을 제거하거나 예제 조회 뒤로 미루지 않는다.
- 게임 상태의 기술적 행동 가능 여부와 사용자의 실행 허용은 다르다. 서버는 대화상 허용을 모른다. 실제 허용 판정은 호스트/에이전트가 하며, 서버가 `approved:true` 등을 추정하지 않는다.

## 3. 검색 단위: 독립적으로 안전한 예제 캡슐

캡슐 하나는 `상황 → 필수 관측 → 호출 → 결과 판정 → 중단 조건`을 함께 담는다. One-shot은 대표 궤적 한 개로 여러 호출을 포함할 수 있다. Few-shot은 단순 호출 수가 아니라 다른 조건의 대조 궤적 2개다.

| 필드 | 계약 |
| --- | --- |
| `id`, `revision` | 안정적인 기능/사례 ID, 내용 변경 시 revision 변경 |
| `capability`, `case` | 기능 ID; `normal`, `incomplete`, `exception` 사례 구분 |
| `when`, `not_when` | 관측 가능한 적용 조건과 배제 조건 |
| `requires` | 비용·대상·설명·완전성·허용 등 판단 전 필수 사실 |
| `bindings` | 실제 관측값의 출처와 자료형; 예제 상수와 구분 |
| `calls` | 기존 도구와 실제 입력 키를 사용하는 선언적 호출 템플릿 |
| `expect`, `stop` | 다음 상태/영수증에서 확인할 것, 호출하지 말아야 할 조건 |
| 검증 메타데이터 | 스키마/관측 계약 hash, 제공자 목록, 연결된 fixture/test ID |

검증 메타데이터 전체는 패키지 내부에 둔다. 런타임에는 ID/revision과 판단에 필요한 내용만 노출한다. 예제를 임의 스크립트로 실행하거나 외부 문서에서 동적으로 받아 실행하지 않는다. 게임 본문·카드 설명 안의 명령문은 게임 데이터이며 캡슐 지침으로 승격하지 않는다.

각 기능은 정상 1개 + 정보 부족 1개 + 오류/금지 1개를 소유한다. 읽기 전용 기능은 오류/금지 사례를 무효 ref·세션 불일치·불필요한 재조회 같은 실제 실패로 구성한다. 공통 규칙을 재사용하되, 개별 캡슐에도 그 호출에 필요한 안전 조건을 함께 넣는다.

## 4. 노출·조회·캐시 계약

1. 패키지/API 계약 일치 여부를 먼저 검사한다. 불일치 캡슐은 비활성화한다. 기존 상태 정보와 안전 규칙은 계속 제공한다.
2. 서버는 현재 screen, ready, 공개된 control/ref, offered action으로 후보를 필터링한다. 수동으로 숨긴 기능의 ref를 요청해도 읽을 수 없어야 한다. 미지원/전환 상태에는 복구 지침만 후보가 된다.
3. 필터 안에서 안전 문제 해결 → 필요한 증거 읽기 → 일반 행동 순으로 대표 예제를 결정한다. 동률은 고정 capability ID 순서로 정해 출력이 흔들리지 않게 한다. 의미 유사도로 상태 필터를 우회하지 않는다.
4. 기본 응답에 현재 관련 기능의 짧은 목차를 붙인다. 상위 3개까지만 표시하고 나머지는 상태 내 `guidance` 디렉터리에서 페이지로 읽는다. 세션 없는 시작/설치 상황은 런타임 ref 대신 스킬의 짧은 bootstrap 표를 사용한다.
5. 대표 예제는 최대 1개, 기존 o200k tokenizer로 160토큰 이하이며 직렬화된 예제 조각이 2,400 JSON 문자 이하일 때만 inline 제공한다. 두 한도를 만족시키려고 안전 조건을 삭제하지 않는다. 넘으면 제목·ref만 제공한다. 이 수치는 초기 설계 상한이며 속도 개선 실측치는 아니다.
6. 예제 ref 구조는 `guidance/<capability>/<case>`. 현 상태에서 관련 있는 capability의 정상/대조 사례만 조회 가능하다. 대조 사례는 **현재 사실이 아닌 조건부 반례**임을 표시한다. 전역 전체 예제 덤프는 없다.
7. 캡슐은 전체가 2,400 JSON 문자에 들어오는 독립 단위로 저작한다. 초과하면 빌드 검증 실패; 안전 내용을 잘라 페이지로 보내지 않는다. 디렉터리는 기존 페이지 규칙을 사용한다. 일반 게임 문서의 text/field 페이지 규칙은 변경하지 않는다.
8. 같은 상태에서 이미 읽은 캡슐을 다시 읽지 않는다. 캡슐의 정적 지식은 revision/계약 hash가 같으면 재사용할 수 있지만 동적 사실·행동 ID·ref는 현재 session/state로 재검증한다. 이 캐시는 에이전트 컨텍스트 안의 재사용이며 서버가 읽음 여부를 추측하지 않는다.
9. 새 예제 선택·revision은 `view_id` 계산 대상이다. 내용이 달라졌는데 `known_view`에 unchanged를 반환하면 실패다. 단순 알려진 예제 재노출을 줄이려고 새 입력 필드나 서버측 숨은 읽음 상태를 추가하지 않는다.

서버 후보를 받은 호스트/에이전트는 사용자 허용 범위를 추가로 검사한다. 향후 embedding 검색을 도입해도 로컬 상태·버전 필터와 호스트의 허용 범위 판정을 통과한 후보 안에서만 순위를 정한다. 미허용 행동의 정상 실행 예제를 선택하지 않으며 필요하면 해당 기능의 호출 금지 사례를 사용한다.

`sts_act`가 반환한 ready 상태가 충분하면 다음 행동 판단에 그대로 사용한다. 예제를 읽기 위한 필수 왕복을 모든 호출 앞에 삽입하지 않는다. 예제가 필요 없으면 목차를 읽고 바로 현재 증거로 판단한다. 필수 진행 알림·이벤트 설명·위험 행동 확인은 생략하지 않는다.

## 5. 호출 템플릿과 바인딩

아래는 설계용 표기다. `$...` 문자열을 실제 API에 보내지 않는다. 구현 시 `bindings`를 fixture 관측값으로 치환한 뒤 현재 도구 스키마로 검증한다. 문서 예제는 실제 게임에서 실행하지 않는다.

- `$s`, `$n`: 마지막 유효 관측의 session 문자열, state 정수. 서로 다른 응답에서 섞지 않는다.
- `$a`: 현재 offered actions에서 선택한 행동의 정확한 ID. 접두사/UUID/배열 위치로 ID를 만들어내지 않는다.
- `$p`: 관측된 인자 명세로 검증한 객체. 명세가 정확히 빈 객체이면 `{}`; 누락/null은 빈 객체로 간주하지 않는다.
- `$refs`: 현재 목차/child toc/명시적 ref 필드에서 읽은 문자열 배열. 필요한 서로 다른 offset은 다른 호출로 나눈다.
- `$r`: 불확실한 요청의 실제 receipt/request ID. 다른 요청 ID로 바꿔 재전송하지 않는다.
- `$view`: 보유한 직전 summary의 view_id. `$cardRef`, `$listRef`, `$returnedCardRef`는 관측된 ref 문자열, `$next`는 해당 조각이 반환한 next_offset 정수다.

```text
Q(refs): sts_get_context({session_id:$s,state_id:$n,refs:$refs})
A(action,args): sts_act({session_id:$s,state_id:$n,action_id:$a,arguments:$p})
S(): sts_get_state({})
R(): sts_get_request({request_id:$r})
```

Q/A/S/R은 아래 표의 문서 약어일 뿐 새 MCP 도구가 아니다. 실제 제공 캡슐은 약어 해석을 위한 별도 조회 없이 전체 도구명·입력 키를 포함한다. `A`에는 관련 설명·비용·대상·경고 확인과 사용자 허용이 선행한다. `parameters:{}`만으로 이 조건을 충족하지 않는다.

## 6. 전체 기능별 상황과 one-shot/few-shot 명세

표 각 행은 대표 예제 1개와 대조 예제 2개의 내용 계약이다. 화살표는 앞 결과를 확인한 뒤에만 다음 호출을 판단한다는 뜻이며 자동 연속 실행을 의미하지 않는다. `중단`은 추측 행동 금지이며 필요한 읽기/사용자 확인은 허용한다.

| 기능 ID / 현재 근거 | 사용 상황·필수 관측 | One-shot 정상 궤적 | Few-shot: 부족 / 오류·금지 |
| --- | --- | --- | --- |
| lifecycle.status / `sts_game_status` | 실행/연결 상태 확인만 요청 | `sts_game_status({})` → 확인된 상태 보고 | 연결 불명: 진단 정보만 보고 / 게임 꺼짐: 조회 요청만으로 시작 금지 |
| lifecycle.start / `sts_start_game` | 명시적 실행/플레이 요청; 준비된 테스트 사본 | `sts_start_game({wait_ms:15000})` → 상태 확인 | starting/launch lock: `sts_game_status({})`, 재실행 금지 / 다른 런타임·제어자 충돌: 중단, 프로세스 종료 금지 |
| lifecycle.setup / 스킬 | 준비본 없음/변경됨 | 상태 확인 → 업데이트 요청 범위 확인 → 저장 보호 조건 보고 | 일반 실행 요청만 있음: 재빌드 중단 / 다른 세이브 복사·Steam 변경 금지; setup은 새 게임 도구가 아님 |
| state.decision / `sts_get_state` | 현재 판단 또는 마지막 요약 복구 | `S()` → ready/목차 확인 | 이전 view 보유: `sts_get_state({known_view:$view})` → unchanged면 이전 요약 유지 / 요약 분실: known_view 없이 S; unready이면 A 금지 |
| context.read / `sts_get_context` | 누락된 필수 사실; 현재 refs | `Q(필요한 refs 1–8개)` → 완전성 확인 | 페이지 있음: 반환 next_offset으로 같은 ref 조회 / stale/무효 ref: S 후 재발견; 전체 덤프 금지 |
| action.simple / `sts_act` | 현재 단순 행동, 인자 명세 정확히 `{}`, 판단 충분 | `A(관측 ID,{})` → receipt와 반환 state 확인 | 다른 details_required 있음: Q 후 판단 / 인자 명세 누락/null: `{}` 추정 금지 |
| action.parameters / 행동 명세 | 현재 행동에 인자 있음 | `Q(parameters_ref)` → 명세·증거 검증 → `A(ID,검증 객체)` | 명세 child toc: 필요한 자식 조회 / 예제 인자·과거 상태 ID 복사 금지 |
| combat.play / `run.play.*`, combat 제공자 | 완전한 hand, 현재 rendered 비용·자원·효과·target_playability | `Q(카드와 대상/자원 refs)` → 합법 offered play의 A → 새 상태 사용 | X/별도 자원 비용 불완전: 해당 공개 refs 조회 / unrendered·대상 불가·불완전 hand: A 중단 |
| combat.end / `run.end_turn` | 남은 선택·효과와 종료 영향 확인 | 필요한 Q → `A(관측 end-turn ID,{})` → 새 턴 확인 | 미확인 필수 상태: Q / 팝업·전환·ready=false: 종료 호출 금지 |
| cards.inspect / CardObservation, upgrade | 특정 카드 설명·키워드·표준 강화 비교 | `Q(선택 카드 ref)` → 완전한 단일 조각 사용 | 큰 카드: child refs/pages로 비교 완성 / 표준 강화 preview를 이벤트·획득 효과 예측으로 사용 금지 |
| resources.public / mechanics, collection | 현재 캐릭터의 공개 자원·비용 관련 패널 | `Q(노출 mechanics ref)` → native 수치/완전성으로 판단 | nested incomplete: 관련 조각 추가 조회 / 미지원 자원을 0으로 추정하거나 숨은 값 조회 금지 |
| cards.collections / deck,piles,collection | 덱·공개 pile·collection 특정 항목 찾기 | Q 목록 → next_offset 페이지 → 반환된 특정 카드 ref의 Q | 서로 다른 페이지: 개별 Q / `order_visible=false`: 반환 순서로 다음 드로우 추론 금지 |
| reward.take / `run.reward.*`, `run.card_reward.*` | 효과·상호배타·특수 선택 모드 확인 | Q 보상/카드 → A 선택 → 반환 상태 확인 | 긴 목록/추가 강화 정보: Q / proceed·skip·bowl: 포기/변환 효과 확인 없이 호출 금지 |
| reward.boss_chest / `run.boss_relic.*`, `run.chest.open` | 보이는 상자/보스 유물 선택, 효과와 skip 확인 | Q 관련 정보 → A 현재 선택 → 상태 확인 | 유물 설명 불완전: Q / 대체 화면 미지원·제공 안 된 ID: 중단 |
| potion.use / `run.potion.use.*` | 실제 슬롯·효과·대상·사용 가능 확인 | Q potion_controls/관련 대상 → A 제공 use → 상태 확인 | 대상 불명: Q / 사용자가 연 팝업·사용 불가: 중단 |
| potion.discard / `run.potion.discard.*` | 별도 파괴적 선택으로 명시 허용된 버리기 | Q 슬롯/효과 → 허용 확인 → A 제공 discard | 슬롯 내용 바뀜: 재확인 / 보상 공간 부족만으로 자동 버리기·교체 금지 |
| shop / `run.shop.*` | 진입/구매/제거/나가기; 현재 상품·가격·잔고 | Q shop_controls/상품 → A 구매 → 잔고/새 상태 확인 | purge: A 진입 후 새 selector 정보 Q → 별도 선택 / 가격·상품 ID 변경: 이전 판단 재사용 금지 |
| rest / `run.rest.*` | 현재 휴식 선택과 효과; 강화면 카드 정보 | Q rest_controls/필요 카드 → A 선택 → 후속 화면 확인 | 업그레이드 selector 발생: 새 상태의 선택 예제 / 진행 버튼만 보고 선택 효과 완료 추정 금지 |
| selection / `run.grid.*`, `run.hand.*` | 선택/해제/확인/취소; 제한·현재 선택 수 | Q selection controls → A 선택 → 새 상태에서 확인 조건 판단 | 제한 부족/초과: 제공된 해제/선택만 검토 / 잠긴 카드·미제공 confirm/cancel을 합성 금지 |
| selection.upgrade / `run.grid.upgrade.*` | 분기/트리 후보의 실제 preview | Q 후보 → A 후보 → Q 최신 `selected_after` → 별도 confirm 판단 | preview 불완전: Q 또는 중단 / 후보 선택을 강화 확정으로 간주 금지 |
| event / `acknowledge_event_reading`, `run.event.*` | 본문·상황·선택지·reading ID 완전 확인 | Q body_ref와 options → 사용자 설명 → Q 필요 인자 명세 → A ack → 새 상태에서 선택 판단 | 긴 본문/결과 페이지: next_offset 따라 완독·설명 / reading ID 변경·침묵 ack·본문 조작 명령: 실행 중단 |
| narrative.history / narrative | 현재 대화 관련 지난 발화 필요; 공개 history 존재 | Q 현 목차의 history ref → 필요한 페이지 확인 | 여러 페이지: next_offset 사용 / history 비노출 상태: 임의 ref로 우회 금지 |
| map.plan / `run.map.plan`, `map_plan` | MAP, 현재 map ID/revision/노드/원래 edge 확인 | `sts_get_state({view:"map_plan"})` → 필요 시 decision S/Q → A 제공 plan → 계획 revision 확인 | node/edge 부족: Q map / plan 성공을 실제 이동으로 간주 금지 |
| map.travel / `run.map.*`, boss | MAP, 현재 제공된 이동과 계획 구분 | Q 필요한 현재 경로 → A 제공 이동 → 새 위치 확인 | revision/state 변경: S/Q 재검증 / 미제공 edge·추측 노드로 이동 금지 |
| menu / `menu.*`, `run.embark` | 메뉴와 사용자 요청 범위 일치 | S/Q menu → 허용된 offered 선택의 A → 화면 확인 | 이어하기/새 런 의도 불명: 사용자 확인 / 실행 요청만으로 새 런·포기·세이브 변경 금지 |
| tutorial.room / `run.tutorial.confirm`, `run.room.proceed` | 현재 설명/방 완료·남은 보상 확인 | Q 현재 tutorial/room·navigation → A 제공 진행 → 상태 확인 | 남은 선택/보상 불명: Q / 진행으로 보상을 버리는 경우 확인 없이 호출 금지 |
| recovery.stale / state 검증 | stale rejection, session 변경 | S → 현재 refs 재발견 → 동적 사실 재검증 | 예전 요청 pending: R로 먼저 확인 / 과거 ID/ref/판단으로 행동 재전송 금지 |
| recovery.receipt / `sts_get_request` | `unknown` 또는 `applied_waiting`, 실제 request ID | R → S → receipt와 현재 상태를 함께 판정 | receipt missing: unknown 유지 / 성공/실패 단정·새 ID로 A 재전송·재시작 통한 재굴림 금지 |
| recovery.unsupported / ready·control gates | 미지원 화면·수동 입력·연결 충돌 | S 또는 상태 조회 → 확인된 차단 원인 보고 | 일시 전환: bounded 상태 조회 / 추측 클릭·콘솔 변경·다른 제어자 강제 종료 금지 |

커버리지는 위 표의 행 수로만 판정하지 않는다. 구현 시 6개 도구, 모든 현재 action family, control root, 지원되는 특수 비용/자원 계약을 registry와 대조한다. 표의 묶음 안에서도 purchase 종류, 선택/해제/confirm, normal/branch/tree, skip/bowl 등 **의미가 다른 하위 기능은 각각 fixture를 가진다.** 동적 UUID/인덱스별 중복 예제는 만들지 않는다. 미분류 신규 제공자가 생기면 coverage 검증이 실패해야 한다.

## 7. 대표 캡슐의 실제 호출 모양

다음 JSON 모양은 템플릿으로, `$n`은 실제 정수로, 나머지 변수는 지정된 관측값으로 바인딩해야 한다. 런타임 저작물은 타입 있는 bindings를 사용하며 이 문서의 기호 문자열을 그대로 전송하지 않는다.

```text
case: action.simple/normal
when: ready; offered action; parameters exactly {}; necessary facts read; user scope permits.
call: sts_act({"session_id":$s,"state_id":$n,"action_id":$a,"arguments":{}})
expect: check receipt; use returned ready state if sufficient. No redundant sts_get_state.
stop: missing facts, unready, not offered. Empty parameters ≠ safe/fully understood action.
```

```text
case: cards.inspect/normal
when: explicit current card ref; descriptions/keywords/upgrade needed.
call: sts_get_context({"session_id":$s,"state_id":$n,"refs":[$cardRef]})
expect: complete fragment → compare. Child toc/next_offset → read needed continuation.
stop: missing effects/cost/target facts; standard upgrade preview is not an event outcome.
```

```text
case: context.read/incomplete
when: previous public-list fragment supplied next_offset; same session/state.
call: sts_get_context({"session_id":$s,"state_id":$n,"refs":[$listRef],"offset":$next,"limit":20,"response_format":"compact"})
then: sts_get_context({"session_id":$s,"state_id":$n,"refs":[$returnedCardRef]})
expect: locate card using returned ref; decode format as returned; default JSON for card.
stop: stale state, missing continuation, hidden order inference. Never guess the card ref.
```

```text
case: recovery.receipt/normal
when: last action outcome unknown|applied_waiting; actual request_id known.
call: sts_get_request({"request_id":$r})
then: sts_get_state({})
expect: reconcile receipt and current state. Missing receipt remains unknown.
stop: unresolved outcome → report/inspect; never retransmit sts_act, even with new request_id.
```

긴 이벤트나 분기 강화처럼 여러 판단이 필요한 사례도 자동 실행 체인으로 만들지 않는다. 각 응답 후 안전 조건을 다시 확인한다.

## 8. 최소 변경 경계

- 예제 registry/selector/검증을 별도 작은 모듈로 둔다. 기존 `context.ts`에는 현재 scope를 전달하고 선택 결과를 투영하는 연결만 추가한다.
- `guidance` ref 해석은 기존 state 검증·허용 루트·prototype 방어를 그대로 통과한다. 게임 observation에 정적 문서를 섞지 않는다.
- 스킬은 bootstrap, 보편 안전 규칙, 상황 목차 사용법만 보유한다. 모든 예제를 SKILL.md에 복사하지 않는다.
- 캡슐/패키지 버전 불일치는 예제만 비활성화하고 기존 rules를 유지한다. 누락된 게임 사실을 예제로 대체하지 않는다.
- 기존 카드 조각 한도·페이지 복귀, 빈 인자 요약·parameters_ref, 이벤트 body_ref, 재전송 방지, compact 무손실 계약은 변경하지 않는다.

## 9. 검증과 채택 기준

### 비용 없는 로컬 검증

- Coverage: 모든 도구/action family/control의 예제·사용 조건·두 대조 사례 연결. 의미가 다른 하위 기능별 fixture 누락 실패.
- Schema: 타입 있는 바인딩 후 실제 입력 스키마 검증. `section`, 옛 event 필드명, 복사된 고정 행동 ID, 잘못된 offset/자료형 검출.
- Meaning: 필수 사실·부정·조건·수치·행동 결과가 압축 전후 동일. null/미확인/0/false 구분, 중첩 incomplete 보존. 한국어·영어 설명 포함.
- Safety: 가상 백엔드에서만 궤적 검증. stale·unknown·missing receipt·unready·무권한·자동 discard·숨은 정보·악성 게임 본문 사례는 금지 호출 0건.
- Retrieval: 비관련 상태의 예제 미노출/직접 ref 차단, 세션 불일치, 캡슐 계약 불일치, 페이지/UTF-16 경계, 예제 revision 변경 시 view_id 변경.
- Budget: inline 최대 1개/160토큰, 캡슐 2,400 JSON 문자, 상태 목차 3개 상한; 나머지 관련 사례의 페이지 도달성. 안전 조건을 잘라 상한을 통과시키지 않는다.
- 기존 테스트 전체와 신규 테스트 실행. 여기 적힌 검증은 후속 구현의 통과 조건이며, 현재 실행 완료 주장이나 의미 손상 0의 증거가 아니다.

### 실제 에이전트 평가와 설치

현재 설계 승인으로 추가 모델 사용을 시작하지 않는다. 후속 승인된 예산에서 예제 없음/대표 예제/대조 예제 조회를 비교하고, 조회 왕복과 예제 자체까지 포함한 전체 과제 토큰·시간을 기록한다. 정적 토큰 감소나 scripted 통과만으로 에이전트 가속을 선언하지 않는다.

기존 호출 지연 계획의 채택 조건은 유지한다: 동일한 확인된 모델/추론 강도, 가상 백엔드만, 의미·안전 전부 통과, 비교 가능한 호출 간 지연 중앙값 20% 이상 감소, 전체 시간 중앙값 악화 없음, 시나리오별 시간 10% 초과 악화 없음, 시나리오별 전체 토큰 증가 15% 이하. 호스트 이벤트와 MCP 대리 지표는 구분하며 p90은 참고치다. 예제 추가로 사실 확인이 생략된 실행은 빠르더라도 실패다.

실측 부족·기준 미달·호스트 실행 제약이면 속도 개선 판정 및 기본 설치본 교체를 보류한다. 기존 백업/설치 검증 경계를 유지하고 별도 승인 없이 평가 한도를 새로 부여하거나 우회하지 않는다.

## 10. 문서 검토 후 다음 단계

사용자가 이 설계를 검토한 뒤 구현 계획을 작성한다. 구현 순서는 기능 inventory/fixture 계약 → 캡슐과 상태 selector → 기존 context 연결 → 스킬 최소 갱신 → 회귀·안전·예산 검증이다. 실제 모델 평가/설치는 별도 실행 허용과 기존 채택 조건을 충족한 경우에만 진행한다.
