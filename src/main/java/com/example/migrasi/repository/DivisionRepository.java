package com.example.migrasi.repository;

import com.example.migrasi.model.Branch;
import com.example.migrasi.model.Division;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository("divisionRepoJpa")
public interface DivisionRepository extends JpaRepository<Division, String> {
    Optional<Division> findByNameIgnoreCase(String nama);
}
