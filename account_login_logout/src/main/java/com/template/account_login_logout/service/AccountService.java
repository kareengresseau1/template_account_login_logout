package com.template.account_login_logout.service;
import org.springframework.stereotype.Service;
import com.template.account_login_logout.model.Account;
import com.template.account_login_logout.repository.AccountRepository;
import java.util.Optional;


@Service
public class AccountService { 

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Optional<Account> findByEmail(String email) {
        return accountRepository.findByEmail(email);
    }

    public Optional<Account> findByUsername(String username) {
        return accountRepository.findByUsername(username);
    } 

    

}
