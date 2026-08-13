package com.Chung_Woon.Chung_Woon.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 스웨거 문서의 머리말. 프론트가 개별 API 를 보기 전에 알아야 할 공통 규칙을 여기 모았다.
 */
@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI autoYardOpenApi() {
		return new OpenAPI().info(new Info()
				.title("AutoYard Copilot API")
				.version("v1")
				.description("""
						현장 자연어 지시를 검증된 야드 배치로 바꾸는 시스템의 공개 API.
						**프론트는 이 서버만 호출한다.** 파이썬 AI 서비스는 이 서버가 내부적으로 부른다.

						---

						### 1. 응답은 전부 봉투에 싸여 온다

						```json
						{ "success": true,  "data": { ... } }
						{ "success": false, "error": { "code": "AI001", "message": "..." } }
						```

						`success` 를 먼저 보고 `data` 를 꺼내면 된다.

						### 2. 필드명은 snake_case

						`bl_number`, `vehicle_count`, `slot_id` … 자바는 카멜케이스지만 JSON 경계에서는
						snake_case 로 바꿔서 나간다.

						### 3. ⚠️ 값이 없으면 `null` 이 아니라 **키가 통째로 빠진다**

						서버 전역이 `non_null` 설정이라 값이 없는 필드는 응답에 아예 없다.
						`data.summary.avg_move_distance_meters === null` 같은 검사는 통하지 않는다.
						`?.` 나 `in` 으로 다뤄야 한다.

						### 4. enum 은 문자열 그대로

						`BLOCK_CLOSURE`, `PENDING_REVIEW`, `TRUCK` … 숫자 코드는 쓰지 않는다.
						화면에 한글이 필요한 곳은 서버가 `_label` 필드를 같이 준다
						(예: `status` = `OPERATING`, `status_label` = `운영 중`).

						### 5. 시각은 ISO-8601 로컬시각

						`2026-08-13T14:00:00`. 타임존은 `Asia/Seoul` 고정이고 오프셋은 붙지 않는다.

						### 6. 야드 격자

						**22행 × 46열** (정사각형 아님). 4 + 5 + 4 + 5 + 4 = 22행,
						4 + 17 + 4 + 17 + 4 = 46열. 블록 4개가 각 **5행 × 17열 = 85칸**,
						전체 주차칸 **340**, 도로칸 **672**.

						슬롯 ID 가 좌표를 품는다 — `B01-R04-C07` = 블록 B01, 행 4, 열 7.
						파싱해서 그대로 격자에 찍으면 된다. 알고리즘 좌표는 **x = col, y = row**.

						### 7. 오류 코드

						| code | HTTP | 뜻 |
						|---|---|---|
						| `C001` | 400 | 입력값이 올바르지 않음 |
						| `C003` | 404 | 없는 리소스 |
						| `C999` | 500 | 서버 오류 |
						| `AI001` | 503 | AI 서비스를 부르지 못함 (파이썬 다운·미구현·인증거부) |
						| `AI002` | 502 | AI 응답이 계약과 다름 |

						**AI 가 죽어도 조회 API 는 살아 있다.** 파이썬을 부르는 요청만 실패한다.
						""")
		);
	}
}
