package com.example.fancontrol.dto;

import com.example.fancontrol.model.Role;

public record AuthenticatedUserResponse(Long id, String name, Role role) {
}
