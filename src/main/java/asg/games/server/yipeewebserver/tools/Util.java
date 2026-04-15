package asg.games.server.yipeewebserver.tools;

import asg.games.server.yipeewebserver.config.ServerIdentity;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.objects.YipeeSeat;
import asg.games.yipee.core.objects.YipeeTable;
import asg.games.yipee.common.net.wire.AbstractClientRequest;
import asg.games.yipee.common.net.wire.AbstractServerResponse;
import asg.games.yipee.net.tools.NetUtil;
import asg.games.yipee.common.net.packets.PlayerSummary;
import asg.games.yipee.common.net.packets.SeatDetailSummary;
import asg.games.yipee.common.net.packets.SeatSummary;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Slf4j
public class Util {
    // ========================================================================
    //  Utility: envelope + meta
    // ========================================================================

    public static void copyEnvelope(AbstractClientRequest req, AbstractServerResponse resp) {
        resp.setGameId(req.getGameId());
        resp.setSessionId(req.getSessionId());
        // serverId / tick / timestamp / tickRate are filled by stampServerMeta
    }

    public static void stampServerMeta(AbstractServerResponse resp, ServerIdentity serverIdentity) {
        resp.setServerId(serverIdentity.getFullId());
        resp.setServerTimestamp(System.currentTimeMillis());
        // Leave serverTick/tickRate to the caller if you want real values,
        // or keep them 0 for now (like your handshake).
    }

    public static List<SeatDetailSummary> buildSeatSummaryList(YipeeTable table) {
        List<SeatDetailSummary> summaries = new LinkedList<>();
        if(table != null) {
            for(YipeeSeat seat : table.getSeats()) {
                if(seat != null) {
                    summaries.add(NetUtil.newSeatDetailSummary(
                            buildSeatSummary(seat),
                            buildPlayerSummary(seat.getSeatedPlayer())));
                }
            }
        }
        return summaries;
    }

    public static SeatSummary buildSeatSummary(YipeeSeat seat) {
        SeatSummary seatSummary = null;
        if(seat != null) {
            String seatedPlayerId = seat.isOccupied() ? seat.getSeatedPlayer().getId() : null;
            String seatedPlayerName = seat.isOccupied() ? seat.getSeatedPlayer().getName() : null;
            seatSummary = NetUtil.newSeatSummary(seat.getId(), seat.getSeatNumber(), seat.isSeatReady(), seat.isOccupied(), seatedPlayerId, seatedPlayerName);
        }
        return seatSummary;
    }

    public static PlayerSummary buildPlayerSummary(YipeePlayer player) {
        PlayerSummary playerSummary = null;
        if(player != null) {
            playerSummary = NetUtil.newPlayerSummary(player.getId(), player.getName(), player.getIcon(), player.getRating());
        }
        return playerSummary;
    }

    public static List<PlayerSummary> buildPlayerSummaryList(YipeeTable table) {
        List<PlayerSummary> summaries = new LinkedList<>();
        if(table != null) {
            for(YipeePlayer player : table.getWatchers()) {
                if(player != null) {
                    summaries.add(buildPlayerSummary(player));
                }
            }
        }

        return summaries;
    }
}
