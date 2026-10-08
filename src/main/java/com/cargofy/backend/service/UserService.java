package com.cargofy.backend.service;

import com.cargofy.backend.dto.UpdateProfileResponse;
import com.cargofy.backend.dto.UpdateUserRequest;
import com.cargofy.backend.dto.UserResponse;
import com.cargofy.backend.exception.ConflictException;
import com.cargofy.backend.exception.ForbiddenException;
import com.cargofy.backend.exception.ResourceNotFoundException;
import com.cargofy.backend.model.User;
import com.cargofy.backend.repository.UserRepository;
import com.cargofy.backend.security.CustomUserDetailsService;
import com.cargofy.backend.security.JwtService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public UserService(UserRepository userRepository,
                       JwtService jwtService,
                       CustomUserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    public UpdateProfileResponse updateProfile(Long id, String currentEmail, UpdateUserRequest request) {
        User currentUser = userRepository.findByEmail(currentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!currentUser.getId().equals(id)) {
            throw new ForbiddenException("You can only update your own profile");
        }

        String newEmail = request.getEmail();
        boolean emailChanged = !newEmail.equalsIgnoreCase(currentUser.getEmail());
        if (emailChanged && userRepository.existsByEmail(newEmail)) {
            throw new ConflictException("Email is already in use");
        }

        currentUser.setName(request.getName());
        currentUser.setEmail(newEmail);
        currentUser.setCompanyName(request.getCompanyName());
        User savedUser = userRepository.save(currentUser);

        UserDetails userDetails = userDetailsService.loadUserByUsername(savedUser.getEmail());
        String newToken = jwtService.generateToken(userDetails);

        return new UpdateProfileResponse(toUserResponse(savedUser), newToken);
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
