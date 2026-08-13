package com.Chung_Woon.Chung_Woon.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 해커톤용 기본 설정: 일단 전부 열어두고, 로그인 붙일 때 authorizeHttpRequests 만 조이면 된다.
 * spring-boot-starter-security 를 넣은 순간 모든 요청에 로그인 폼이 뜨는데, 그걸 막는 역할도 겸한다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.csrf(csrf -> csrf.disable())
				.cors(Customizer.withDefaults())
				.httpBasic(basic -> basic.disable())
				.formLogin(form -> form.disable())
				.logout(logout -> logout.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				// H2 콘솔이 iframe 을 쓰기 때문에 필요
				.headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
				.authorizeHttpRequests(auth -> auth
						// TODO 로그인 붙이면 여기를 조인다.
						//  예) .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
						//      .anyRequest().authenticated()
						.anyRequest().permitAll());
		return http.build();
	}

	/** 회원가입/로그인 붙일 때 비밀번호 해싱용. 기본은 bcrypt. */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}
