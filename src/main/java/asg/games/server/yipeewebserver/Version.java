package asg.games.server.yipeewebserver;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Version {
    private static String version;
    private static String major;
    private static String minor;
    private static String patch;
    private final static char SEPARATOR = '.';

    public static Object printVersion() {
        return version + SEPARATOR + major + SEPARATOR + minor + SEPARATOR + patch;
    }
}
