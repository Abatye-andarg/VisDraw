package com.visualnotes.controller;

import com.visualnotes.dto.AccountResponse;
import com.visualnotes.dto.CsrfResponse;
import com.visualnotes.dto.RegisterRequest;
import com.visualnotes.security.AccountPrincipal;
import com.visualnotes.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse register(@Valid @RequestBody RegisterRequest request) {
        return accounts.register(request);
    }

    @GetMapping("/me")
    public AccountResponse me(@AuthenticationPrincipal AccountPrincipal account) {
        return new AccountResponse(account.getId(), account.getUsername());
    }
}
