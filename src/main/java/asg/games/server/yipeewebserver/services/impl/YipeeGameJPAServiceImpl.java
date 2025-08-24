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
import asg.games.yipee.core.persistence.YipeeObjectJPAVisitor;
import asg.games.yipee.core.tools.TimeUtils;
import asg.games.yipee.core.tools.Util;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Transient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class YipeeGameJPAServiceImpl extends AbstractStorage implements YipeeGameServices, YipeeTerminateServices {
    @Transient
    private static final Logger logger = LoggerFactory.getLogger(YipeeGameJPAServiceImpl.class);
    private final Map<Class<?>, JpaRepository<?, String>> repoMap = new HashMap<>();

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

    @Override
    public void dispose() {
        //Spring takes care of destroying repository
    }

    @PostConstruct
    public void init() {
        repoMap.put(YipeeRoom.class, yipeeRoomRepository);
        repoMap.put(YipeeTable.class, yipeeTableRepository);
        repoMap.put(YipeeSeat.class, yipeeSeatRepository);
        repoMap.put(YipeePlayer.class, yipeePlayerRepository);
        repoMap.put(PlayerConnectionDTO.class, yipeeClientConnectionRepository);
    }

    @SuppressWarnings("unchecked")
    private <T extends YipeeObject> YipeeRepository<T, String> getRepository(Class<T> clazz) {
        YipeeRepository<?, String> repository = (YipeeRepository<?, String>) repoMap.get(clazz);
        if (repository == null) {
            throw new IllegalStateException("No repository found for class: " + clazz.getSimpleName());
        }
        return (YipeeRepository<T, String>) repository;
    }

    @Override
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
    public <T extends YipeeObject> T getObjectById(Class<T> clazz, String id)  {
        JpaRepository<T, String> repository = getRepository(clazz);
        return repository.findById(id).orElse(null);
    }

    @Override
    public <T extends YipeeObject> List<T> getAllObjects(Class<T> clazz) {
        JpaRepository<T, String> repository = getRepository(clazz);
        return repository.findAll();
    }

    @Override
    public <T extends YipeeObject> int countAllObjects(Class<T> clazz) {
        JpaRepository<T, String> repository = getRepository(clazz);
        return Util.otoi(repository.count());
    }

    @SuppressWarnings("unchecked")
    private <T extends YipeeObject> void internalSave(T object) {
        logger.debug("enter internalSave({})", object);
        if (object == null) {
            throw new IllegalArgumentException("Cannot save a null object.");
        }

        JpaRepository<T, String> repository = getRepository((Class<T>) object.getClass());

        logger.debug("exit internalSave()");
        repository.save(object);
    }

    @SuppressWarnings("unchecked")
    private <T extends YipeeObject> boolean internalDelete(T object) {
        logger.debug("enter internalDelete({})", object);
        if (object == null) {
            throw new IllegalArgumentException("Cannot save a null object.");
        }

        JpaRepository<T, String> repository = getRepository((Class<T>) object.getClass());

        repository.delete(object);
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
            logger.debug("Found existing object: {}", existing);
            // Copy ID to update same record
            object.setId(existing.getId());
            object.setModified(TimeUtils.millis());
        } else {
            // Let DB generate ID naturally (leave it null)
            object.setCreated(TimeUtils.millis());
            object.setModified(TimeUtils.millis());
        }
        internalSave(object);
        logger.debug("exit saveOrUpdateObject()");
    }

    @Override
    public void saveObject(YipeeObject object) {
        logger.debug("enter saveObject()");
        // Save parent object first
        saveOrUpdateObject(object);

        // Attempt to save child objects
        if (object instanceof YipeeObjectJPAVisitor) {
            ((YipeeObjectJPAVisitor) object).visitSave(this);
        }
        logger.debug("exit saveObject()");
    }

    @Override
    public boolean deleteObject(YipeeObject object) {
        logger.debug("enter internalDelete()");
        boolean successful = true;
        try {
            // Attempt to delete child objects first
            if(object instanceof TerminatorJPAVisitor) {
                ((TerminatorJPAVisitor) object).visitDelete(this);
            }
        } catch (Exception e) {
            logger.error("There was an exception while deleting child objects for object: {}", object, e);
            successful = false;
        }

        try {
            // Attempt to delete child objects first
            successful = internalDelete(object);
        } catch (Exception e) {
            logger.error("There was an exception while deleting object: {}", object, e);
            successful = false;
        }
        logger.debug("exit internalDelete()");
        return successful;
    }

    @Override
    public void commitTransactions() {

    }

    @Override
    public void rollBackTransactions() {

    }

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
