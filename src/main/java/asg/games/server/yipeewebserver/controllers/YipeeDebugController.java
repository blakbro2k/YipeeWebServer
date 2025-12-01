package asg.games.server.yipeewebserver.controllers;

import asg.games.server.yipeewebserver.net.YipeePacketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/debug/yipee")
@RequiredArgsConstructor
public class YipeeDebugController {
    private final YipeePacketHandler packetHandler;

    @GetMapping("/ws-test")
    public String wsTestPage() {
        return """
        <html>
        <body>
        <h3>Yipee WebSocket Test</h3>
        <button onclick="connect()">Connect</button>
        <button onclick="send()">Send</button>
        <pre id="log"></pre>

        <script>
        let ws;

        function connect() {
            ws = new WebSocket("ws://localhost:8080/ws/yipee");
            ws.onmessage = e => log(e.data);
            log("connected");
        }

        function send() {
            ws.send(JSON.stringify({
                type: "ClientHandshakeRequest",
                clientId: "123",
                playerId: "456"
            }));
        }

        function log(msg){
            document.getElementById('log').textContent += "\\n" + msg;
        }
        </script>
        </body>
        </html>
    """;
    }

}
