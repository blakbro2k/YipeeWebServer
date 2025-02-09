package asg.games.server.yipeewebserver.controllers;


import asg.games.server.yipeewebserver.services.impl.YipeeGameJPAServiceImpl;
import asg.games.yipee.objects.YipeeRoom;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Slf4j
@Controller
public class YipeeGameController {
    @Autowired
    private YipeeGameJPAServiceImpl yipeeGameService;

    public YipeeGameController(){
    }

    @GetMapping("/game/{id}")
    public String launchGame(@PathVariable(value = "id") String id, Model model) {
        YipeeRoom room = yipeeGameService.getObjectById(YipeeRoom.class, id);
        if(room != null) {
            model.addAttribute("roomTitle", room.getName());
        }
        return "room";
    }
}

