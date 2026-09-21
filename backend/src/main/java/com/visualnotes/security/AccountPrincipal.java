package com.visualnotes.security;

import java.io.Serial;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

public class AccountPrincipal extends User {
    @Serial
    private static final long serialVersionUID = 1L;
    private final String id;

    public AccountPrincipal(String id, String email, String passwordHash) {
        super(email, passwordHash, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
