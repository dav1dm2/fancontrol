package com.example.fancontrol.service;

import com.example.fancontrol.model.OperatorUser;
import com.example.fancontrol.model.Role;
import com.example.fancontrol.repository.OperatorUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private final OperatorUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String initialName;
    private final String initialPassword;

    public AdminBootstrap(
            OperatorUserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.bootstrap-admin.name:}") String initialName,
            @Value("${app.bootstrap-admin.password:}") String initialPassword
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.initialName = initialName;
        this.initialPassword = initialPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean hasName = initialName != null && !initialName.isBlank();
        boolean hasPassword = initialPassword != null && !initialPassword.isBlank();
        if (!hasName && !hasPassword) {
            return;
        }
        if (!hasName || !hasPassword) {
            throw new IllegalStateException("Set both ADMIN_INITIAL_NAME and ADMIN_INITIAL_PASSWORD to bootstrap an administrator.");
        }
        if (initialName.trim().length() > 100 || initialPassword.length() < 8
                || initialPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("The initial administrator name or password does not meet the configured limits.");
        }
        if (userRepository.existsByNameIgnoreCase(initialName.trim())) {
            return;
        }

        OperatorUser admin = new OperatorUser();
        admin.setName(initialName.trim());
        admin.setPasswordHash(passwordEncoder.encode(initialPassword));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
    }
}
