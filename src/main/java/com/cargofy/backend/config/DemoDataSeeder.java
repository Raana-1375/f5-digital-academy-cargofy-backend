package com.cargofy.backend.config;

import com.cargofy.backend.model.Role;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one demo account per role on startup (only if they do not exist yet).
 * The shared password comes from the DEMO_PASSWORD environment variable (or the
 * git-ignored .env file). If it is not set, no account is created.
 * Disabled in the "test" profile.
 */
@Component
@Profile("!test")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoDataSeeder(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          @Value("${cargofy.demo-password:}") String demoPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoPassword == null || demoPassword.isBlank()) {
            log.warn("DEMO_PASSWORD is not set: demo accounts were not created");
            return;
        }
        createIfMissing("Admin User", "admin@cargofy.com", Role.ADMIN, "Cargofy");
        createIfMissing("Operator User", "operator@cargofy.com", Role.OPERATOR, "Cargofy");
        createIfMissing("Client User", "client@cargofy.com", Role.CLIENT, "Demo Imports S.L.");
    }

    private void createIfMissing(String name, String email, Role role, String companyName) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(demoPassword));
        user.setRole(role);
        user.setCompanyName(companyName);
        userRepository.save(user);
        log.info("Demo account created: {} ({})", email, role);
    }
}