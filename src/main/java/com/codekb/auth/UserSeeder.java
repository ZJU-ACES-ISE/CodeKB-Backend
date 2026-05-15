package com.codekb.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class UserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUser("admin", "admin123", "Administrator", User.Role.ADMIN);
        seedUser("demo", "demo123", "Demo User", User.Role.USER);
    }

    private void seedUser(String username, String rawPassword, String displayName, User.Role role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        User u = new User();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setDisplayName(displayName);
        u.setRole(role);
        userRepository.save(u);
        log.info("Seeded user: {} ({})", username, role);
    }
}
