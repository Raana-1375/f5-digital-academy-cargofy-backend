package com.cargofy.backend.controller;

import com.cargofy.backend.dto.UpdateProfileResponse;
import com.cargofy.backend.dto.UpdateUserRequest;
import com.cargofy.backend.dto.UserResponse;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.UserRepository;
import com.cargofy.backend.security.CustomUserDetailsService;
import com.cargofy.backend.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public UserController(UserRepository userRepository,
                          JwtService jwtService,
                          CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProfile(@PathVariable Long id,
                                           @Valid @RequestBody UpdateUserRequest request) {
        String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByEmail(currentEmail).orElse(null);

        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!currentUser.getId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You can only update your own profile");
        }

        String newEmail = request.getEmail();
        boolean emailChanged = !newEmail.equalsIgnoreCase(currentUser.getEmail());
        if (emailChanged && userRepository.existsByEmail(newEmail)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Email is already in use");
        }

        currentUser.setName(request.getName());
        currentUser.setEmail(newEmail);
        currentUser.setCompanyName(request.getCompanyName());
        User savedUser = userRepository.save(currentUser);

        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String newToken = jwtService.generateToken(userDetails);

        return ResponseEntity.ok(new UpdateProfileResponse(toUserResponse(savedUser), newToken));
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCompanyName()
        );
    }
}
