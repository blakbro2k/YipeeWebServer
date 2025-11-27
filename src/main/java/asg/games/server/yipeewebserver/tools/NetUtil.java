package asg.games.server.yipeewebserver.tools;

import asg.games.yipee.common.dto.NetYipeeKeyMap;
import asg.games.yipee.common.dto.NetYipeePlayer;
import asg.games.yipee.core.objects.YipeeKeyMap;
import asg.games.yipee.core.objects.YipeePlayer;

public class NetUtil {
     public static YipeePlayer getPlayerFromNetYipeePlayer(NetYipeePlayer netYipeePlayer) {
         YipeePlayer player = new YipeePlayer();

         if(netYipeePlayer instanceof YipeePlayer) {
             player = (YipeePlayer) netYipeePlayer;
         }

         player.setIcon(netYipeePlayer.getIcon());
         player.setRating(netYipeePlayer.getRating());
         player.setName(netYipeePlayer.getName());

         return player;
    }

    public static YipeeKeyMap getPlayerFromNetYipeePlayer(NetYipeeKeyMap netYipeeKeyMap) {
        YipeeKeyMap newYipeeKeyMap = new YipeeKeyMap();
        if(netYipeeKeyMap instanceof YipeeKeyMap) {
            newYipeeKeyMap = (YipeeKeyMap) netYipeeKeyMap;
        }

        return newYipeeKeyMap;
    }
}
