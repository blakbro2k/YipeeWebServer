package asg.games.server.yipeewebserver.services.impl;

import asg.games.server.yipeewebserver.data.PlayerConnectionEntity;
import asg.games.server.yipeewebserver.data.YipeeTableOccupancyEntity;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.persistence.YipeeRepository;
import asg.games.server.yipeewebserver.persistence.YipeeRoomRepository;
import asg.games.server.yipeewebserver.persistence.YipeeSeatRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableOccupancyRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableRepository;
import asg.games.server.yipeewebserver.services.TableService;
import asg.games.yipee.common.enums.YipeeObject;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.objects.YipeeRoom;
import asg.games.yipee.core.objects.YipeeSeat;
import asg.games.yipee.core.objects.YipeeTable;
import asg.games.yipee.core.persistence.AbstractStorage;
import asg.games.yipee.core.persistence.Updatable;
import asg.games.yipee.core.tools.Util;
import ch.qos.logback.core.util.StringUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class YipeeGameJPAServiceImpl extends AbstractStorage {
    private final Map<Class<? extends YipeeObject>, YipeeRepository<? extends YipeeObject, String>> repoMap = new HashMap<>();

    private final YipeeRoomRepository yipeeRoomRepository;
    private final YipeeTableRepository yipeeTableRepository;
    private final YipeePlayerRepository yipeePlayerRepository;
    private final YipeeSeatRepository yipeeSeatRepository;
    private final YipeeClientConnectionRepository yipeeClientConnectionRepository;
    private final YipeeTableOccupancyRepository yipeeTableOccupancyRepository;

    @PostConstruct
    public void init() {
        // Register concrete entity types to their repos
        register(YipeeRoom.class, yipeeRoomRepository);
        register(YipeeTable.class, yipeeTableRepository);
        register(YipeeSeat.class, yipeeSeatRepository);
        register(YipeePlayer.class, yipeePlayerRepository);

        // Only include DTOs here if they really are JPA entities extending YipeeObject.
        // If PlayerConnectionEntity is a real @Entity extending YipeeObject, keep it:
        //register(PlayerConnectionEntity.class, yipeeClientConnectionRepository);
    }

    private <T extends YipeeObject> void register(Class<T> type, YipeeRepository<T, String> repo) {
        repoMap.put(type, repo);
    }

    @SuppressWarnings("unchecked")
    private <T extends YipeeObject> YipeeRepository<T, String> getRepository(Class<T> clazz) {
        // Exact match first
        YipeeRepository<? extends YipeeObject, String> repo = repoMap.get(clazz);
        if (repo != null) return (YipeeRepository<T, String>) repo;

        // Walk up type hierarchy as a fallback (in case you registered a superclass)
        Class<?> c = clazz.getSuperclass();
        while (c != null && YipeeObject.class.isAssignableFrom(c)) {
            repo = repoMap.get(c);
            if (repo != null) return (YipeeRepository<T, String>) repo;
            c = c.getSuperclass();
        }
        throw new IllegalStateException("No repository found for class: " + clazz.getName());
    }

    @Override
    public void dispose() {
        //Spring takes care of destroying repository
    }

    // ---------- Reads ----------

    @Override
    @Transactional(readOnly = true)
    public <T extends YipeeObject> T getObjectByName(Class<T> clazz, String name) {
        log.trace("Enter getObjectByName()={}, {}", clazz, name);
        if (clazz == null || name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Class and name must not be null or empty.");
        }

        YipeeRepository<T, String> repository = getRepository(clazz);
        log.trace("repository()={}", repository.getClass().getSimpleName());
        Optional<T> objectByName = Optional.ofNullable(repository.findByName(name));
        log.trace("exit getObjectByName()={}", objectByName);
        return objectByName.orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public <T extends YipeeObject> T getObjectById(Class<T> clazz, String id)  {
        JpaRepository<T, String> repository = getRepository(clazz);
        return repository.findById(id).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public <T extends YipeeObject> List<T> getAllObjects(Class<T> clazz) {
        JpaRepository<T, String> repository = getRepository(clazz);
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public <T extends YipeeObject> long countAllObjectsLong(Class<T> clazz) {
        // Prefer returning long; keep a legacy int wrapper if needed
        return getRepository(clazz).count();
    }

    @Override
    @Transactional(readOnly = true)
    public <T extends YipeeObject> int  countAllObjects(Class<T> clazz) {
        JpaRepository<T, String> repository = getRepository(clazz);
        return Util.otoi(repository.count());
    }

    // ---------- Writes ----------

    @SuppressWarnings("unchecked")
    private <T extends YipeeObject> void internalSave(T object) {
        log.debug("enter internalSave({})", object);
        if (object == null) {
            throw new IllegalArgumentException("Cannot save a null object.");
        }

        JpaRepository<T, String> repository = getRepository((Class<T>) object.getClass());
        repository.save(object);
        log.debug("exit internalSave()");
    }

    @SuppressWarnings("unchecked")
    private <T extends YipeeObject> boolean internalDelete(T object) {
        log.debug("enter internalDelete({})", object);
        if (object == null) {
            throw new IllegalArgumentException("Cannot delete a null object.");
        }

        JpaRepository<T, String> repository = getRepository((Class<T>) object.getClass());
        repository.delete(object);
        log.debug("exit internalDelete()");
        return true;
    }

    private void saveOrUpdateObject(YipeeObject object) {
        log.debug("saveOrUpdateObject object={}", object);
        if (object == null) {
            throw new IllegalArgumentException("Cannot save a null object.");
        }

        // Try to find existing by ID first
        YipeeObject existing = null;
        if (object.getId() != null && !object.getId().isEmpty()) {
            existing = getObjectById(object.getClass(), object.getId());
        }

        // Fallback to finding by name if ID lookup failed
        if (existing == null && object.getName() != null && !object.getName().isEmpty()) {
            existing = getObjectByName(object.getClass(), object.getName());
        }

        if (existing != null) {
            // Merge incoming fields into the managed entity (avoid copying id/created)
            copyMutableFields(object, existing);
            // timestamps handled by @PreUpdate; no need to touch created/modified here
            internalSave(existing);
        } else {
            // New row
            internalSave(object);
        }
        log.debug("exit saveOrUpdateObject()");
    }

    @SuppressWarnings("unchecked")
    private <T extends YipeeObject> void copyMutableFields(T src, T managed) {
        if (managed instanceof Updatable) {
            ((Updatable<T>) managed).updateFrom(src);
        } else {
            // minimal default for rare cases
            managed.setName(src.getName());
        }
    }

    @Override
    @Transactional
    public void saveObject(YipeeObject object) {
        log.debug("enter saveObject({})", object);
        // Save parent object first
        saveOrUpdateObject(object);

        log.debug("exit saveObject()");
    }

    @Override
    @Transactional
    public boolean deleteObject(YipeeObject object) {
        log.debug("enter internalDelete({})", object);
        boolean successful = true;

        try {
            // Attempt to delete child objects first
            successful = internalDelete(object);
        } catch (Exception e) {
            log.error("There was an exception while deleting object: {}", object, e);
            successful = false;
        }
        log.debug("exit internalDelete()={}", successful);
        return successful;
    }

    @Override
    public void commitTransactions() {
        // No-op: Spring manages transaction commits via @Transactional
        // This method is here to satisfy the AbstractStorage contract.
    }

    @Override
    public void rollBackTransactions() {
        // No-op: Spring manages rollbacks automatically on exceptions.
        // If you need explicit rollback, throw a RuntimeException inside a @Transactional method.
    }

    // ---------- Identity helpers ----------

    @Transactional(readOnly = true)
    public YipeePlayer findPlayerByExternalIdentity(String provider, String externalUserId) {
        log.debug("findPlayerByExternalIdentity({}, {})", provider, externalUserId);
        PlayerConnectionEntity identity = yipeeClientConnectionRepository.findByProviderAndExternalUserId(provider, externalUserId).orElse(null);
        return identity != null ? identity.getPlayer() : null;
    }

    @Transactional
    public YipeePlayer linkPlayerToExternalIdentity(
            String provider,
            String externalUserId,
            YipeePlayer player,
            String clientId
    ) {
        log.debug("linkPlayerToExternalIdentity({}, {}, playerId={}, clientId={})",
                provider, externalUserId,
                player != null ? player.getId() : null,
                clientId);
        if (player == null) {
            throw new IllegalArgumentException("player must not be null");
        }

        // 1) Find existing or use the passed-in instance
        YipeePlayer persistentPlayer = yipeePlayerRepository.findByName(player.getName());
        if (persistentPlayer == null) {
            // new registration: use the incoming object
            persistentPlayer = player;
        } else {
            // existing player: optionally update fields from request
            persistentPlayer.setIcon(player.getIcon());
            persistentPlayer.setRating(player.getRating());
            // name usually stays the same
        }

        // This call will either INSERT (new) or UPDATE (existing)
        persistentPlayer = yipeePlayerRepository.save(persistentPlayer);

        // 2) Upsert the connection row
        String connectionName = buildIdentityName(persistentPlayer); // e.g. connection:{name}:{id}

        PlayerConnectionEntity connection = yipeeClientConnectionRepository
                .findOptionalByName(connectionName)             // add this method to repo
                .orElseGet(PlayerConnectionEntity::new);

        connection.setName(connectionName);
        connection.setClientId(clientId);
        connection.setProvider(provider);
        connection.setExternalUserId(externalUserId);
        connection.setPlayer(persistentPlayer);
        connection.setConnected(false);

        yipeeClientConnectionRepository.save(connection);

        return persistentPlayer;
    }

    private String buildIdentityName(YipeePlayer player) {
        String buildName = "connection:no_player:no_id";
        if(player != null) {
            buildName = StringUtil.nullStringToEmpty("connection:") +
                    StringUtil.nullStringToEmpty(player.getName()) +
                    StringUtil.nullStringToEmpty(":") +
                    StringUtil.nullStringToEmpty(player.getId());
        }
        return buildName;
    }

    @Transactional
    public void updateLastActivity(String sessionId) {
        yipeeClientConnectionRepository.findBySessionId(sessionId).ifPresentOrElse(conn -> {
            conn.touch();
            yipeeClientConnectionRepository.save(conn);
            log.debug("Updated lastActivity for session {}", sessionId);
        }, () -> {
            log.warn("Attempted activity update for unknown session: {}", sessionId);
        });
    }

    // ---------- Game room helpers ----------

    @Transactional
    public YipeeRoom joinRoom(String playerId, String roomId) {
        YipeePlayer player = yipeePlayerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        YipeeRoom room = yipeeRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        // uses your helper that maintains both sides
        room.joinRoom(player);

        // Hibernate will flush on commit; returning the managed room is enough
        return room;
    }

    public YipeeTable createTable(String playerId,
                                  String roomId,
                                  boolean rated,
                                  boolean soundOn,
                                  String accessType) {
        YipeeTable table = createLocalTable(playerId,
                roomId,
                rated,
                soundOn,
                accessType
        );

        yipeeTableOccupancyRepository.save(new YipeeTableOccupancyEntity(table.getId()));

        return table;
    }

    @Transactional
    private YipeeTable createLocalTable(String playerId,
                                  String roomId,
                                  boolean rated,
                                  boolean soundOn,
                                  String accessType) {
        YipeePlayer player = yipeePlayerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        YipeeRoom room = yipeeRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        // uses your helper that maintains both sides
        Map<String, Object> arguments = new HashMap<>();
        arguments.put(YipeeTable.ARG_RATED, rated);
        arguments.put(YipeeTable.ARG_SOUND, soundOn);
        arguments.put(YipeeTable.ARG_TYPE, accessType);

        YipeeTable table = room.addTable(arguments);
        table.addWatcher(player);

        yipeeTableRepository.save(table);

        // Hibernate will flush on commit; returning the managed room is enough
        return table;
    }

    @Transactional
    public YipeeTable joinTable(String playerId,
                                String roomId,
                                Integer tableNumber,
                                boolean createIfMissing) {

        YipeeRoom room = getRoomById(roomId);

        YipeeTable table = null;
        if(tableNumber > 0) {
            // Explicit table selection (e.g. clicking "Play Now" on a specific table)
            table = room.getTableIndexMap().values().stream()
                    .filter(Objects::nonNull)
                    .filter(t -> t.getTableNumber() == tableNumber)
                    .findFirst()
                    .orElseGet(() -> {
                        if (!createIfMissing) {
                            throw new IllegalStateException(
                                    "Table number " + tableNumber +
                                            " does not exist in room " + roomId +
                                            " and createIfMissing=false"
                            );
                        }
                        // Create a new table in this room.
                        // This uses your existing createTable JPA method.
                        return createTable(
                                playerId,
                                roomId,
                                /* rated   */ false,
                                /* soundOn */ true,
                                /* accessType */ "public"
                        );
                    });
        } else {
            // PLAY NOW PATH: auto-pick a table
            table = findExistingTableWithFreeSeat(room)
                    .orElseGet(() -> {
                        if (!createIfMissing) {
                            throw new IllegalStateException("No free tables and createIfMissing=false");
                        }

                        return createTable(
                                playerId,
                                roomId,
                                false,
                                true,
                                "public");
                    });
        }

        return table;
    }

    private void autoSeatPlayer(YipeeTable table, String playerId) {
        if (table == null || playerId == null) {
            return;
        }

        YipeePlayer player = yipeePlayerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        // 1) If they’re already seated at this table, nothing to do
        if (table.getSeats() != null && table.getSeats().stream()
                .anyMatch(seat -> seat != null && player.equals(seat.getSeatedPlayer()))) {
            return;
        }

        // 2) Defensive: clear any duplicate seats on this table (shouldn’t happen, but just in case)
        if (table.getSeats() != null) {
            table.getSeats().forEach(seat -> {
                if (seat != null && player.equals(seat.getSeatedPlayer())) {
                    seat.standUp();
                }
            });
        }

        // 3) Find the first free seat (by seatNumber)
        YipeeSeat freeSeat = table.getSeats().stream()
                .filter(Objects::nonNull)
                .filter(seat -> !seat.isOccupied())
                .sorted(java.util.Comparator.comparingInt(YipeeSeat::getSeatNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No free seat available on table " + table.getId()
                ));

        // 4) Seat the player using your domain method
        // If you have seat.sitDown(player), prefer that; otherwise set fields directly.
        freeSeat.sitDown(player);
        // Or:
        // freeSeat.setSeatedPlayer(player);
        // freeSeat.setSeatReady(false); // or true – your choice

        // 5) Make sure they are no longer a watcher of this table
        if (table.getWatchers() != null && table.getWatchers().remove(player)) {
            log.debug("Removed player {} from watchers when auto-seating on table {}",
                    player.getId(), table.getId());
        }
    }


    private Optional<YipeeTable> findExistingTableWithFreeSeat(YipeeRoom room) {
        if (room == null || room.getTableIndexMap() == null || room.getTableIndexMap().isEmpty()) {
            return Optional.empty();
        }

        // Sort by table number so "Play Now" prefers lower-numbered tables first
        return room.getTableIndexMap().values().stream()
                .sorted(java.util.Comparator.comparingInt(YipeeTable::getTableNumber))
                .filter(this::tableHasFreeSeat)
                .findFirst();
    }

    private boolean tableHasFreeSeat(YipeeTable table) {
        if (table == null || table.getSeats() == null) {
            return false;
        }
        return table.getSeats().stream()
                .anyMatch(seat -> seat != null && !seat.isOccupied());
    }


    @Transactional
    public void leaveRoom(String playerId, String roomId) {
        YipeePlayer player = yipeePlayerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        YipeeRoom room = yipeeRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));

        // 1) Remove player from all tables in this room (watcher + seats)
        room.getTableIndexMap().values().forEach(table -> {
            // remove as watcher
            table.getWatchers().remove(player);

            // stand up from any seat they occupy
            table.getSeats().forEach(seat -> {
                if (player.equals(seat.getSeatedPlayer())) {
                    seat.standUp();  // clears ready + seatedPlayer
                }
            });
        });

        // 2) Remove from room players (and player's rooms via your helper)
        room.leaveRoom(player);
        // JPA will flush on commit
    }

    @Transactional(readOnly = true)
    public List<YipeeRoom> getAllRooms() {
        return yipeeRoomRepository.findAll();
    }

    @Transactional(readOnly = true)
    public YipeeRoom getRoomOrThrow(String roomId) {
        return yipeeRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
    }

    @Transactional
    public void leaveTable(String playerId, String tableId) {
        YipeePlayer player = yipeePlayerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        YipeeTable table = yipeeTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        // 1) Remove as watcher
        table.getWatchers().remove(player);

        // 2) Stand up from any seat they occupy at this table
        table.getSeats().forEach(seat -> {
            if (player.equals(seat.getSeatedPlayer())) {
                seat.standUp();
            }
        });

        // no delete; we’re just detaching player from this
        // table is managed; changes will flush on commit
    }

    @Transactional
    public YipeeSeat sitDown(String playerId, String tableId, int seatNumber) {
        // Load managed entities
        YipeeTable table = yipeeTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        YipeePlayer player = yipeePlayerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        // 1) Make sure player is not already seated at this table
        table.getSeats().forEach(seat -> {
            if (seat.isOccupied()
                    && seat.getSeatedPlayer() != null
                    && playerId.equals(seat.getSeatedPlayer().getId())) {
                    seat.standUp();
                    //throw new IllegalStateException("Player " + playerId + " is already seated at table " + tableId);
            }
        });

        // 2) Find the requested seatNumber on this table
        YipeeSeat targetSeat = table.getSeats().stream()
                .filter(s -> s.getSeatNumber() == seatNumber)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Seat " + seatNumber + " not found for table " + tableId)
                );

        // 3) Ensure that seat is not already occupied
        if (targetSeat.isOccupied() && targetSeat.getSeatedPlayer() != null) {
            throw new IllegalStateException(
                    "Seat " + seatNumber + " at table " + tableId + " is already occupied by player "
                            + targetSeat.getSeatedPlayer().getId()
            );
        }

        // 4) Assign the player to the seat
        targetSeat.setSeatedPlayer(player);
        targetSeat.setSeatReady(false);  // or whatever your default is
        // isOccupied could be derived from seatedPlayer, or set explicitly:
        // targetSeat.setOccupied(true);

        return targetSeat;
    }


    @Transactional
    public YipeeSeat standUp(String playerId, String tableId) {
        YipeePlayer player = yipeePlayerRepository.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Player not found: " + playerId));

        YipeeTable table = yipeeTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));

        // Find the seat where this player is seated
        YipeeSeat seat = table.getSeats().stream()
                .filter(s -> player.equals(s.getSeatedPlayer()))
                .findFirst()
                .orElse(null);

        if (seat == null) {
            // Idempotent: nothing to do, but still "success"
            return null;
        }

        seat.standUp();
        return seat;
    }

    @Transactional
    public void removePlayerCompletely(String playerId) {
        YipeePlayer player = yipeePlayerRepository.findById(playerId)
                .orElse(null);
        if (player == null) {
            return; // already gone
        }

        // 1) Remove from all rooms
        yipeeRoomRepository.findByPlayers_Id(playerId).forEach(room -> {
            room.getPlayers().removeIf(p -> playerId.equals(p.getId()));
            // because YipeeRoom is owning side of @ManyToMany, this is enough
        });

        // 2) Remove from all table watchers
        yipeeTableRepository.findByWatchers_Id(playerId).forEach(table -> {
            table.getWatchers().removeIf(p -> playerId.equals(p.getId()));
        });

        // 3) Stand them up from all seats
        // clears seatedPlayer + isSeatReady
        // or explicitly: seat.setSeatedPlayer(null); seat.setSeatReady(false);
        yipeeSeatRepository.findBySeatedPlayer_Id(playerId).forEach(YipeeSeat::standUp);

        // 4) Remove PlayerConnectionEntity rows for this player (if you like)
        yipeeClientConnectionRepository.deleteAllByPlayerId(playerId);

        // 5) Finally, delete the player entity
        yipeePlayerRepository.delete(player);
    }

    @Transactional(readOnly = true)
    public java.util.Set<YipeePlayer> getTableWatchers(String tableId) {
        YipeeTable table = yipeeTableRepository.findById(tableId)
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableId));
        return table.getWatchers();
    }

    @Transactional(readOnly = true)
    public java.util.Set<YipeePlayer> getRoomPlayers(String roomId) {
        YipeeRoom room = yipeeRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
        return room.getPlayers();
    }

    /**
     * Convenience method if you want just tables for a room id.
     */
    @Transactional(readOnly = true)
    public List<YipeeTable> getTablesForRoom(String roomId) {
        YipeeRoom room = getRoomOrThrow(roomId);
        // assuming you still have tableIndexMap in YipeeRoom
        return room.getTableIndexMap()
                .values()
                .stream()
                .toList();
    }

    @Transactional(readOnly = true)
    public YipeeRoom getRoomById(String roomId) {
        return yipeeRoomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + roomId));
    }

    // -----------------------------------------------
    // 2) Mark a session as disconnected
    //    (e.g., explicit logout or server-side eviction)
    // -----------------------------------------------
    @Transactional
    public void disconnectSession(String sessionId) {
        yipeeClientConnectionRepository.findBySessionId(sessionId).ifPresent(conn -> {
            conn.setConnected(false);
            conn.setDisconnectedAt(Instant.now());
            log.info("Disconnected sessionId={} for playerId={}", sessionId, conn.getPlayer() != null ? conn.getPlayer().getId() : null);
            yipeeClientConnectionRepository.save(conn); // optional, explicit
        });
    }
}
