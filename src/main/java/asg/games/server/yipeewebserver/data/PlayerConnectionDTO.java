package asg.games.server.yipeewebserver.data;

import asg.games.yipee.objects.AbstractYipeeObject;
import asg.games.yipee.objects.YipeePlayer;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "YT_PLAYER_CONNECTION")
public class PlayerConnectionDTO extends AbstractDTO {
    @Transient
    private static final Logger logger = LoggerFactory.getLogger(PlayerConnectionDTO.class);

    private String clientId;
    private long timeStamp;
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "player_id")
    private YipeePlayer player;
    private boolean connected;

    public PlayerConnectionDTO() {
        super();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlayerConnectionDTO)) return false;
        if (!super.equals(o)) return false;
        PlayerConnectionDTO that = (PlayerConnectionDTO) o;
        return timeStamp == that.timeStamp && connected == that.connected && Objects.equals(clientId, that.clientId) && Objects.equals(sessionId, that.sessionId) && Objects.equals(player, that.player);
    }

    @Override
    public int hashCode() {
       return Objects.hash(super.hashCode(), clientId, timeStamp, sessionId, player, connected);
    }

    @Override
    public PlayerConnectionDTO copy() {
        PlayerConnectionDTO copy = new PlayerConnectionDTO();
        copyParent(copy);
        copy.setClientId(this.clientId);
        copy.setTimeStamp(this.timeStamp);
        copy.setSessionId(this.sessionId);
        copy.setConnected(this.connected);
        return copy;
    }

    @Override
    public PlayerConnectionDTO deepCopy() {
        PlayerConnectionDTO deepCopy = copy();
        deepCopy.setPlayer(this.player.deepCopy());
        return deepCopy;
    }
}