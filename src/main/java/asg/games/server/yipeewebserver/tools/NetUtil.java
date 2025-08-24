package asg.games.server.yipeewebserver.tools;

import asg.games.yipee.common.net.NetYipeePlayer;
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
}
