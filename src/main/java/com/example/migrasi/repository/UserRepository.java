package com.example.migrasi.repository;

import com.example.migrasi.model.User;
import com.example.migrasi.model.UserMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository("userRepoJpa")
public interface UserRepository extends JpaRepository<User, UUID> {

}
