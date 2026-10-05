package com.paytm.seatreservation.controller;

import com.paytm.seatreservation.dto.LoginRequest;
import com.paytm.seatreservation.dto.LoginResponse;
import com.paytm.seatreservation.entity.User;
import com.paytm.seatreservation.repository.UserRepository;
import com.paytm.seatreservation.security.JwtService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;

        public AuthController(
                        UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {

                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
        }

        @PostMapping("/login")
        public ResponseEntity<LoginResponse> login(
                        @Valid @RequestBody LoginRequest request) {

                User user = userRepository
                                .findByUsername(request.username())
                                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

                boolean passwordMatches = passwordEncoder.matches(
                                request.password(),
                                user.getPasswordHash());

                if (!passwordMatches) {
                        throw new RuntimeException(
                                        "Invalid username or password");
                }

                String token = jwtService.generateToken(
                                user.getId(),
                                user.getUsername(),
                                user.getRole().name());

                return ResponseEntity
                                .status(HttpStatus.OK)
                                .body(new LoginResponse(token));
        }
}