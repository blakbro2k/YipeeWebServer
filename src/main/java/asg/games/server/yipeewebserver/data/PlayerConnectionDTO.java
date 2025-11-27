package asg.games.server.yipeewebserver.data;

import asg.games.yipee.core.objects.YipeePlayer;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Objects;

@Slf4j
@Getter
@Setter
@Entity
@Table(
        name = "YT_PLAYER_CONNECTION",
        indexes = {
                @Index(name = "IDX_PLAYER_CONNECTION_PLAYER", columnList = "player_id"),
                @Index(name = "IDX_PLAYER_CONNECTION_SESSION", columnList = "session_id"),
                @Index(name = "IDX_PLAYER_CONNECTION_ACTIVITY", columnList = "last_activity")
        }
)
public class PlayerConnectionDTO extends AbstractDTO {
    @Column(name = "client_id", nullable = false, length = 64)
    private String clientId;

    @Column(name = "session_id", length = 64, unique = true)
    private String sessionId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "external_user_id", nullable = false, length = 128)
    private String externalUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private YipeePlayer player;

    @Column(name = "connected", nullable = false)
    private boolean connected = true;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "last_activity", nullable = false)
    private Instant lastActivity;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt;

    @Column(name = "disconnected_at")
    private Instant disconnectedAt;

    // ----------------------------------------
    // Lifecycle
    // ----------------------------------------

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.connectedAt = now;
        this.lastActivity = now;
    }

    // ----------------------------------------
    // Domain helpers
    // ----------------------------------------

    public void touch() {
        this.lastActivity = Instant.now();
    }

    public boolean isTimedOut(long timeoutSeconds) {
        return Instant.now().minusSeconds(timeoutSeconds).isAfter(this.lastActivity);
    }

    // ----------------------------------------
    // Equality
    // ----------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerConnectionDTO that)) return false;
        if (!super.equals(o)) return false;

        return Objects.equals(sessionId, that.sessionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sessionId);
    }
}
