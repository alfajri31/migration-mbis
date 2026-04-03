package com.example.migrasi.repository;

import com.example.migrasi.model.UserMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository("userMappingRepoJpa")
public interface UserMappingRepository extends JpaRepository<UserMapping, UUID> {
    List<UserMapping> findAllByBranchId(String branchId);
}
