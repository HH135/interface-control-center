# Interface Control Center

기존 `gpt 테스트용 repo`에서 시작한 인터페이스 운영 관리 MVP입니다.
계약 등록 → SAP 시뮬레이터 전송 → 성공/실패 기록 → 실패 재처리 → 일별 현황/이력 조회를 제공합니다.

## 기술 구성

Java 21, Spring Boot 3.5.16, Gradle 8.14.3(Wrapper 포함), PostgreSQL 16,
MyBatis 3.0.5, Flyway, JUnit 5, Docker Compose, GitHub Actions.
요청에 따라 Spring Boot 3을 사용합니다. 향후 운영 적용 시 지원 정책을 확인하고 업그레이드를 계획하세요.

## 빠른 실행

Docker Engine/Desktop과 Compose v2가 있으면 Java를 별도로 설치할 필요가 없습니다.

```sh
docker compose up --build -d
docker compose logs -f app
curl http://localhost:8080/actuator/health
```

health 응답이 `{"status":"UP"}`이면 준비 완료입니다. 최초 빌드는 의존성 다운로드 때문에 시간이 걸립니다.
API는 `http://localhost:8080`에서 제공됩니다. 데이터는 `postgres_data` 볼륨에 유지됩니다.

```sh
docker compose down       # 종료, 데이터 유지
# docker compose down -v  # 모든 개발 DB 데이터 삭제(초기화가 필요할 때만)
```

Java 21로 직접 실행하려면:

```sh
docker compose up -d db
./gradlew bootRun
```

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다.
`JAVA_HOME`은 JDK 21을 가리켜야 합니다. DB 연결은 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`로 설정합니다.
기본값은 `jdbc:postgresql://localhost:5432/icc`, `icc`, `icc_local`입니다.
Compose의 비밀번호는 `.env`에 `DB_PASSWORD=...`를 지정해 변경할 수 있습니다.
기존 볼륨의 DB 비밀번호는 환경 변수만 변경해도 자동 변경되지 않습니다.

## 재현 예제

아래는 Bash 기준입니다. Windows에서는 `curl.exe` 또는 PowerShell 예제를 사용하세요.

```sh
curl -X POST localhost:8080/api/contracts -H 'Content-Type: application/json' \
  -d '{"contractNumber":"C-2026-001","customerName":"테스트 고객","amount":125000.00}'
# 응답 id를 CONTRACT_ID에 넣습니다.
CONTRACT_ID=응답의_계약_UUID
curl -X POST localhost:8080/api/contracts/$CONTRACT_ID/send \
  -H 'Content-Type: application/json' -d '{"scenario":"FAIL_ONCE"}'
# 응답 id를 MESSAGE_ID에 넣습니다. 첫 전송은 FAILED입니다.
MESSAGE_ID=응답의_메시지_UUID
curl -X POST localhost:8080/api/messages/$MESSAGE_ID/retry
curl localhost:8080/api/messages/$MESSAGE_ID/history
curl 'localhost:8080/api/reports/daily?date=2026-09-10'
curl localhost:8080/api/batches
```

PowerShell:

```powershell
$contract = Invoke-RestMethod http://localhost:8080/api/contracts -Method Post -ContentType 'application/json' -Body '{"contractNumber":"PS-001","customerName":"Demo","amount":125000}'
$message = Invoke-RestMethod "http://localhost:8080/api/contracts/$($contract.id)/send" -Method Post -ContentType 'application/json' -Body '{"scenario":"FAIL_ONCE"}'
Invoke-RestMethod "http://localhost:8080/api/messages/$($message.id)/retry" -Method Post
Invoke-RestMethod "http://localhost:8080/api/messages/$($message.id)/history"
```

`scenario`는 필수이며 `SUCCESS`, `FAIL_ONCE`(첫 실패 후 성공), `ALWAYS_FAIL`, `TIMEOUT`을 지원합니다.
TIMEOUT은 지연 없이 타임아웃 결과를 기록하는 결정적 시뮬레이션입니다. 실제 SAP 네트워크 호출은 하지 않습니다.

## API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/contracts` | 계약 등록, 201 |
| GET | `/api/contracts`, `/api/contracts/{id}` | 계약 목록/상세 |
| POST | `/api/contracts/{id}/send` | 최초 전송, 201 |
| GET | `/api/messages`, `/api/messages/{id}` | 메시지 목록/상세 |
| POST | `/api/messages/{id}/retry` | 실패 재처리, 200 |
| GET | `/api/messages/{id}/history` | 시도 순서별 이력 |
| GET | `/api/reports/daily?date=YYYY-MM-DD` | 한국 시간 기준 일별 시도 집계 |
| GET | `/api/batches` | 전송/재처리 실행 이력 |
| GET | `/actuator/health` | 앱 및 DB 상태 |

목록은 `limit`(기본 50, 최대 100), `offset`(기본 0)을 지원합니다.
계약 번호는 공백 제거 후 고유하며 금액은 0보다 크고 소수점 2자리 이하여야 합니다.
잘못된 입력은 400, 없는 자원은 404, 중복 계약/전송 및 성공 메시지 재처리는 409입니다.
오류 응답은 Problem Details 형식입니다. 전송의 업무 실패는 HTTP 오류가 아니라 응답 `status=FAILED`로 확인합니다.

## 아키텍처와 데이터 정합성

```text
REST Controller → Service (@Transactional) → MyBatis → PostgreSQL
                       ↓
                  SapSimulator

contract 1 ── 0..1 interface_message 1 ── N interface_history
                                               N │
                                                 1 batch_history
```

- `contract`: 계약 번호, 고객명, 금액.
- `interface_message`: 계약당 하나의 논리 메시지, 최종 상태, 시나리오, 시도 횟수, 마지막 오류.
- `interface_history`: 각 시도의 결과/오류 코드 및 처리 시각. 재처리 시 기존 이력을 보존합니다.
- `batch_history`: INITIAL_SEND/MANUAL_RETRY 실행 단위의 성공/실패 및 시작/종료 시각.
  MVP에서는 한 실행이 메시지 한 건을 처리하며, 스케줄러나 다건 배치는 아직 없습니다.

최초 전송은 DB의 고유 제약으로 중복을 방지합니다. 재처리는 행 잠금(`SELECT FOR UPDATE`)으로 직렬화하고
잠금 획득 후 FAILED 상태를 검사합니다. 실패가 지속되는 경우 각각의 재처리 요청은 별도 시도가 됩니다.
메시지 상태, 시도 이력, 실행 이력은 한 트랜잭션에서 저장합니다. DB 오류 발생 시 모두 롤백됩니다.
PENDING은 최초 전송 트랜잭션 안의 중간 상태이며, 응답은 SUCCESS 또는 FAILED입니다.

DB 시각은 타임존이 있는 UTC 시각으로 저장하고, 일별 조회는 Asia/Seoul의 자정 이상~다음 자정 미만입니다.
통계는 **계약 수나 최종 상태가 아닌 시도 횟수**입니다. 당일 실패 후 재처리 성공은 total=2, success=1, failed=1입니다.
Flyway가 최초 기동 시 버전 관리된 스키마를 적용합니다.

현재 범위는 로컬 개발용 백엔드 MVP입니다. 인증/권한, UI, 실제 SAP/CRM 연동, 자동 재시도는 포함하지 않습니다.
Compose는 호스트의 localhost에만 포트를 공개합니다. 실제 외부 전송으로 확장할 때는 DB 트랜잭션만으로
외부 시스템의 중복 처리를 막을 수 없으므로 outbox, 수신 측 멱등 키, 타임아웃/장애 복구 정책을 추가해야 합니다.

## 테스트와 CI

```sh
./gradlew clean build
```

기본 테스트는 H2 PostgreSQL 호환 모드에서 실제 Spring API, MyBatis SQL, Flyway 마이그레이션을 실행합니다.
H2 검증을 실제 PostgreSQL 검증과 동일하게 취급하지 않습니다. PostgreSQL에서 같은 테스트를 실행하려면
**별도의 테스트 DB**를 만들고 다음 환경 변수를 설정하세요. 테스트는 각 실행 전에 네 테이블의 데이터를 삭제합니다.

```sh
export TEST_DB_URL=jdbc:postgresql://localhost:5432/icc_test
export TEST_DB_USERNAME=icc
export TEST_DB_PASSWORD=icc_test
./gradlew clean test
```

GitHub Actions는 push/PR마다 Java 21 + PostgreSQL 16 서비스로 `clean build`를 실행하고,
테스트 리포트를 아티팩트로 보관하며 Compose 구문과 Docker 이미지 빌드를 검증합니다.
검증 범위: 정상 전송, 일시/영구 실패, 타임아웃, 재처리, 중복 방지, 동시 재처리,
입력 검증, 목록 페이지, 이력, 한국 시간 일별 집계 경계.
빌드 결과는 `build/libs/app.jar`, 테스트 보고서는 `build/reports/tests/test/index.html`입니다.
