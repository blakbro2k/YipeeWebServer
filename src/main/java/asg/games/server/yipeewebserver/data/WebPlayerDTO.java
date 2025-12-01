package asg.games.server.yipeewebserver.data;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;

@Slf4j
@Setter
@Getter
public class WebPlayerDTO implements Serializable {
    private String username;
    private int rating;
    private int icon;
}
