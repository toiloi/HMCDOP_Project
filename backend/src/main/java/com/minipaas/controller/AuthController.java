package com.minipaas.controller;

import com.minipaas.dto.AuthResponse;
import com.minipaas.dto.LoginRequest;
import com.minipaas.dto.RegisterRequest;
import com.minipaas.model.User;
import com.minipaas.security.JwtUtil;
import com.minipaas.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        User user = userService.register(req.getEmail(), req.getPassword());
        String token = jwtUtil.generateToken(user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getEmail(), "Đăng ký thành công!"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = userService.findByEmail(req.getEmail());
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Email hoặc mật khẩu không đúng");
        }
        String token = jwtUtil.generateToken(user.getEmail());
        return ResponseEntity.ok(new AuthResponse(token, user.getEmail(), "Đăng nhập thành công!"));
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(java.util.Map.of("email", auth.getName()));
    }
}
