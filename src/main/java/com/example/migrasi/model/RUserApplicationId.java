package com.example.migrasi.model;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public class RUserApplicationId implements Serializable {
    private UUID idUser;
    private UUID idApplication;
}