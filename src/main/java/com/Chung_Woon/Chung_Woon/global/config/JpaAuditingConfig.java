package com.Chung_Woon.Chung_Woon.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** BaseTimeEntity 의 createdAt/updatedAt 자동 기록을 켠다. */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
