package com.suaposta.auth.presentation.controller;

import com.suaposta.auth.application.condition.AuthPersistenceConfiguredCondition;
import com.suaposta.auth.application.service.IdentifyCurrentUserService;
import com.suaposta.auth.presentation.dto.AuthenticatedUserResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Conditional(AuthPersistenceConfiguredCondition.class)
public class AuthCurrentUserController {

    private final IdentifyCurrentUserService identifyCurrentUserService;

    public AuthCurrentUserController(IdentifyCurrentUserService identifyCurrentUserService) {
        this.identifyCurrentUserService = identifyCurrentUserService;
    }

    @GetMapping("/me")
    public ResponseEntity<AuthenticatedUserResponse> currentUser(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        var user = identifyCurrentUserService.identify(userId);
        return ResponseEntity.ok(new AuthenticatedUserResponse(user.id(), user.name(), user.email()));
    }
}
