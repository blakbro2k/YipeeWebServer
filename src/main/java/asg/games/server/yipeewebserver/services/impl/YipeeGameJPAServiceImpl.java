package asg.games.server.yipeewebserver.services.impl;

import asg.games.server.yipeewebserver.data.PlayerConnectionDTO;
import asg.games.server.yipeewebserver.persistence.YipeeClientConnectionRepository;
import asg.games.server.yipeewebserver.persistence.YipeePlayerRepository;
import asg.games.server.yipeewebserver.persistence.YipeeRepository;
import asg.games.server.yipeewebserver.persistence.YipeeRoomRepository;
import asg.games.server.yipeewebserver.persistence.YipeeSeatRepository;
import asg.games.server.yipeewebserver.persistence.YipeeTableRepository;
import asg.games.server.yipeewebserver.services.YipeeGameServices;
import asg.games.server.yipeewebserver.services.YipeeTerminateServices;
import asg.games.yipee.core.objects.YipeeObject;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.objects.YipeeRoom;
import asg.games.yipee.core.objects.YipeeSeat;
import asg.games.yipee.core.objects.YipeeTable;
import asg.games.yipee.core.persistence.AbstractStorage;
import asg.games.yipee.core.persistence.TerminatorJPAVisitor;
import asg.games.yipee.core.persistence.Updatable;
import asg.games.yipee.core.persistence.YipeeObjectJPAVisitor;
import asg.games.yipee.core.tools.Util;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class YipeeGameJPAServiceImpl extends AbstractStorage implements YipeeGameServices, YipeeTerminateServices {
    private static final Logger logger = LoggerFactory.getLogger(YipeeGameJPAServiceImpl.class);
    private final Map<Class<? extends YipeeObject>, YipeeRepository<? extends YipeeObject, String>> repoMap = new HashMap<>();

    @Autowired
    YipeeRoomRepository yipeeRoomRepository;

    @Autowired
    YipeeTableRepository yipeeTableRepository;

    @Autowired
    YipeePlayerRepository yipeePlayerRepository;

    @Autowired
    YipeeSeatRepository yipeeSeatRepository;

    @Autowired
    YipeeClientConnectionRepository yipeeClientConnectionRepository;

    @PostConstruct
    public void init() {
        // Register concrete entity types to their repos
        register(YipeeRoom.class, yipeeRoomRepository);
        register(YipeeTable.class, yipeeTableRepository);
        register(YipeeSeat.class, yipeeSeatRepository);
        register(YipeePlayer.class, yipeePlayerRepository);

        // Only include DTOs here if they really are JPA entities extending YipeeObject.
        // If PlayerConnectionDTO is a real @Entity extending YipeeObject, keep it:
        register(PlayerConnectionDTO.class, yipeeClientConnectionRepository);
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
        logger.trace("Enter getObjectByName()={}, {}", clazz, name);
        if (clazz == null || name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Class and name must not be null or empty.");
        }

        YipeeRepository<T, String> repository = getRepository(clazz);
        logger.trace("repository()={}", repository.getClass().getSimpleName());
        Optional<T> objectByName = Optional.ofNullable(repository.findByName(name));
        logger.trace("exit getObjectByName()={}", objectByName);
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
        logger.debug("enter internalSave({})", object);
        if (object == null) {
            throw new IllegalArgumentException("Cannot save a null object.");
        }

        JpaRepository<T, String> repository = getRepository((Class<T>) object.getClass());
        repository.save(object);
        logger.debug("exit internalSave()");
    }

    @SuppressWarnings("unchecked")
    private <T extends YipeeObject> boolean internalDelete(T object) {
        logger.debug("enter internalDelete({})", object);
        if (object == null) {
            throw new IllegalArgumentException("Cannot delete a null object.");
        }

        JpaRepository<T, String> repository = getRepository((Class<T>) object.getClass());
        repository.delete(object);
        logger.debug("exit internalDelete()");
        return true;
    }

    private void saveOrUpdateObject(YipeeObject object) {
        logger.debug("saveOrUpdateObject object={}", object);
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
        logger.debug("exit saveOrUpdateObject()");
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
        logger.debug("enter saveObject({})", object);
        // Save parent object first
        saveOrUpdateObject(object);

        // Attempt to save child objects
        if (object instanceof YipeeObjectJPAVisitor visitor) {
            visitor.visitSave(this);
        }
        logger.debug("exit saveObject()");
    }

    @Override
    @Transactional
    public boolean deleteObject(YipeeObject object) {
        logger.debug("enter internalDelete({})", object);
        boolean successful = true;
        try {
            // Attempt to delete child objects first
            if(object instanceof TerminatorJPAVisitor visitor) {
                visitor.visitDelete(this);
            }
        } catch (Exception e) {
            logger.error("There was an exception while deleting child objects for object: {}", object, e);
        }

        try {
            // Attempt to delete child objects first
            successful = internalDelete(object);
        } catch (Exception e) {
            logger.error("There was an exception while deleting object: {}", object, e);
            successful = false;
        }
        logger.debug("exit internalDelete()={}", successful);
        return successful;
    }

    @Override
    public void commitTransactions() {

    }

    @Override
    public void rollBackTransactions() {

    }

    // ---------- Visitor callbacks ----------

    @Override
    public void visitSaveYipeeRoom(YipeeRoom room) {
        if(room != null) {
            for(YipeePlayer player : Util.safeIterable(room.getPlayers())){
                saveOrUpdateObject(player);
            }
        }
    }

    @Override
    public void visitSaveYipeeTable(YipeeTable table) {
        if(table != null) {
            table.setTableName(table.getTableNumber());
            saveOrUpdateObject(table);
            for(YipeeSeat seat : Util.safeIterable(table.getSeats())){
                saveOrUpdateObject(seat);
            }

            for(YipeePlayer watcher : Util.safeIterable(table.getWatchers())){
                saveOrUpdateObject(watcher);
            }
        }
    }

    @Override
    public void visitTerminateYipeeRoom(YipeeRoom room) {
        if(room != null) {
            for(YipeePlayer player : Util.safeIterable(room.getPlayers())){
                internalDelete(player);
            }
        }
    }

    @Override
    public void visitTerminateYipeeTable(YipeeTable table) {
        if(table != null) {
            for(YipeeSeat seat : Util.safeIterable(table.getSeats())){
                internalDelete(seat);
            }

            for(YipeePlayer watcher : Util.safeIterable(table.getWatchers())){
                internalDelete(watcher);
            }
        }
    }
}
