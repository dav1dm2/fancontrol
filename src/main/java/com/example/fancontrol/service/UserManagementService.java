package com.example.fancontrol.service;

import com.example.fancontrol.dto.UserSummaryResponse;
import com.example.fancontrol.exception.ApiException;
import com.example.fancontrol.model.OperatorUser;
import com.example.fancontrol.model.Role;
import com.example.fancontrol.repository.OperatorUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserManagementService {

    private final OperatorUserRepository userRepository;

    public UserManagementService(OperatorUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<UserSummaryResponse> listUsers() {
        return userRepository.findAllByOrderByNameAsc().stream()
                .map(user -> new UserSummaryResponse(user.getId(), user.getName()))
                .toList();
    }

    @Transactional
    public void removeUser(long id) {
        OperatorUser user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User with id " + id + " was not found."));

        if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw ApiException.conflict("The last administrator cannot be removed.");
        }

        userRepository.delete(user);
    }
}
