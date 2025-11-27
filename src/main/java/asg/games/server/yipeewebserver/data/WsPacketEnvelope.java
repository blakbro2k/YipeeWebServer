package asg.games.server.yipeewebserver.data;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class WsPacketEnvelope {
    private String packetType; // e.g. "ClientHandshakeRequest"
    private JsonNode payload;  // raw JSON payload
}