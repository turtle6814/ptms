package com.example.backend.config;

import com.example.backend.enums.Role;
import com.example.backend.user.entity.User;
import com.example.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

// ponytail: hardcoded seed passwords, fine for dev/demo since they're not real
// user data; move to per-role env vars (or gate to non-prod profiles) before
// deploying anywhere with real accounts.
@Component
@RequiredArgsConstructor
@Slf4j
public class RoleAccountSeeder implements CommandLineRunner {

    private record SeedAccount(String username, String phoneNumber, String password, Role role) {
    }

    private static final List<SeedAccount> SEED_ACCOUNTS = List.of(
            new SeedAccount("admin", "0900000001", "Admin@123", Role.ADMIN),
            new SeedAccount("organizer", "0900000002", "Organizer@123", Role.ORGANIZER),
            new SeedAccount("referee", "0900000003", "Referee@123", Role.REFEREE),
            new SeedAccount("user", "0900000004", "User@123", Role.USER));

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        for (SeedAccount seed : SEED_ACCOUNTS) {
            if (userRepository.existsByUsername(seed.username())) {
                continue;
            }
            User user = User.builder()
                    .username(seed.username())
                    .phoneNumber(seed.phoneNumber())
                    .password(passwordEncoder.encode(seed.password()))
                    .role(seed.role())
                    .build();
            userRepository.save(user);
            log.info("Seeded default {} account: username={}", seed.role(), seed.username());
        }
    }
}
