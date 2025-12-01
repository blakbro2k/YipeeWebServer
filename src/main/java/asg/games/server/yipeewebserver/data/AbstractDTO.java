package asg.games.server.yipeewebserver.data;

import asg.games.yipee.common.enums.Copyable;
import asg.games.yipee.core.tools.TimeUtils;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.GenericGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@Slf4j
@Getter
@Setter
@MappedSuperclass
public abstract class AbstractDTO implements DTOObject {
    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid",strategy = "uuid")
    @Column(name = "id", nullable = false,length = 32)
    protected String id;

    @Column(name = "name", nullable = false, unique = true)
    protected String name;

    protected long created;

    protected long modified;

    AbstractDTO() {
        this.setCreated(TimeUtils.millis());
        this.setModified(TimeUtils.millis());
    }

    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o != null && this.getClass() == o.getClass()) {
            AbstractDTO object = (AbstractDTO)o;
            return Objects.equals(this.getId(), object.getId()) && Objects.equals(this.getName(), object.getName());
        } else {
            return false;
        }
    }

    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getCreated(), this.getModified());
    }

    public String toString() {
        String clazz = this.getClass().getSimpleName();
        return clazz + "[" + this.getId() + "," + this.getName() + "]";
    }
}
