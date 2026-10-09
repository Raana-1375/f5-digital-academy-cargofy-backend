package com.cargofy.backend.controller;

import com.cargofy.backend.dto.ChangePasswordRequest;
import com.cargofy.backend.dto.UpdateProfileResponse;
import com.cargofy.backend.dto.UpdateUserRequest;
import com.cargofy.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PutMapping("/{id}")
    public ResponseEntity<UpdateProfileResponse> updateProfile(@PathVariable Long id,
                                                               @Valid @RequestBody UpdateUserRequest request,
                                                               Authentication authentication) {
        UpdateProfileResponse response = userService.updateProfile(id, authentication.getName(), request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Void> changePassword(@PathVariable Long id,
                                               @Valid @RequestBody ChangePasswordRequest request,
                                               Authentication authentication) {
        userService.changePassword(id, authentication.getName(), request);
        return ResponseEntity.noContent().build();
    }
}