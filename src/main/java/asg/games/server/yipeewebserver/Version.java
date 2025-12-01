package asg.games.server.yipeewebserver;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Version {
    private static String version = "0";
    private static String major = "4";
    private static String minor = "0";
    private static String patch = "0";
    private final static char SEPARATOR = '.';

    public static String printVersion() {
        return version + SEPARATOR + major + SEPARATOR + minor + SEPARATOR + patch;
    }
}
