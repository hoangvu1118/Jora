package com.sefujo.auth;

import com.sefujo.auth.dto.LoginRequest;
import com.sefujo.auth.dto.RegisterRequest;
import com.sefujo.common.exception.EmailAlreadyExistsException;
import com.sefujo.common.exception.InvalidCredentialException;
import com.sefujo.common.exception.RateLimitException;
import com.sefujo.common.exception.UserNotAuthenticated;
import com.sefujo.common.security.CustomUserDetail;
import com.sefujo.common.security.CustomUserDetailService;
import com.sefujo.common.security.JwtTokenProvider;
import com.sefujo.user.User;
import com.sefujo.user.UserRepository;
import com.sefujo.user.UserResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;


@Service
@AllArgsConstructor
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final AuthLimitLock authLimitLock;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthResponse authenticate(LoginRequest loginRequest) {
        Authentication authentication;
        if(!authLimitLock.allowAuthRequest(loginRequest.getEmail())){
            throw new RateLimitException("");
        }
        try {
             authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword())
            );
        } catch (AuthenticationException e) {
            throw new InvalidCredentialException("Invalid email or password");
        }

        CustomUserDetail userDetails = (CustomUserDetail) authentication.getPrincipal();
        if (userDetails == null) {
            throw new UserNotAuthenticated("User not authenticated");
        }
        String token = jwtTokenProvider.generateToken(userDetails);

        return new AuthResponse(
                userDetails.getUsername(),
                token,
                userDetails.getId()
        );
    }

    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        // 1. check if email exists
        if(userRepository.existsByEmail((request.getEmail()))){
            throw new EmailAlreadyExistsException("email","This Email is already registered");
        }
        // 2. Create new database entity
        User newUser = new User();
        newUser.setEmail(request.getEmail());

        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setRole("USER");

        // 3. save to database
        User saveUser = userRepository.save(newUser);


        // 4. Convert to safe Response DTO
        UserResponse userResponse = new UserResponse();
        userResponse.setRole(saveUser.getRole());
        userResponse.setEmail(saveUser.getEmail());
        // 5. return to controller
        return userResponse;
    }
}
