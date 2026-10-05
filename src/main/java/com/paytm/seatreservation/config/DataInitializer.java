package com.paytm.seatreservation.config;

import com.paytm.seatreservation.entity.User;
import com.paytm.seatreservation.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    public org.springframework.boot.CommandLineRunner initUsers(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {

            // =====================================================
            // ADMIN USER
            // =====================================================

            if (userRepository
                    .findByUsername("admin")
                    .isEmpty()) {

                User admin = new User();

                admin.setUsername("admin");

                admin.setPasswordHash(
                        passwordEncoder.encode(
                                "Admin@123"));

                admin.setRole(
                        User.Role.ADMIN);

                userRepository.save(admin);
            }

            // =====================================================
            // NORMAL USER
            // =====================================================

            if (userRepository
                    .findByUsername("testuser")
                    .isEmpty()) {

                User user = new User();

                user.setUsername("testuser");

                user.setPasswordHash(
                        passwordEncoder.encode(
                                "Test@123"));

                user.setRole(
                        User.Role.USER);

                userRepository.save(user);
            }

            // =====================================================
            // METRICS TEST USER
            // =====================================================

            if (userRepository
                    .findByUsername("metricsuser")
                    .isEmpty()) {

                User metricsUser = new User();

                metricsUser.setUsername(
                        "metricsuser");

                metricsUser.setPasswordHash(
                        passwordEncoder.encode(
                                "Metrics@123"));

                metricsUser.setRole(
                        User.Role.USER);

                userRepository.save(metricsUser);
            }
        };
    }
}