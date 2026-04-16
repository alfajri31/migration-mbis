package com.example.migrasi.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "m_user")
@Setter
@Getter
public class RUser extends BaseAuditEntity {
    @Id
    private UUID id;
    private String username;
    private String nip;
    private String description;
}
