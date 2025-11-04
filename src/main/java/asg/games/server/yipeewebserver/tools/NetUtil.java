package asg.games.server.yipeewebserver.tools;

import asg.games.yipee.common.net.NetYipeeKeyMap;
import asg.games.yipee.common.net.NetYipeePlayer;
import asg.games.yipee.core.objects.YipeeKeyMap;
import asg.games.yipee.core.objects.YipeePlayer;

public class NetUtil {

     public static YipeePlayer getPlayerFromNetYipeePlayer(NetYipeePlayer netYipeePlayer) {
         if(netYipeePlayer instanceof YipeePlayer) {
             return (YipeePlayer) netYipeePlayer;
         }

         YipeePlayer player = new YipeePlayer();
         player.setIcon(netYipeePlayer.getIcon());
         player.setRating(netYipeePlayer.getRating());
         player.setName(netYipeePlayer.getName());
        return player;
    }

    public static YipeeKeyMap getPlayerFromNetYipeePlayer(NetYipeeKeyMap netYipeeKeyMap) {
        if(netYipeeKeyMap instanceof YipeeKeyMap) {
            return (YipeeKeyMap) netYipeeKeyMap;
        }

        YipeeKeyMap newYipeeKeyMap = new YipeeKeyMap();
        return newYipeeKeyMap;
    }
}
