package com.example.migrasi.repository;

import com.example.migrasi.model.Branch;
import com.example.migrasi.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("roleRepoJpa")
public interface RoleRepository extends JpaRepository<Role, String> {
    Optional<Role> findByIdIgnoreCase(String role);
}
