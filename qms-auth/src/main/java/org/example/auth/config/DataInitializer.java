package org.example.auth.config;

import lombok.RequiredArgsConstructor;
import org.example.auth.entity.AppUser;
import org.example.auth.entity.enums.Role;
import org.example.auth.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() == 0) {
            System.out.println("Initializing Mock Users Data...");

            AppUser admin = AppUser.builder()
                    .branchId(1L)
                    .username("admin")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .fullName("System Admin")
                    .role(Role.ADMIN)
                    .isActive(true)
                    .build();

            AppUser manager = AppUser.builder()
                    .branchId(1L)
                    .username("manager1")
                    .passwordHash(passwordEncoder.encode("manager123"))
                    .fullName("Branch Manager HCM")
                    .role(Role.MANAGER)
                    .isActive(true)
                    .build();

            AppUser teller = AppUser.builder()
                    .branchId(1L)
                    .username("teller1")
                    .passwordHash(passwordEncoder.encode("teller123"))
                    .fullName("Nguyen Van Teller")
                    .role(Role.TELLER)
                    .isActive(true)
                    .build();

            userRepository.save(admin);
            userRepository.save(manager);
            userRepository.save(teller);

            System.out.println("Successfully Initialized 3 Mock Users: [admin, manager1, teller1]");
        }
    }
}

