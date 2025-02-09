package asg.games.server.yipeewebserver.data;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

@Setter
@Getter
public class WebPlayerDTO implements Serializable {
    private static final Logger logger = LoggerFactory.getLogger(WebPlayerDTO.class);

    private String username;
    private int rating;
    private int icon;
}
