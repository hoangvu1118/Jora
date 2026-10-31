package com.sefujo.auth;

import com.sefujo.auth.dto.LoginRequest;
import com.sefujo.auth.dto.RegisterRequest;
import com.sefujo.user.UserResponse;
import com.sefujo.user.UserService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth/")
@AllArgsConstructor
public class AuthController {

    private AuthService authService;
    private UserService userService;


    // TODO: give Redis lock 5 times per 15 minute
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest){
        AuthResponse response = authService.authenticate(loginRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest registerRequest){
        UserResponse response = authService.register(registerRequest);
        return ResponseEntity.ok(response);
    }}
