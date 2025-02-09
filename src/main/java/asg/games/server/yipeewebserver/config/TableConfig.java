package asg.games.server.yipeewebserver.config;

import asg.games.server.yipeewebserver.YipeeWebserverApplication;
import asg.games.server.yipeewebserver.data.RoomConfigDTO;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.objects.YipeeRoom;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;
import org.springframework.core.io.Resource;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

@Configuration
@ImportResource("classpath:rooms.xml")
public class TableConfig {
    private static final Logger logger = LoggerFactory.getLogger(YipeeWebserverApplication.class);

    @Autowired
    YipeeGameJPAServiceImpl yokelGameServiceImpl;

    //private @Value("#{Lounge}") Object rooms;
    @Value("classpath:rooms.xml")
    private Resource resource;

    public TableConfig()  {
    }

    @Bean
    public boolean loadRooms() throws IOException {
        logger.trace("start loadRooms()");
        boolean success = false;
        try {
            File file = resource.getFile();
            XmlMapper xmlMapper = new XmlMapper();
            String xml = inputStreamToString(new FileInputStream(file));
            RoomConfigDTO value = xmlMapper.readValue(xml, RoomConfigDTO.class);
            createDatabaseLounges(value);
            success = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.trace("exit loadRooms()={}", success);
        return success;
    }

    public String inputStreamToString(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        BufferedReader br = new BufferedReader(new InputStreamReader(is));
        while ((line = br.readLine()) != null) {
            sb.append(line);
        }
        br.close();
        return sb.toString();
    }

    public void createDatabaseLounges(RoomConfigDTO roomConfig) {
        logger.trace("start createDatabaseLounges()");

        if (roomConfig != null) {
            RoomConfigDTO.LoungeDTO[] dto = roomConfig.getLoungeDTO();

            if (dto != null) {
                for (RoomConfigDTO.LoungeDTO loungeDTO : dto) {
                    if (loungeDTO != null) {
                        if (!loungeDTO.getDisabled()) {
                            String loungeName = loungeDTO.getName();
                            RoomConfigDTO.RoomDTO[] roomDtos = loungeDTO.getRoomDTO();

                            if (roomDtos != null) {
                                for (RoomConfigDTO.RoomDTO roomDTO : roomDtos) {
                                    if (roomDTO != null) {
                                        if (!roomDTO.getDisabled()) {
                                            String roomName = roomDTO.getName();

                                            if(yokelGameServiceImpl.getObjectByName(YipeeRoom.class, roomName) == null) {
                                                yokelGameServiceImpl.saveObject(new YipeeRoom(roomName, loungeName));
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        logger.trace("exit createDatabaseLounges()");
    }
}