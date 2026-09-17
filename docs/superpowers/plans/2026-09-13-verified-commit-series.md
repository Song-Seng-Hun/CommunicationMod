# 검증된 로컬 커밋 분할 기록

2026-09-13. 이번 작업은 기존 변경의 이력 구성과 재검증이다. 새 게임 기능이나 새 관찰자 추상화를 추가하지 않았다.

## 원본과 분리 브랜치

- 원본: `E:/dev/gamemcp/sts`, `codex/communication-dev`, 기준 HEAD `1d90e9f0fee9bd4d55b48116b41fc0111b6a45ae`.
- 분리 작업 트리: `E:/dev/gamemcp/sts/target/verified-commit-series`.
- 결과 브랜치: `codex/verified-commit-series`. 원본으로 병합하거나 원본 인덱스를 정리하지 않았다.
- 보존 스냅샷: `E:/dev/gamemcp/sts/target/commit-series-20260913`.
- 스냅샷의 `files/`와 `manifest.json`은 추적 파일과 미추적 작업물 326개, 파일별 SHA-256을 포함한다. `index.original`, `tracked.patch`, 원본 HEAD/상태도 보존했다.
- 기존 ignored 보고서 두 개는 `reports/`에도 복사했다. 이전 백업, 설치본, 게임 데이터와 원본 `target`의 기존 산출물은 유지했다. 전체 게임 복사본을 스냅샷에 재복제하지 않았다.

## 실제 분할

| 순서 | 커밋 | 범위 | 새 검증 결과 |
| --- | --- | --- | --- |
| 기준 | `1d90e9f` | 기존 HEAD | 오프라인 Maven test 빌드 성공; npm 11/11 |
| 1 | `b8a48c1` | 지도 계획, 보스/노드 입력 안전, map 전용 공유 파일 hunk, 테스트·문서 | 새 Maven 빌드; map compatibility/planner; observer 바이트코드 24개 클래스; protocol; npm 11/11 |
| 2 | `82945c3` | 관측·강화·특수 비용/자원·선택/실행 제어와 대응 훅/fixture | 새 Maven 빌드; 지도·강화·run-usability·observer 27개 클래스·protocol; npm 11/11; 공개 설명/드로우 비공개·이벤트·전투 준비도 추가 회귀 |
| 3 | 이 문서를 포함한 MCP 통합 커밋 | context/compact, guidance, 이벤트 대기, 계측, 미등록 훅, 평가 도구, 스킬·패키징 연결 | 새 Maven 빌드와 지도·강화·run-usability·observer·protocol; npm 168/168, 실패/취소/스킵 0; 51개 기능·153개 예제·inline 6개 |

Maven에는 등록된 Java 단위 테스트가 없다(`No tests to run`). 따라서 Maven 성공을 Java 회귀 통과로 대체하지 않았다. 표의 Java 회귀는 별도 headless fixture 및 바이트코드 검사 결과다. 마지막 npm 테스트 본체 시간은 8,838.6954ms이며 에이전트 호출 지연 측정값이 아니다.

8개 후보를 그대로 나누지 않은 이유: native selection/upgrade/cost 제공자가 서로 참조하고, guidance 계약은 Java 전체와 MCP 핵심 파일을 함께 해시한다. 관련 구현·fixture·배포용 파일 복사 규칙을 함께 넣어 중간 상태를 검증했다. `guidance-contract.json`은 이번에 변경하거나 자동 repin하지 않았다. 기존 fingerprint `95551d30dfbfb852903f20cd25546edcc0a9bea467a6af1e25ce6dcc3935e7ef`, 101개 계약 파일로 통과했다.

기존 기능 문서와 계획서는 당시 전체 기능·검증 범위를 기록한 역사 자료로 보존했다. 특히 지도 문서의 MCP 설명은 1번 Java 커밋만으로 구현되었다는 뜻이 아니며, 3번 이후에 대응 MCP 구현이 포함된다. 과거 문서의 “커밋하지 않았다”도 당시 기록이고, 현재 이력 상태는 이 문서가 설명한다.

## 검증 방법과 증거

실행 도구는 `target/commit-series-tools/validate.ps1`. `map`, `native`, `mcp` 단계별로 실행했다. 로그는 스냅샷의 `baseline-*.log`, `map-*.log`, `native-*.log`, `mcp-*.log`에 있다. 모든 성공 판정은 이번 실행의 exit 0 및 로그를 기준으로 했다.

사용한 Maven은 기존 `.tools/apache-maven-3.9.11/bin/mvn.cmd`, JDK는 기존 Microsoft JDK 11이다. `mvn -o test`에 기존 로컬 게임 JAR을 `sts.jar`, `mts.jar`, `basemod.jar`로 지정했다. 게임 클래스는 컴파일·fixture·바이트코드 검사에 사용했으며 게임 부트스트랩은 호출하지 않았다. observer 검사는 새 worktree `target`에 복사된 바이트코드만 생성·검사했고 원본 JAR 해시를 확인했다.

최초 `mvn -o clean test`는 캐시에 없는 clean 플러그인 때문에 실행되지 못했다. 이를 성공으로 세지 않았다. 다운로드 대신 검증된 정확한 worktree `target` 경로를 스냅샷의 `generated-before-*`로 보존 이동한 후 빈 출력 디렉터리에서 빌드했다. 원본 프로젝트 출력은 이동하지 않았다. `map-maven-clean-unavailable.log`에 최초 오류도 남겼다.

추가 Java 회귀에는 120개 드로우 순열의 순서 은닉·원본 보존, 설명/툴팁, 이벤트 읽기/설명/선택, 전투 stale/duplicate/selection 및 dispatch 전 재확인이 포함된다. `npm test`는 실제 MCP stdio와 가짜 lifecycle/backend를 사용한다. 실제 LLM 평가 runner는 실행하지 않았다.

독립 검토 Aristotle은 각 묶음의 의존성·범위·파일 보존을 확인했고 조립 관련 차단 사항이 없다고 판정했다. 검토자가 직접 빌드를 실행한 것은 아니며, 실행 증거는 위 로그로 분리한다.

파일 무결성은 `snapshot.mjs check`로 원본 326개 파일·인덱스·HEAD·상태와 최종 소스의 SHA-256을 확인한다. `verify-tree.mjs`는 파일 목록과 Git blob을 별도로 확인한다. 기존 `core.autocrlf=true`에 따른 텍스트 줄바꿈 정규화만 허용하며 바이너리는 원문 그대로 비교한다. 인덱스 및 최종 커밋 결과는 `candidate-integrity.json`, `final-integrity.json`에 기록한다. 스냅샷 외 추가 추적 파일은 이 인계 문서 한 개뿐이다.

기본 `git diff --cached --check`는 원래 스냅샷에도 있는 `mcp-server/evaluation/guidance-fixtures.mjs:155`의 EOF 빈 줄 한 건을 보고했다. 원본 바이트 보존을 위해 수정하지 않았다. 이 정확한 기존 경고만 별도 기록하며, 기본 whitespace 검사가 완전히 통과했다고 주장하지 않는다. 테스트나 계약 검증 조건은 완화하지 않았다.

## 명확히 남은 범위

- 정적 토큰 보고서는 보존했으며 이번 이력 정리에서 절감률을 새로 측정하거나 개선했다고 주장하지 않는다.
- `NativePanelObserver`는 Encode/Ghostflame 패널용이다. Stasis/orb, 카드 소켓/수정자, 자원, 입력 dispatch를 이 인터페이스로 강제 통합하지 않는다.
- 합성 fixture·설치 JAR 메서드 검사·정적 토큰 검증은 실제 플레이 수용이나 실제 에이전트 가속의 증거가 아니다.
- 원격 push, PR, 원본 브랜치 병합, 설치본 교체, 훅 활성화, 하트비트 생성, 실제 게임 실행·조작, 실제 모델 A/B는 하지 않았다.
- 실제 모델 A/B는 가상 서버 전용 실행 허용과 새 예산이 별도로 필요하다. 실측 부족 시 속도 개선 판정과 기본 설치본 교체를 보류한다.
- 새 clone에는 ignored 로컬 Maven/게임 JAR/측정 baseline이 포함되지 않는다. 과거 `measure-feedback.mjs` 재현에는 별도 보존된 `target/feedback-baseline-20260913`이 필요하다.

결과 브랜치와 worktree는 보존한다. 원본 dirty checkout은 의도적으로 그대로 남겨 두었으므로, 이후 통합 시 기존 변경을 덮어쓰는 reset/checkout을 사용하지 않는다.
