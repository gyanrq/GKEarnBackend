package com.myworld.modules.notification.domain;

import com.myworld.core.domain.BaseEntity;
import com.myworld.modules.identity.domain.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    // FIX: Boolean wrapper (not primitive boolean) so @Builder.Default works
    // and Jackson serializes correctly as "read" not "isRead"
    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean read = false;

    public boolean isRead() {
        return Boolean.TRUE.equals(read);
    }

    public void setRead(boolean read) {
        this.read = read;
    }
}