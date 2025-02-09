package asg.games.server.yipeewebserver.data;

import asg.games.yipee.objects.YipeeRoom;
import asg.games.yipee.objects.YipeeTable;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import java.io.Serializable;

@Setter
@Getter
public class WebTableDTO implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(WebTableDTO.class);

    private String accessType;
    private YipeeRoom parentRoom;
    private boolean rated;
    private boolean sound;

    public boolean getRated() {
        return rated;
    }

    public boolean getSound() {
        return sound;
    }
}
