package asg.games.server.yipeewebserver;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Version {
    private static String version = "0";
    private static String major = "3";
    private static String minor = "0";
    private static String patch = "0";
    private final static char SEPARATOR = '.';

    public static Object printVersion() {
        return version + SEPARATOR + major + SEPARATOR + minor + SEPARATOR + patch;
    }
}
