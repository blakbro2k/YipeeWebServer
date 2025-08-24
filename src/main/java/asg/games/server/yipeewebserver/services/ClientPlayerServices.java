package asg.games.server.yipeewebserver.services;

import asg.games.yipee.core.objects.YipeePlayer;
import com.badlogic.gdx.utils.ObjectMap;

public interface ClientPlayerServices {
    /** Player Controls
     *  Methods control the flow of a player and client */
    /** Must link clientID to Player ID to Player Object. */
    void registerPlayer(String clientId, YipeePlayer player) throws Exception;

    /** Remove Registered Player. */
    /** Should remove the from all tables, seats, games and rooms **/
    void unRegisterPlayer(String clientID) throws Exception;

    /** Get Registered Player given Yipee Id. */
    YipeePlayer getRegisteredPlayerGivenId(String playerId);

    /** Get Registered Player given YipeeObject. */
    YipeePlayer getRegisteredPlayer(YipeePlayer player);

    /** Gets all registered players. */
    ObjectMap.Values<YipeePlayer> getAllRegisteredPlayers();

    /** Check if client id is registered **/
    boolean isClientRegistered(String clientId);

    /** Check if player id is registered **/
    boolean isPlayerRegistered(String playerId);
}
