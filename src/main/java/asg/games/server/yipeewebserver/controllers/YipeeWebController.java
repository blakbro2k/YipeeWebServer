package asg.games.server.yipeewebserver.controllers;

import asg.games.server.yipeewebserver.data.WebPlayerDTO;
import asg.games.server.yipeewebserver.data.WebTableDTO;
import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.common.enums.Constants;
import asg.games.yipee.core.objects.YipeePlayer;
import asg.games.yipee.core.objects.YipeeRoom;
import asg.games.yipee.core.objects.YipeeTable;
import asg.games.yipee.core.tools.Util;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
public class YipeeWebController {
    @Autowired
    private YipeeGameJPAServiceImpl yipeeGameService;

    private final Map<String, List<YipeeRoom>> roomMap;
    private boolean runOnce = false;

    public YipeeWebController(){
        roomMap = new HashMap<>();
    }

    private void buildRoomMap (){
        if(!runOnce) {
            runOnce = true;
            List<YipeeRoom> rooms = yipeeGameService.getAllObjects(YipeeRoom.class);

            if(!Util.isEmpty(rooms)) {
                for(YipeeRoom room : Util.safeIterable(rooms)) {
                    if(room != null) {
                        String loungeName = room.getLoungeName();
                        List<YipeeRoom> listOfRooms = roomMap.get(loungeName);
                        if(listOfRooms == null) {
                            listOfRooms = new ArrayList<>();
                        }
                        listOfRooms.add(room);
                        roomMap.put(loungeName, listOfRooms);
                    }
                }
            }
        }
    }

    private void addRoomsToModel(Model model) {
        buildRoomMap();
        model.addAttribute("allSocialRooms", roomMap.get(YipeeRoom.SOCIAL_LOUNGE));
        model.addAttribute("allBeginnerRooms", roomMap.get(YipeeRoom.BEGINNER_LOUNGE));
        model.addAttribute("allIntermediateRooms", roomMap.get(YipeeRoom.INTERMEDIATE_LOUNGE));
        model.addAttribute("allAdvancedRooms", roomMap.get(YipeeRoom.ADVANCED_LOUNGE));
    }

    @GetMapping()
    public String index(Model model) {
        model.addAttribute("index", "index");
        return "redirect:/home";
    }

    @GetMapping("/home")
    public String home(Model model) {
        addRoomsToModel(model);
        return "home";
    }

    @GetMapping("/createPlayer")
    public String createPlayer(Model model) {
        model.addAttribute("webPlayerDTO", new WebPlayerDTO());
        return "createPlayer";
    }

    @PostMapping("/savePlayer")
    public String savePlayer(@ModelAttribute WebPlayerDTO webPlayerDTO) {
        System.out.println(webPlayerDTO);

        if(webPlayerDTO != null) {
            YipeePlayer dbPlayer = new YipeePlayer();
            dbPlayer.setName(webPlayerDTO.getUsername());
            //dbPlayer.setIcon(webPlayer.getIcon());
            //dbPlayer.setRating(webPlayer.getRating());

            log.trace("Creating: " + dbPlayer);
            yipeeGameService.saveObject(dbPlayer);
        }

        return "home";
    }

    @GetMapping("/room/{id}")
    public String room(@PathVariable(value = "id") String id, Model model) {
        YipeeRoom room = yipeeGameService.getObjectById(YipeeRoom.class, id);
        if(room != null) {
            model.addAttribute("roomTitle", room.getName());
            model.addAttribute("roomId", id);
            log.trace("room model: " + model);
        }
        return "room";
    }

    @RequestMapping(value="/#",params="playNew",method=RequestMethod.POST)
    public void playNew() {
        System.out.println("Action1 block called");
    }

    @GetMapping(value = "/createTable")
    public String createTable(@RequestParam(name = "roomId", required = false) String roomId, Model model) {
        List<String> types = Arrays.asList(Constants.ENUM_VALUE_PUBLIC, Constants.ENUM_VALUE_PROTECTED, Constants.ENUM_VALUE_PRIVATE);
        model.addAttribute("webTableDTO", new WebTableDTO());
        model.addAttribute("tableTypes", types);
        model.addAttribute("roomId", roomId);
        return "createTable";
    }

    @PostMapping("/doCreateTable")
    public String doCreateTable(@RequestParam(name = "roomId", required = false) String roomId, @ModelAttribute WebTableDTO webTableDTO, Model model) {
        if(webTableDTO != null) {
            log.trace("create table model: " + model);
            log.trace("create table roomId: " + roomId);
            boolean isRated = webTableDTO.getRated();
            boolean isSoundOn = webTableDTO.getSound();
            String accessType = webTableDTO.getAccessType();
            log.trace("isRated: " + isRated);
            log.trace("isSoundOn: " + isSoundOn);
            log.trace("accessType: " + accessType);

            YipeeRoom dbRoom = yipeeGameService.getObjectById(YipeeRoom.class, roomId);
            log.trace("dbRoom: " + dbRoom);

            Map<String, Object> arguments = new HashMap<>();
            arguments.put(YipeeTable.ARG_TYPE, accessType);
            arguments.put(YipeeTable.ARG_RATED, isRated);
            //arguments.put(YipeeTable.ARG_SOUND, isSoundOn);
            YipeeTable dbTable = new YipeeTable( 2, arguments);
            dbTable.setSoundOn(isSoundOn);
            log.trace("Creating: " + dbTable.getSeats());

            yipeeGameService.saveObject(dbTable);
        }
        return "game";
    }
}
