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
import asg.games.yipee.objects.YipeeObject;
import asg.games.yipee.objects.YipeePlayer;
import asg.games.yipee.objects.YipeeRoom;
import asg.games.yipee.objects.YipeeSeat;
import asg.games.yipee.objects.YipeeTable;
import asg.games.yipee.persistence.AbstractStorage;
import asg.games.yipee.persistence.TerminatorJPAVisitor;
import asg.games.yipee.persistence.YipeeObjectJPAVisitor;
import asg.games.yipee.tools.TimeUtils;
import asg.games.yipee.tools.Util;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.Transient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
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

    private <T extends YipeeObject> YipeeRepository<T, String> getRepository(Class<T> clazz) {
        return (YipeeRepository<T, String>) repoMap.get(clazz);
    }

    @Override
    public <T extends YipeeObject> T getObjectByName(Class<T> clazz, String name) {
        logger.trace("Enter getObjectByName()={}, {}", clazz, name);
        if (clazz == null || name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Class and name must not be null or empty.");
        }

        YipeeRepository<T, String> repository = getRepository(clazz);
        if (repository == null) {
            throw new IllegalStateException("No repository found for class: " + clazz.getSimpleName());
        }
        logger.trace("repository()={}", repository.getClass().getSimpleName());
        Optional<T> objectByName = Optional.ofNullable(repository.findByName(name));
        logger.trace("exit getObjectByName()={}", objectByName);
        return objectByName.orElse(null);
    }

    @Override
    public <T extends YipeeObject> T getObjectById(Class<T> clazz, String id)  {
        JpaRepository<T, String> repository = getRepository(clazz);
        if (repository != null) {
            return repository.findById(id).orElse(null);
        }
        return null;
    }

    @Override
    public <T extends YipeeObject> List<T> getAllObjects(Class<T> clazz) {
        JpaRepository<T, String> repository = getRepository(clazz);
        return (repository != null) ? repository.findAll() : Collections.emptyList();
    }

    @Override
    public <T extends YipeeObject> int countAllObjects(Class<T> clazz) {
        JpaRepository<T, String> repository = getRepository(clazz);
        return (repository != null) ? Util.otoi(repository.count()) : 0;
    }

    private <T extends YipeeObject> T internalSave(T object) {
        logger.debug("enter internalSave({})", object);
        if (object == null) {
            throw new IllegalArgumentException("Cannot save a null object.");
        }

        JpaRepository<T, String> repository = getRepository((Class<T>) object.getClass());
        if (repository == null) {
            throw new IllegalStateException("No repository found for class: " + object.getClass().getSimpleName());
        }

        logger.debug("exit internalSave()");
        return repository.save(object);
    }

    private <T extends YipeeObject> boolean internalDelete(T object) {
        logger.debug("enter internalDelete({})", object);
        if (object == null) {
            throw new IllegalArgumentException("Cannot save a null object.");
        }

        JpaRepository<T, String> repository = getRepository((Class<T>) object.getClass());
        if (repository == null) {
            throw new IllegalStateException("No repository found for class: " + object.getClass().getSimpleName());
        }

        return internalDelete(object);
    }

    private void saveOrUpdateObject(YipeeObject object) {
        logger.debug("saveOrUpdateObject object={}, full", object);
        if(object != null) {
            YipeeObject obj = getObjectByName(object.getClass(), object.getName());
            if(obj != null) {
                logger.debug("Found obj: {}, setting id and modified date", obj);
                object.setId(obj.getId());
                object.setModified(TimeUtils.millis());
                logger.debug("obj: {}", obj);
                logger.debug("object: {}", object);
            }
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
            logger.error("There was an exception while deleting child objects for object: {}", object);
            successful = false;
        }

        try {
            // Attempt to delete child objects first
            successful = internalDelete(object);
        } catch (Exception e) {
            logger.error("There was an exception while deleting object: {}", object);
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
