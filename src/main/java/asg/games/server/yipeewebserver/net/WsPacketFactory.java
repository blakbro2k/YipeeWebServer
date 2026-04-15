package asg.games.server.yipeewebserver.net;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public final class WsPacketFactory {

    private WsPacketFactory() {}

    /**
     * Creates a GameAuthTokenResponse if that class exists in yipee-net at runtime.
     * Falls back to a Map JSON payload if the class/fields don't match.
     */
    public static Object gameAuthTokenResponse(String sessionJwt,
                                               String sessionId,
                                               String clientId,
                                               String playerId,
                                               String gameId,
                                               String tableId,
                                               Integer seatIndex,
                                               Instant expiresAt) {

        // Try real packet class first (no compile-time dependency on its fields)
        try {
            Class<?> clazz = Class.forName("asg.games.yipee.net.packets.GameAuthTokenResponse");
            Object instance = clazz.getDeclaredConstructor().newInstance();

            setIfPresent(clazz, instance, "setAuthToken", String.class, sessionJwt);
            setIfPresent(clazz, instance, "setToken", String.class, sessionJwt);
            setIfPresent(clazz, instance, "setSessionJwt", String.class, sessionJwt);

            setIfPresent(clazz, instance, "setSessionId", String.class, sessionId);
            setIfPresent(clazz, instance, "setClientId", String.class, clientId);
            setIfPresent(clazz, instance, "setPlayerId", String.class, playerId);
            setIfPresent(clazz, instance, "setGameId", String.class, gameId);
            setIfPresent(clazz, instance, "setTableId", String.class, tableId);
            setIfPresent(clazz, instance, "setSeatIndex", Integer.class, seatIndex);
            setIfPresent(clazz, instance, "setSeatIndex", int.class, seatIndex == null ? 0 : seatIndex);

            // Some responses use String ISO time
            if (expiresAt != null) {
                setIfPresent(clazz, instance, "setExpiresAt", String.class, expiresAt.toString());
                setIfPresent(clazz, instance, "setExpiresAt", Instant.class, expiresAt);
            }

            return instance;
        } catch (ClassNotFoundException e) {
            log.warn("GameAuthTokenResponse class not found in runtime classpath; using Map fallback.");
        } catch (Exception e) {
            log.warn("Failed to build GameAuthTokenResponse reflectively; using Map fallback. {}", e.getMessage());
        }

        // Fallback JSON payload (client can still parse this)
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("packetType", "GameAuthTokenResponse");
        m.put("authToken", sessionJwt);
        m.put("sessionId", sessionId);
        m.put("clientId", clientId);
        m.put("playerId", playerId);
        m.put("gameId", gameId);
        m.put("tableId", tableId);
        m.put("seatIndex", seatIndex);
        m.put("expiresAt", expiresAt == null ? null : expiresAt.toString());
        return m;
    }

    /**
     * Server-minted JWT used for authenticated game API and game transport calls after launch.
     *
     * <p>This token is derived from the validated launch token and represents the
     * authenticated game session context (e.g., scope {@code game_session}).
     *
     * <p><b>Client behavior:</b> store this value and use it as the {@code Authorization: Bearer ...}
     * token for subsequent {@code /api/game/*} calls and (optionally) WebSocket authentication.</p>
     */
    private String gameToken;
    private static void setIfPresent(Class<?> clazz, Object instance,
                                     String methodName, Class<?> paramType, Object value) {
        try {
            Method m = clazz.getMethod(methodName, paramType);
            m.invoke(instance, value);
        } catch (NoSuchMethodException ignored) {
            // ok: field name differs in your yipee-net JAR
        } catch (Exception e) {
            log.debug("Setter failed {}.{}({}): {}", clazz.getSimpleName(), methodName, paramType.getSimpleName(), e.getMessage());
        }
    }
}