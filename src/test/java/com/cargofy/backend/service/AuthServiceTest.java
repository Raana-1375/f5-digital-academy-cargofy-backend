package com.cargofy.backend.service;

import com.cargofy.backend.dto.AuthResponse;
import com.cargofy.backend.dto.LoginRequest;
import com.cargofy.backend.dto.RegisterRequest;
import com.cargofy.backend.exception.BadRequestException;
import com.cargofy.backend.exception.ConflictException;
import com.cargofy.backend.exception.ForbiddenException;
import com.cargofy.backend.exception.ResourceNotFoundException;
import com.cargofy.backend.model.Role;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.UserRepository;
import com.cargofy.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String OPERATOR_CODE = "test-operator-code";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtService,
                authenticationManager, OPERATOR_CODE);
    }

    // Operators get a valid access code and the Cargofy company by default.
    private RegisterRequest registerRequest(Role role) {
        RegisterRequest request = new RegisterRequest();
        request.setName("Test User");
        request.setEmail("test@cargofy.com");
        request.setPassword("Test12345");
        request.setRole(role);
        if (role == Role.OPERATOR) {
            request.setCompanyName("Cargofy");
            request.setOperatorAccessCode(OPERATOR_CODE);
        } else {
            request.setCompanyName("Test Co");
        }
        return request;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@cargofy.com");
        request.setPassword("Test12345");
        return request;
    }

    private User existingUser() {
        User user = new User();
        user.setName("Test User");
        user.setEmail("test@cargofy.com");
        user.setPassword("encoded-password");
        user.setRole(Role.CLIENT);
        return user;
    }

    // ---------- register ----------

    @Test
    @DisplayName("register saves the user with an encoded password and returns a token")
    void register_client_savesEncodedPasswordAndReturnsToken() {
        when(userRepository.existsByEmail("test@cargofy.com")).thenReturn(false);
        when(passwordEncoder.encode("Test12345")).thenReturn("encoded-password");
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(registerRequest(Role.CLIENT));

        assertEquals("jwt-token", response.getToken());
        assertEquals("test@cargofy.com", response.getEmail());
        assertEquals("Test User", response.getName());
        assertEquals("CLIENT", response.getRole());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals("encoded-password", savedUser.getValue().getPassword());
        assertEquals(Role.CLIENT, savedUser.getValue().getRole());
        assertEquals("Test Co", savedUser.getValue().getCompanyName());
    }

    @Test
    @DisplayName("register puts the role authority in the token's user details")
    void register_operator_tokenCarriesRoleAuthority() {
        when(userRepository.existsByEmail("test@cargofy.com")).thenReturn(false);
        when(passwordEncoder.encode("Test12345")).thenReturn("encoded-password");
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        authService.register(registerRequest(Role.OPERATOR));

        ArgumentCaptor<UserDetails> details = ArgumentCaptor.forClass(UserDetails.class);
        verify(jwtService).generateToken(details.capture());
        assertEquals("test@cargofy.com", details.getValue().getUsername());
        assertTrue(details.getValue().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_OPERATOR")));
    }

    @Test
    @DisplayName("register rejects the ADMIN role and saves nothing")
    void register_adminRole_throwsBadRequest() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.register(registerRequest(Role.ADMIN)));

        assertEquals("Admin accounts cannot be created through registration", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("register rejects an email that is already in use")
    void register_existingEmail_throwsConflict() {
        when(userRepository.existsByEmail("test@cargofy.com")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> authService.register(registerRequest(Role.CLIENT)));

        verify(userRepository, never()).save(any());
        verifyNoInteractions(passwordEncoder);
    }

    // ---------- register: operator access code ----------

    @Test
    @DisplayName("operator registration with a wrong access code is forbidden and saves nothing")
    void register_operatorWrongCode_throwsForbidden() {
        RegisterRequest request = registerRequest(Role.OPERATOR);
        request.setOperatorAccessCode("wrong-code");

        ForbiddenException ex = assertThrows(ForbiddenException.class,
                () -> authService.register(request));

        assertEquals("Invalid operator access code", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("operator registration without an access code is forbidden")
    void register_operatorMissingCode_throwsForbidden() {
        RegisterRequest request = registerRequest(Role.OPERATOR);
        request.setOperatorAccessCode(null);

        assertThrows(ForbiddenException.class, () -> authService.register(request));

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("operator registration is closed when no access code is configured")
    void register_operatorCodeNotConfigured_throwsForbidden() {
        AuthService closedService = new AuthService(userRepository, passwordEncoder, jwtService,
                authenticationManager, "");
        RegisterRequest request = registerRequest(Role.OPERATOR);
        request.setOperatorAccessCode("");

        assertThrows(ForbiddenException.class, () -> closedService.register(request));

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("operator registration with another company is rejected")
    void register_operatorOtherCompany_throwsBadRequest() {
        RegisterRequest request = registerRequest(Role.OPERATOR);
        request.setCompanyName("Other Co");

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> authService.register(request));

        assertEquals("Operators must belong to Cargofy", ex.getMessage());
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("operator company is stored as Cargofy whatever the letter case")
    void register_operatorCompanyIsNormalized() {
        RegisterRequest request = registerRequest(Role.OPERATOR);
        request.setCompanyName("  CARGOFY ");
        when(userRepository.existsByEmail("test@cargofy.com")).thenReturn(false);
        when(passwordEncoder.encode("Test12345")).thenReturn("encoded-password");
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        authService.register(request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertEquals("Cargofy", savedUser.getValue().getCompanyName());
    }

    @Test
    @DisplayName("clients do not need an access code")
    void register_clientWithoutCode_isAllowed() {
        when(userRepository.existsByEmail("test@cargofy.com")).thenReturn(false);
        when(passwordEncoder.encode("Test12345")).thenReturn("encoded-password");
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = authService.register(registerRequest(Role.CLIENT));

        assertEquals("CLIENT", response.getRole());
    }

    // ---------- login ----------

    @Test
    @DisplayName("login with valid credentials authenticates and returns a token")
    void login_validCredentials_returnsToken() {
        when(userRepository.findByEmail("test@cargofy.com")).thenReturn(Optional.of(existingUser()));
        when(jwtService.generateToken(any(UserDetails.class))).thenReturn("jwt-token");

        AuthResponse response = authService.login(loginRequest());

        assertEquals("jwt-token", response.getToken());
        assertEquals("test@cargofy.com", response.getEmail());
        assertEquals("CLIENT", response.getRole());

        ArgumentCaptor<Authentication> authentication = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authentication.capture());
        assertEquals("test@cargofy.com", authentication.getValue().getPrincipal());
        assertEquals("Test12345", authentication.getValue().getCredentials());
    }

    @Test
    @DisplayName("login with bad credentials propagates the error and issues no token")
    void login_badCredentials_throwsAndIssuesNoToken() {
        doThrow(new BadCredentialsException("Bad credentials"))
                .when(authenticationManager).authenticate(any(Authentication.class));

        assertThrows(BadCredentialsException.class, () -> authService.login(loginRequest()));

        verifyNoInteractions(userRepository);
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("login fails with not found when the authenticated user no longer exists")
    void login_userMissing_throwsNotFound() {
        when(userRepository.findByEmail("test@cargofy.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.login(loginRequest()));

        verifyNoInteractions(jwtService);
    }
}