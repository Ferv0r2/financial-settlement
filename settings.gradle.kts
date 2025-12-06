rootProject.name = "financial-settlement"

// Backend modules
include("common")           // 공통 도메인, 유틸리티
include("api")              // REST API 서버
include("batch")            // 배치 처리 (청산/정산)

// Frontend module
include("frontend")
