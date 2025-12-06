-- PayFlow 초기 데이터베이스 설정

-- 확장 기능 활성화
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 스키마 생성 (필요시)
-- CREATE SCHEMA IF NOT EXISTS payflow;

-- 기본 설정
ALTER DATABASE payflow SET timezone TO 'Asia/Seoul';

-- 초기 테스트용 사용자 (개발 환경)
-- 실제 테이블은 Flyway에서 관리
