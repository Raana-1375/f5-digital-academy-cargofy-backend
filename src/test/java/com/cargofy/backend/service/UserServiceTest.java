package com.cargofy.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cargofy.backend.dto.ChangePasswordRequest;
import com.cargofy.backend.dto.UpdateProfileResponse;
import com.cargofy.backend.dto.UpdateUserRequest;
import com.cargofy.backend.dto.UserResponse;
import com.cargofy.backend.exception.BadRequestException;
import com.cargofy.backend.exception.ConflictException;
import com.cargofy.backend.exception.ForbiddenException;
import com.cargofy.backend.exception.ResourceNotFoundException;
import com.cargofy.backend.model.Role;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.UserRepository;
import com.cargofy.backend.security.CustomUserDetailsService;
import com.cargofy.backend.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String EMAIL = "ana@cargofy.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private UserService userService;

    private User existingUser() {
        return new User(1L, "Ana Lopez", EMAIL, "hashed-old", Role.CLIENT, "Old Co");
    }

    // getCurrentUser

    @Test
    void getCurrentUser_returnsTheUserData() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));

        UserResponse response = userService.getCurrentUser(EMAIL);

        assertEquals(1L, response.getId());
        assertEquals("Ana Lopez", response.getName());
        assertEquals(EMAIL, response.getEmail());
        assertEquals("CLIENT", response.getRole());
        assertEquals("Old Co", response.getCompanyName());
    }

    @Test
    void getCurrentUser_throwsWhenTheUserDoesNotExist() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getCurrentUser(EMAIL));
    }

    // updateProfile

    @Test
    void updateProfile_savesTheNewDataAndReturnsANewToken() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
        when(userRepository.existsByEmail("ana.new@cargofy.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userDetailsService.loadUserByUsername("ana.new@cargofy.com")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("new-token");

        UpdateProfileResponse response = userService.updateProfile(1L, EMAIL,
                new UpdateUserRequest("Ana Garcia", "ana.new@cargofy.com", "New Co"));

        assertEquals("Ana Garcia", response.getUser().getName());
        assertEquals("ana.new@cargofy.com", response.getUser().getEmail());
        assertEquals("New Co", response.getUser().getCompanyName());
        assertEquals("new-token", response.getToken());
    }

    @Test
    void updateProfile_doesNotCheckDuplicatesWhenTheEmailIsUnchanged() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userDetailsService.loadUserByUsername("ANA@cargofy.com")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("token");

        userService.updateProfile(1L, EMAIL, new UpdateUserRequest("Ana Lopez", "ANA@cargofy.com", "Old Co"));

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void updateProfile_rejectsUpdatingAnotherUsersProfile() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));

        assertThrows(ForbiddenException.class, () -> userService.updateProfile(2L, EMAIL,
                new UpdateUserRequest("Ana Lopez", EMAIL, "Old Co")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateProfile_rejectsAnEmailThatIsAlreadyInUse() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
        when(userRepository.existsByEmail("taken@cargofy.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.updateProfile(1L, EMAIL,
                new UpdateUserRequest("Ana Lopez", "taken@cargofy.com", "Old Co")));

        verify(userRepository, never()).save(any());
    }

    // changePassword

    @Test
    void changePassword_savesTheEncodedNewPassword() {
        User user = existingUser();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass123", "hashed-old")).thenReturn(true);
        when(passwordEncoder.matches("NewPass123", "hashed-old")).thenReturn(false);
        when(passwordEncoder.encode("NewPass123")).thenReturn("hashed-new");

        userService.changePassword(1L, EMAIL, new ChangePasswordRequest("OldPass123", "NewPass123"));

        assertEquals("hashed-new", user.getPassword());
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_rejectsAWrongCurrentPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches("WrongPass1", "hashed-old")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> userService.changePassword(1L, EMAIL,
                new ChangePasswordRequest("WrongPass1", "NewPass123")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_rejectsTheSamePassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));
        when(passwordEncoder.matches("SamePass123", "hashed-old")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> userService.changePassword(1L, EMAIL,
                new ChangePasswordRequest("SamePass123", "SamePass123")));

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_rejectsChangingAnotherUsersPassword() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));

        assertThrows(ForbiddenException.class, () -> userService.changePassword(2L, EMAIL,
                new ChangePasswordRequest("OldPass123", "NewPass123")));

        verify(userRepository, never()).save(any());
    }
}