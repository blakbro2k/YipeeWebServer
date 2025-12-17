[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-donate-FFDD00?logo=buymeacoffee)](https://buymeacoffee.com/boaten01y)

# Yipee WebServer

Authoritative game server for **Yipee!**, a modernized, network-enabled clone of Yahoo! Towers.  
Built with **Spring Boot**, **LibGDX Headless**, and **KryoNet**.

---

## 🎯 Overview
The Yipee WebServer is the authoritative multiplayer server. It manages:
- Player sessions and connections
- Game rooms and per-tick simulation
- Packet serialization and networking
- Persistence and telemetry (via JPA)
- Integration with Spring Boot for lifecycle, config, and observability

---

## 🏛 Architecture

### Runtime Model
- **Host process**: Spring Boot manages lifecycle, configuration, health checks.
- **Game runtime**: LibGDX headless app (`YipeeServerApplication`) runs inside the process.
- **Tick driver**: Single authoritative loop at configurable `tickRate` (default 20 Hz).

### Major Components
- **Transport (KryoNet)**
    - Manages TCP/UDP connections and packet serialization.
    - IO threads enqueue messages → game loop processes them.

- **Session Directory**
    - Maps `connectionId → SessionInfo{sessionId, playerId, roomId, mailbox}`.
    - Each session has a bounded outbound queue for safe backpressure handling.

- **Room Directory**
    - Maps `roomId → Room`.
    - Each room owns a `ServerGameManager` (authoritative state), input queue, player seat map, and tick counter.

- **ServerGameManager**
    - Deterministic update of game logic each tick.
    - Emits per-seat state deltas and optional periodic snapshots.

- **Broadcaster**
    - Routes per-seat deltas to the correct players/partners.
    - Drains outbound queues fairly; slow clients don’t stall the loop.

- **Persistence Adapter**
    - Asynchronously stores player connections, match results, telemetry.

- **Observability**
    - Spring Actuator endpoints for health and metrics.
    - Micrometer metrics: tick latency, queue sizes, dropped packets, correction counts.

---

## 📦 Packets & Protocol

**Client → Server**
- `Handshake`
- `SeatSelection`
- `StartGame`
- `PlayerAction { seat, tick, timestamp, type, data }`
- `Pong`

**Server → Client**
- `StateDelta { seat, tick, delta }`
- `StateSnapshot { tick, fullState }`
- `Correction { fromTick, authoritativeState }`
- `Ping`

Packet IDs are defined in `packet.xml` and must match client/server exactly.

---

## ⚙️ Configuration

Spring Boot application properties (with env overrides):

```yaml
gameserver:
  port: 54555
  udp:
    port: 54777
  tickrate: 20
  snapshotHz: 10
  jitterLateTicks: 2
```

---

## 🌀 Data Flow

1. **Transport Layer** → KryoNet receives a packet on IO thread.
2. **Session Layer** → Packet validated, enqueued into the correct Room’s input queue.
3. **Tick Loop** → On each tick:
    - Drain inputs, apply to `ServerGameManager`.
    - Advance simulation deterministically.
    - Collect per-seat deltas and snapshots.
4. **Broadcast** → Route updates to the correct sessions via bounded outbound queues.

---

## 🧩 Error Handling

- Invalid packet → ignored + logged.
- Late actions → accepted within `jitterLateTicks`, otherwise corrected.
- Slow clients → outbound queue drops excess, periodic snapshot heals state.

---

## 🚀 Roadmap

**Phase 1 — Core Loop**
- One tick driver
- Room + Session directory
- Enqueued input processing

**Phase 2 — Resilience**
- Bounded queues & backpressure
- Snapshot & correction path
- Health/metrics

**Phase 3 — Persistence & Auth**
- Async database persistence
- JWT-based handshake (optional)

**Phase 4 — Production Polish**
- Performance testing
- Room sharding (if needed)
- Horizontal scale (multi-pod deployments)

---

## 🔧 Tech Stack
- Java 17
- Spring Boot (config, DI, persistence, observability)
- LibGDX Headless (tick loop)
- KryoNet (network transport)
- Hibernate/JPA (optional persistence)

---

## 📝 License
Apache License 2.0 (see `LICENSE` file)

---

## Support

Like what you see and wish to buy be a coffee? Here's the link.
[![Buy Me A Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-donate-FFDD00?logo=buymeacoffee)](https://buymeacoffee.com/boaten01y)
