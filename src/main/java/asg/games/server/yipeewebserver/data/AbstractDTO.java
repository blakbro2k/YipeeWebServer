package asg.games.server.yipeewebserver.data;

import asg.games.yipee.core.objects.Copyable;
import asg.games.yipee.core.tools.TimeUtils;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@Getter
@Setter
@MappedSuperclass
public abstract class AbstractDTO implements DTOObject, Copyable<DTOObject> {
    @Transient
    private static final Logger logger = LoggerFactory.getLogger(AbstractDTO.class);

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

    protected void copyParent(DTOObject o) {
        if (o != null) {
            logger.debug("Copying parent attributes to: " + o);
            o.setId((String)null);
            o.setName(this.getName());
            o.setCreated(TimeUtils.millis());
            o.setModified(TimeUtils.millis());
        }
    }
}
