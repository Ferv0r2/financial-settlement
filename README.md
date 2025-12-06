# Financial Settlement System

금융권 정산시스템 풀스택 프로젝트

## Tech Stack

### Backend
- Java 17 LTS
- Spring Boot 3.2.5
- Spring Security
- Spring Data JPA
- PostgreSQL (Production) / H2 (Local)
- Gradle (Kotlin DSL)

### Frontend
- React (TBD)

## Project Structure

```
financial-settlement/
├── backend/          # Spring Boot API 서버
├── frontend/         # React 클라이언트
├── build.gradle.kts  # 루트 빌드 설정
└── settings.gradle.kts
```

## Getting Started

### Prerequisites
- JDK 17+
- Gradle 8.x (또는 Gradle Wrapper 사용)

### Run Backend (Local)

```bash
cd backend
../gradlew bootRun
```

API 서버: http://localhost:8080
Swagger UI: http://localhost:8080/swagger-ui.html
H2 Console: http://localhost:8080/h2-console

### Build

```bash
./gradlew clean build
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | /api/health | 서버 상태 확인 |

## Profiles

- `local`: 로컬 개발 환경 (H2 DB)
- `prod`: 프로덕션 환경 (PostgreSQL)
