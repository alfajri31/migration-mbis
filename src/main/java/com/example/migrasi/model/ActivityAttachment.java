package com.example.migrasi.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_activity_attachements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityAttachment extends BaseAuditEntity {

    @Id
    private UUID id;

    private UUID activityId;

    private String documentType;

    private String documentTitle;

    private String fileUrl;

    private String fileName;

    private long fileSizeBytes;

    private String fileExtension;

    private OffsetDateTime uploadedAt;

    private UUID createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activityId", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    private Activity activity;

}
