# PayFlow - 금융 정산 시스템

금융권 결제/정산 시스템 풀스택 캡스톤 프로젝트

## Tech Stack

### Backend
- Java 17 LTS
- Spring Boot 3.2.5
- Spring Security
- Spring Data JPA
- PostgreSQL 15
- Redis 7
- Flyway (DB Migration)
- Gradle (Kotlin DSL)

### Frontend
- React + TypeScript (TBD)

## Project Structure

```
financial-settlement/
├── api/              # Spring Boot API 서버
├── batch/            # Spring Batch 정산 배치
├── common/           # 공통 도메인, 예외처리
├── frontend/         # React 클라이언트
├── docker-compose.yml
├── build.gradle.kts
└── settings.gradle.kts
```

## Quick Start

### Prerequisites
- JDK 17+
- Docker Desktop
- Gradle 8.x (또는 Gradle Wrapper 사용)

---

## Docker 명령어

### 컨테이너 시작/중지
```bash
# 컨테이너 시작 (백그라운드)
docker-compose up -d

# 컨테이너 중지
docker-compose down

# 컨테이너 + 볼륨 삭제 (DB 초기화)
docker-compose down -v

# 컨테이너 재시작 (DB 초기화 포함)
docker-compose down -v && docker-compose up -d
```

### 컨테이너 상태 확인
```bash
# 실행 중인 컨테이너 확인
docker ps

# 모든 컨테이너 확인 (중지된 것 포함)
docker ps -a

# 컨테이너 로그 확인
docker logs payflow-postgres
docker logs payflow-redis
```

### DB 접속
```bash
# PostgreSQL 접속
docker exec -it payflow-postgres psql -U payflow -d payflow

# 테이블 목록 확인
\dt

# 쿼리 실행
SELECT * FROM merchants;

# 종료
\q
```

### Redis 접속
```bash
# Redis CLI 접속
docker exec -it payflow-redis redis-cli

# 키 확인
KEYS *

# 종료
exit
```

---

## Gradle 명령어

### 빌드
```bash
# 전체 빌드
./gradlew build

# 테스트 없이 빌드
./gradlew build -x test

# 클린 빌드
./gradlew clean build

# 특정 모듈만 빌드
./gradlew :common:build
./gradlew :api:build
./gradlew :batch:build
```

### 테스트
```bash
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :common:test

# 특정 테스트 클래스만 실행
./gradlew :common:test --tests "PaymentStatusTest"

# 테스트 리포트 열기
open common/build/reports/tests/test/index.html
```

### 애플리케이션 실행
```bash
# API 서버 실행
./gradlew :api:bootRun

# Batch 서버 실행
./gradlew :batch:bootRun
```

### 의존성 확인
```bash
# 의존성 트리 확인
./gradlew :api:dependencies

# 의존성 업데이트 확인
./gradlew dependencyUpdates
```

---

## 개발 환경 설정

### 1. Docker 환경 시작
```bash
docker-compose up -d
```

### 2. DB 준비 확인 (약 10초 대기)
```bash
docker ps  # STATUS가 (healthy)인지 확인
```

### 3. API 서버 실행
```bash
./gradlew :api:bootRun
```

### 4. 접속 확인
- API 서버: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- Health Check: http://localhost:8080/actuator/health

---

## 문제 해결

### DB 연결 실패 (password authentication failed)
```bash
# Docker 볼륨 삭제 후 재시작
docker-compose down -v
docker-compose up -d
sleep 10
./gradlew :api:bootRun
```

### 포트 충돌 (8080 already in use)
```bash
# 8080 포트 사용 중인 프로세스 종료
lsof -ti:8080 | xargs kill -9
```

### Bean 충돌 오류
```bash
# 클린 빌드 후 재시작
./gradlew clean build -x test
./gradlew :api:bootRun
```

---

## Profiles

| Profile | 용도 | DB |
|---------|------|-----|
| local | 로컬 개발 | PostgreSQL (Docker) |
| test | 테스트 | H2 In-Memory |
| docker | Docker 환경 | PostgreSQL (Docker Network) |

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /actuator/health | 서버 상태 확인 |
| GET | /swagger-ui/index.html | API 문서 |

---

## Sprint Progress

- [x] Sprint 1.1: 인프라 및 프로젝트 구조
- [x] Sprint 1.2: 도메인 모델 및 결제 기본 기능
- [ ] Sprint 1.3: 결제 API 기본 구현
