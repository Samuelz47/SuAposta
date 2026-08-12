package com.suaposta.auth.presentation.controller;

import com.suaposta.auth.application.dto.RegisterUserCommand;
import com.suaposta.auth.application.service.RegisterUserService;
import com.suaposta.auth.application.condition.AuthPersistenceConfiguredCondition;
import com.suaposta.auth.presentation.dto.RegisterUserRequest;
import com.suaposta.auth.presentation.dto.RegisterUserResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Conditional(AuthPersistenceConfiguredCondition.class)
public class AuthController {

    private final RegisterUserService registerUserService;

    public AuthController(RegisterUserService registerUserService) {
        this.registerUserService = registerUserService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        var registeredUser = registerUserService.register(
                new RegisterUserCommand(request.name(), request.email(), request.password()));
        var response = new RegisterUserResponse(
                registeredUser.id(),
                registeredUser.name(),
                registeredUser.email(),
                registeredUser.createdAt());
        return ResponseEntity.status(201).body(response);
    }
}
