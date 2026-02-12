package com.example.migrasi.repository;

import com.example.migrasi.model.CustomerDraft;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<CustomerDraft, Long> {
}
