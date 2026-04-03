package com.example.migrasi.service;

import com.example.migrasi.model.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("branchRepoJpa")
public interface BranchRepository extends JpaRepository<Branch, String> {

    Optional<Branch> findByNameIgnoreCase(String nama);
}