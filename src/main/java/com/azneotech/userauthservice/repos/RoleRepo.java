package com.azneotech.userauthservice.repos;

import com.azneotech.userauthservice.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepo extends JpaRepository<Role, Long> {
    Optional<Role> findByValue(String value);
}
