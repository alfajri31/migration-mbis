package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "m_product")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product extends BaseAuditEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String productCode;
    private String productName;
    private String productCategory;
    private boolean isToc;
    private BigDecimal defaultPremiumRate;
    private String premiumFormula;
    private String description;
    @Column(name = "cob_code")
    private String cobCode;
    @Column(name = "lob_code")
    private String lobCode;

    private String imageUrl;
    private UUID createdBy;
    private UUID updatedBy;
    private Integer sortOrder;
    private boolean isActive;
    private UUID parentId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "cob_code", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Cob cob;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lob_code", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Lob lob;
}
