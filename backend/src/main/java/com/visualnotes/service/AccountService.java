package com.visualnotes.service;

import java.util.Locale;
import com.visualnotes.dto.AccountResponse;
import com.visualnotes.dto.RegisterRequest;
import com.visualnotes.exception.EmailAlreadyRegisteredException;
import com.visualnotes.model.Account;
import com.visualnotes.repository.AccountRepository;
import com.visualnotes.security.AccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService implements UserDetailsService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;

    @Transactional
    public AccountResponse register(RegisterRequest request) {
        var account = new Account(request.email(), passwords.encode(request.password()));
        try {
            // Flush here so concurrent duplicate registrations fail within this operation.
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && violation.getConstraintName() != null
                        && violation.getConstraintName().endsWith("uk_accounts_email")) {
                    throw new EmailAlreadyRegisteredException();
                }
            }
            throw exception;
        }
        return new AccountResponse(account.getId(), account.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPrincipal loadUserByUsername(String email) {
        return accounts.findByEmail(email.strip().toLowerCase(Locale.ROOT))
                .map(account -> new AccountPrincipal(account.getId(), account.getEmail(), account.getPasswordHash()))
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password."));
    }
}
