# Downfall 이벤트 대기 + 최소 훅 구현 기록

날짜: 2026-09-12. 범위: 현재 체크아웃의 소스 후보, 가상 백엔드, 정적 비용 검증.

실제 에이전트 가속·게임 검증·설치 완료를 뜻하지 않는다. 모델/추론/전역 설정, 설치 캐시, 게임/세이브는 변경하지 않았다. 훅 등록·활성화, 하트비트/예약 작업/상주 프로세스, 실제 모델 평가, Git 커밋·푸시도 수행하지 않았다.

## 기준선과 변경 범위

수정 전 스냅샷: `target/agent-latency-baseline-UTkXr1`. 소스·기존 dist JS·스킬은 원래 `manifest.json`의 SHA-256으로 고정했다. 테스트·스크립트·평가 자료·package 파일·기존 guidance bundle도 추가 보관했다. 추가 보관분이 원래 manifest에 포함되었다고 간주하지 않는다. 최종 비용 보고서는 측정에 관련된 기준선/후보 소스·dist·테스트·스크립트·훅·계약·스킬의 해시를 추가 기록하고 측정 시작/끝 일치를 검사한다. 기존 미커밋 변경과 이전 백업은 보존했다.

이번 변경 파일:

- `mcp-server/src/session.ts`: projection predicate 기반 이벤트 대기, 절대 제한/신선도 타이머, 취소·연결 종료·대기자 정리. 동일 heartbeat는 신선도만 갱신한다. pending 설정 시 대기자에 알린다. 행동 재전송/자동 행동 없음.
- `mcp-server/src/index.ts`: 기존 get-state 입력의 known_view/wait_ms 연결, SDK 취소 연결, 6개 도구 공통 선택적 내부 계측. 등록 도구/스키마/설명은 기준선과 동일함을 실제 stdio discovery로 비교했다.
- `mcp-server/src/metrics.ts`: 기본 비활성, 고정 필드/경로, 비동기 제한 큐, 3×1 MiB 순환, 프로세스별 식별자, 기록 실패 격리.
- `mcp-server/hooks/session-start.mjs`, `hooks.template.json`: 미등록 복원 템플릿, 순수 정적 핸들러와 로컬 계약 검증 어댑터.
- `mcp-server/scripts/build-guidance.mjs`, `evaluation/guidance-contract.json`: 기존 예제 재검증, 수동 계약 검토 기록, 빌드 시 훅 digest/revision/token 검증. 예제 목록/본문 확대 없음.
- `plugin/downfall-agent/skills/downfall-play/SKILL.md`: 실제 대기에만 15초 known-view 호출, 연속 두 번 unchanged면 종료/보고하는 짧은 규칙.
- `mcp-server/test/{state-wait,session-hook,metrics,efficiency,mcp,agent-skill}.test.mjs`: 새 회귀 및 기존 검증 확장.
- `mcp-server/scripts/measure-agent-efficiency.mjs`, `mcp-server/README.md`, 이 문서: 재현 가능한 비용/검증 인계.

`context.ts`, 카드/이벤트/예제 데이터, Java 행동 제공자와 게임 로직은 이번 작업에서 수정하지 않았다. 전체 dirty diff는 이번 변경 목록이 아니다.

## 동작과 안전 경계

| 조건 | 결과 |
| --- | --- |
| wait_ms=0 | 즉시 반환 |
| known_view 없음 | ready 대기; 절대 제한·연결 종료·신선도 만료에서 종료 |
| 요청 projection과 known_view 불일치 | 최신 결과 즉시 반환 |
| 일치하고 wait_ms>0 | view/연결/신선도 변경 또는 절대 제한까지 대기 |
| 동일 heartbeat | 신선도 타이머만 갱신; 요청 제한 연장 없음 |
| timeout, 변화 없음 | 기존 unchanged 형식 |
| ready=false | 행동 허용 아님 |
| unknown/applied_waiting | 원래 request_id 조회; 새 ID로 반복하지 않음 |

단일 상태 ID만으로 동일성을 판단하지 않는다. map_plan predicate는 기존 경량 projection만 사용한다. 읽기 취소는 대기자를 제거하며, 진행 중인 게임 행동을 재실행하거나 취소된 것으로 단정하지 않는다. 기존 receipt+다음 상태, single-flight, request ID 중복 차단은 유지한다.

## 선택 기능: 기본 비활성

### 복원 훅

`SessionStart`의 `startup|resume|compact`에 한정한 **미등록 템플릿**이다. `DOWNFALL_AGENT_CONTEXT=1`과 정확한 canonical 저장소 cwd가 함께 맞아야 한다. 상대 명령은 저장소 루트에서만 유효하다. 이 파일을 활성 설정으로 복사하지 않았다. 향후 활성화는 별도 승인과 [공식 훅 신뢰 절차](https://learn.chatgpt.com/docs/hooks)가 필요하다.

출력은 검증된 예제 revision, 필요한 guidance 조회, 동적 사실 재확인, 불확실한 요청 재전송 금지뿐이다. **본문 60 o200k 토큰**, 상한 128. static envelope를 포함하면 이번 비용 계산에는 76토큰을 추가했다. tokenizer는 빌드/평가에서만 사용하며 훅 실행마다 초기화하지 않는다. opt-in 없음/다른 cwd/잘못된 이벤트/누락·불일치 artifact는 빈 출력. 게임 본문·카드·대화 기록·사용자 허용·과거 상태, 네트워크/MCP/LLM/게임 실행은 읽거나 수행하지 않는다.

### 내부 계측

`COMMUNICATIONMOD_METRICS=1`일 때만 configured workspace의 `target/agent-efficiency/metrics`에 기록한다. 인자·본문·토큰·사용자/게임 상태 ID 대신 도구명, 내부 호출/프로세스 ID, 시작/완료, 연결/대기 시간, 응답 UTF-8 byte 수, 제한된 오류 분류만 저장한다. SDK 문자열 취소 사유도 원문을 남기지 않고 cancelled로 분류한다.

행당 최대 1 KiB, pending/in-flight 합계 256, 파일 3개×1 MiB. 프로세스 간 쓰기 잠금 충돌/큐 초과는 진단을 버린다. 실패가 도구 결과·게임 재시도를 유발하지 않는다. 심볼릭 링크/하드 링크/비정상 경로는 fail-closed한다. OS 차원의 적대적 동시 ancestor 교체까지 방어하는 보장은 아니다. 종료 시 버퍼 손실 가능. crash로 인한 빈 `metrics.writer-lock`은 모든 writer가 멈춘 것을 확인한 뒤에만 별도 정리해야 한다. 자동 stale-lock 제거는 없다.

## 검증 기록

기준선 전체 테스트: **131/131**. 후보 최종 `npm test`: **163/163**, 실패/취소/스킵 0, exit 0, 테스트 본체 약 14.7초. 전체 빌드가 51개 기능·153개 예제를 다시 검증했다. 기존 개수를 재사용하지 않았다. 스킬 quick_validate도 통과했다. Node MockTimers experimental 경고는 출력되었으며 테스트 실패는 아니다.

TDD: 새 이벤트 대기 테스트는 구현 전 6개 실패, 계측·훅·스킬·보고서도 각 기능 부재/오류를 확인한 뒤 구현했다. 독립 SPEC 검토의 세 결함은 실패를 재현한 뒤 수정했다: (1) legacy unready 대기의 신선도 만료 미종료, (2) 실제 MCP SDK 문자열 취소의 operation 오분류, (3) 두 clock read 사이 4999→5000ms 경계에서 신선도 타이머가 빠지는 경합. 남은 시간을 한 번 계산하여 만료 종료/타이머 등록을 분기한다. 최종 대기 10개와 예제 catalog 13개, 총 **23/23**으로 재검증 후 계약 pin을 수동 갱신하고 전체 회귀를 통과했다. 추가로 known-view ready/remote pending/receipt 전환과 abort listener 제거를 검증했다.

커버리지: heartbeat·동일 ID 내용 변경·절대 제한·5초 만료·새 세션·연결 종료·동시 대기/취소·등록 재검사·예외 정리, map 비관련 getter 미접근, 기존 compact/카드/이벤트/예제 원자성, stale/unknown/missing receipt/무허용/자동 discard 금지, 가짜 lifecycle 시작, 훅 gate/불일치/악성 본문 비주입, 계측 OFF 무파일/ON 제한·개인정보 배제·기록 실패 격리. 실제 모델의 지침 준수 능력까지 증명하는 테스트는 아니다.

독립 검토 완료: Halley의 최종 SPEC 판정 **PASS**, 세 결함 모두 종료. 기준선 61개/후보 70개 해시를 독립 대조하고 결과/문서 일치를 확인했다. Fermat의 별도 품질·안전 검토는 **열린 지적 없음**이며 핵심 테스트 **36/36**을 독립 실행했다. 전체 163/163은 구현 담당자의 실행 증거와 구분한다. 검토자들은 공유 파일 수정·빌드·게임/모델 실행·설치·커밋을 하지 않았다.

## 비용 결과

근거: 저장소 `target/agent-efficiency/report.json`. 합성 백엔드만 연결했고 예상하지 않은 게임 명령은 거부하며 dispatches=0을 검사했다.

- 고정 10초 변화 대기: 후속 조회 **10→1, 90% 감소**. 초기 조회 포함 전체 **11→2, 81.8% 감소**. 두 분모 모두 80% 목표 충족.
- 계측 OFF/ON을 별도 실제 MCP stdio 프로세스로 측정. 초기 MCP 연결 샘플은 기준 674.2ms/후보 OFF 736.3ms/ON 656.5ms. 전체 대기 구간은 각각 10115.2/10005.3/10015.3ms. 단일 샘플이며 초기 연결/프로세스 편차를 속도 개선으로 해석하지 않는다.
- ON 장기 조회 내부: 대기 10013.4ms, 연결 0.006ms, handler 총 10013.5ms. handler 완료→다음 시작은 **서버 경계 대리 지표**이며 호스트 직접 이벤트/순수 추론시간이 아니다. 같은 process_run_id만 결합하고 중첩 호출은 제외한다.
- 별도 projection/응답 구성 microbenchmark: 각 OFF/ON 5×100회, 호출당 중앙값 **0.1212/0.1465ms**. 별도로 잰 5회 flush 누적 **0.0646/96.060ms**. 네트워크·이벤트 대기 제외, fixed OFF→ON 순서·warmup 없음. 설명적 비용 샘플이지 처리 성능 채택 판정이 아니다.
- 로컬 훅 프로세스 시작+계약 파일 읽기: 3회 중앙값 **131.5ms**. Codex 호스트 훅 시간은 미측정. 매 도구마다 훅을 붙이지 않은 이유이며 기본 비활성 유지.

정적 토큰은 스킬+도구 catalog를 한 번, 전체 요청+MCP text/structured 응답을 모두 포함하고 선택적 훅 envelope를 추가했다. 기준선/후보의 요청·응답 의미/구조 동일성을 assert했다. 모델 reasoning·호스트 반복 주입·실청구 토큰은 측정하지 않았다.

| 고정 워크플로 | 기준 | 후보 기본 | 훅 포함 | 훅 포함 증가 |
| --- | ---: | ---: | ---: | ---: |
| 전투 현재 카드 | 3915 | 3954 | 4030 | 2.94% |
| 이벤트 본문 | 4388 | 4427 | 4503 | 2.62% |
| 현재 지도 경로 | 3560 | 3599 | 3675 | 3.23% |
| 상점 가격·강화 | 3499 | 3538 | 3614 | 3.29% |
| 수집 목록 마지막 카드 | 4484 | 4523 | 4599 | 2.56% |

기본 증가는 **0.87–1.11%**, 훅 포함 **2.56–3.29%**, 모두 5% 이하. 이 기준은 바로 이번 변경 직전 스냅샷이며 과거 contextual examples 이전 버전이 아니다.

## 재현과 인계 상태

저장소의 `mcp-server`에서 실행:

```powershell
npm.cmd test
node scripts/measure-agent-efficiency.mjs ../target/agent-latency-baseline-UTkXr1
```

테스트는 합성 loopback와 가짜 lifecycle만 사용한다. Windows 자식 프로세스 cleanup 테스트는 호스트 승인이 필요할 수 있다. 승인 정책을 우회하거나 실제 모델을 실행하지 않는다. 보고서 생성 중 대상 파일이 바뀌면 해시 검사가 실패한다.

- 소스/가상 회귀/정적 비용: 통과.
- 독립 SPEC 재검토: PASS, 열린 지적 없음.
- 독립 품질·안전 검토: PASS, 핵심 36/36 독립 실행, 열린 지적 없음.
- 설치·훅 활성화·실제 게임·실제 에이전트 A/B: 범위 밖, 미수행.

후속 실제 A/B에는 가상 서버 전용 실행 허용과 새 예산이 필요하다. 기존 채택 조건 유지: 의미·안전 전부 통과, 호출 간 지연 중앙값 20% 감소, 전체 시간 악화 없음, 시나리오별 시간 증가 10% 이하·토큰 증가 15% 이하. 실측 부족이면 가속 판정·기본 설치본 교체 보류. 하트비트는 별도 요청 전 생성하지 않는다.
