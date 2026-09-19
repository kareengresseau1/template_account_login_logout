package com.template.account_login_logout.repository; 
import com.template.account_login_logout.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {  

    Optional<Account> findByEmail(String email); 

    Optional<Account> findByUsername(String username);

}
