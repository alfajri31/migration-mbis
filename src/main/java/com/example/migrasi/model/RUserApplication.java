package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "r_user_application")
@Setter
@Getter
@IdClass(RUserApplicationId.class)
public class RUserApplication extends BaseAuditEntity {
    @Id
    private UUID idUser;
    @Id
    private UUID idApplication;
}
