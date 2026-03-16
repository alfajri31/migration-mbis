package com.example.migrasi.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "m_postal_code")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostalCode extends BaseAuditEntity {

    @Id
    private Long id;
    private Long subdistrictId;
    private Integer districtId;
    private Integer cityId;
    private Integer provId;
    private Integer postalCode;

}
