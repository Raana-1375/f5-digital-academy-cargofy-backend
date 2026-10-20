package com.cargofy.backend.config;

import com.cargofy.backend.model.Role;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DemoDataSeederTest {

    private static final String PASSWORD = "secret-demo-pass";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsOneAccountPerRoleWhenNoneExist() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        DemoDataSeeder seeder = new DemoDataSeeder(userRepository, passwordEncoder, PASSWORD);

        seeder.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(captor.capture());
        List<User> saved = captor.getAllValues();
        assertTrue(saved.stream().anyMatch(u -> u.getRole() == Role.ADMIN));
        assertTrue(saved.stream().anyMatch(u -> u.getRole() == Role.OPERATOR));
        assertTrue(saved.stream().anyMatch(u -> u.getRole() == Role.CLIENT));
        assertTrue(saved.stream().allMatch(u -> "hashed".equals(u.getPassword())));
    }

    @Test
    void doesNotRecreateExistingAccounts() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(userRepository.existsByEmail("admin@cargofy.com")).thenReturn(true);
        DemoDataSeeder seeder = new DemoDataSeeder(userRepository, passwordEncoder, PASSWORD);

        seeder.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(captor.capture());
        assertTrue(captor.getAllValues().stream().noneMatch(u -> u.getRole() == Role.ADMIN));
    }

    @Test
    void createsNothingWhenPasswordIsBlank() {
        DemoDataSeeder seeder = new DemoDataSeeder(userRepository, passwordEncoder, " ");

        seeder.run(null);

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void storesTheEncodedPasswordNeverThePlainOne() {
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        DemoDataSeeder seeder = new DemoDataSeeder(userRepository, passwordEncoder, PASSWORD);

        seeder.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(captor.capture());
        captor.getAllValues().forEach(u -> assertEquals("hashed", u.getPassword()));
    }
}
