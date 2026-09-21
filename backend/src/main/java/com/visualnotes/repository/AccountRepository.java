package com.visualnotes.repository;

import java.util.Optional;
import com.visualnotes.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
    Optional<Account> findByEmail(String email);
}
