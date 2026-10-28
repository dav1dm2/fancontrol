package com.example.fancontrol.repository;

import com.example.fancontrol.model.OperatorUser;
import com.example.fancontrol.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OperatorUserRepository extends JpaRepository<OperatorUser, Long> {

    Optional<OperatorUser> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    long countByRole(Role role);

    List<OperatorUser> findAllByOrderByNameAsc();
}
