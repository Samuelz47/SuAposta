package com.suaposta.auth.presentation.controller;

import com.suaposta.auth.application.condition.AuthLoginConfiguredCondition;
import com.suaposta.auth.application.dto.LoginUserCommand;
import com.suaposta.auth.application.service.AuthenticateUserService;
import com.suaposta.auth.presentation.dto.AuthenticatedUserResponse;
import com.suaposta.auth.presentation.dto.LoginRequest;
import com.suaposta.auth.presentation.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Conditional(AuthLoginConfiguredCondition.class)
public class AuthLoginController {

    private final AuthenticateUserService authenticateUserService;

    public AuthLoginController(AuthenticateUserService authenticateUserService) {
        this.authenticateUserService = authenticateUserService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        var result = authenticateUserService.authenticate(
                new LoginUserCommand(request.email(), request.password()));
        var user = result.user();
        var response = new LoginResponse(
                result.accessToken(),
                "Bearer",
                result.expiresIn(),
                new AuthenticatedUserResponse(user.id(), user.name(), user.email()));
        return ResponseEntity.ok(response);
    }
}
