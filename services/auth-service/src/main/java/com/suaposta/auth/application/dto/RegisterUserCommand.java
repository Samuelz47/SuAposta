package com.suaposta.auth.application.dto;

public record RegisterUserCommand(String name, String email, String password) {
}
