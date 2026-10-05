package com.paytm.seatreservation.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;

        public SecurityConfig(
                        JwtAuthenticationFilter jwtAuthenticationFilter) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(
                        HttpSecurity http) throws Exception {

                http
                                .csrf(csrf -> csrf.disable())

                                .sessionManagement(session -> session.sessionCreationPolicy(
                                                SessionCreationPolicy.STATELESS))

                                .authorizeHttpRequests(auth -> auth

                                                // Public endpoints
                                                .requestMatchers(
                                                                "/auth/**",
                                                                "/actuator/health/**",
                                                                "/actuator/info",
                                                                "/actuator/prometheus")
                                                .permitAll()

                                                // GET /shows/{id}
                                                .requestMatchers("/shows/*").permitAll()

                                                // Admin only
                                                .requestMatchers("/shows")
                                                .hasRole("ADMIN")

                                                // User reservation
                                                .requestMatchers("/shows/*/reserve")
                                                .hasRole("USER")

                                                // User cancellation
                                                .requestMatchers("/reservations/*/cancel")
                                                .hasRole("USER")

                                                // Everything else requires authentication
                                                .anyRequest()
                                                .authenticated())

                                .addFilterBefore(
                                                jwtAuthenticationFilter,
                                                UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}