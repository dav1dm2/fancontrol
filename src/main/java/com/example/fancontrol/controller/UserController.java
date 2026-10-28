package com.example.fancontrol.controller;

import com.example.fancontrol.dto.UserSummaryResponse;
import com.example.fancontrol.service.UserManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public List<UserSummaryResponse> listUsers() {
        return userManagementService.listUsers();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeUser(@PathVariable long id) {
        userManagementService.removeUser(id);
        return ResponseEntity.noContent().build();
    }
}
