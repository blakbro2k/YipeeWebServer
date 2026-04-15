package asg.games.server.yipeewebserver.controllers;

public final class ControllerConstants {

    private ControllerConstants() {}

    // -------------------------------------------------------
    // Base paths
    // -------------------------------------------------------
    public static final String API_BASE_PATH = "/api";
    public static final String API_PUBLIC_BASE = "/public";
    public static final String API_GAME_BASE = "/game";

    // -------------------------------------------------------
    // Relative controller paths (used with @RequestMapping("/api"))
    // -------------------------------------------------------
    public static final String API_STATUS_PATH = API_PUBLIC_BASE + "/status";
    public static final String API_PLAYER_REGISTER_PATH = API_PUBLIC_BASE + "/player/register";
    public static final String API_SESSION_HANDSHAKE_PATH = API_PUBLIC_BASE + "/session/handshake";

    public static final String API_PLAYER_WHOAMI_PATH = "/player/whoami";
    public static final String API_SESSION_PING_PATH = "/session/ping";

    public static final String API_ROOMS_GET_ALL_PATH = "/rooms";
    public static final String API_ROOMS_JOIN_PATH = "/rooms/{roomId}/join";
    public static final String API_ROOMS_LEAVE_PATH = "/rooms/{roomId}/leave";
    public static final String API_ROOMS_GET_ALL_TABLES_PATH = "/rooms/{roomId}/tables";
    public static final String API_ROOMS_CREATE_TABLE_PATH = "/rooms/{roomId}/table/create";
    public static final String API_ROOMS_TABLE_JOIN_ANY_PATH = "/rooms/{roomId}/table/join";
    public static final String API_ROOMS_GET_PLAYERS_PATH = "/rooms/{roomId}/players";

    public static final String API_TABLES_GET_TABLE_PATH = "/tables/{tableId}";
    public static final String API_TABLES_JOIN_PATH = "/tables/{tableId}/join";
    public static final String API_TABLES_LEAVE_PATH = "/tables/{tableId}/leave";
    public static final String API_TABLES_SITDOWN_PATH = "/tables/{tableId}/sit-down";
    public static final String API_TABLES_STANDUP_PATH = "/tables/{tableId}/stand-up";
    public static final String API_TABLES_WATCHERS_PATH = "/tables/{tableId}/watchers";

    public static final String API_GAME_LAUNCH_TOKEN_PATH = API_GAME_BASE + "/launch-token";
    public static final String API_GAME_WHOAMI_PATH = API_GAME_BASE + "/whoami";
    public static final String API_GAME_TABLE_PATH = API_GAME_BASE + "/table";

    // -------------------------------------------------------
    // Full request URI constants (used by filters/resolvers)
    // -------------------------------------------------------
    public static final String FULL_API_PUBLIC_BASE = API_BASE_PATH + API_PUBLIC_BASE;
    public static final String FULL_API_GAME_LAUNCH_TOKEN_PATH = API_BASE_PATH + API_GAME_LAUNCH_TOKEN_PATH;
    public static final String FULL_API_GAME_BASE = API_BASE_PATH + API_GAME_BASE;
}