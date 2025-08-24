package asg.games.server.yipeewebserver.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlText;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

@JacksonXmlRootElement(localName = "RoomConfigDTO")
public class RoomConfigDTO {
    private static final Logger logger = LoggerFactory.getLogger(RoomConfigDTO.class);

    @JacksonXmlElementWrapper(localName = "Lounge", useWrapping = false)
    @JacksonXmlProperty(localName = "Lounge")
    private LoungeDTO[] Lounge;

    public RoomConfigDTO() {
    }

    public RoomConfigDTO(LoungeDTO... lounges) {
        if (lounges != null) {
            this.Lounge = lounges;
        }
    }

    public LoungeDTO[] getLoungeDTO() {
        return Lounge;
    }

    public void setLoungeDTO(LoungeDTO[] lounges) {
        this.Lounge = lounges;
    }

    public String toString() {
        return "RoomConfigDTO{" +
                prettyPrintDTO(Lounge) +
                '}';
    }

    private String prettyPrintDTO(LoungeDTO[] dtos) {
        StringBuilder sb = new StringBuilder();

        for(LoungeDTO dto : dtos) {
            if(dto != null) {
                sb.append(dto.getName()).append(Arrays.toString(dto.getRoomDTO()));
            }
        }
        return sb.toString();
    }

    @Setter
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RoomDTO {
        @JacksonXmlText
        private String name;

        @JacksonXmlProperty(localName = "disabled", isAttribute = true)
        private boolean disabled;

        public RoomDTO() {
        }

        public RoomDTO(String roomName) {
            this(roomName, false);
        }

        public RoomDTO(String roomName, boolean isRoomDisabled) {
            setDisabled(isRoomDisabled);
            setName(roomName);
        }

        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }

        public boolean getDisabled() {
            return disabled;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String toString() {
            return "{" + name + ":disabled=" + disabled + "}";
        }
    }

    @Setter
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LoungeDTO {
        @JacksonXmlElementWrapper(localName = "RoomName", useWrapping = false)
        @JacksonXmlProperty()
        private RoomDTO[] RoomName;


        @JacksonXmlProperty(localName = "name", isAttribute = true)
        private String name;

        @JacksonXmlProperty(localName = "disabled", isAttribute = true)
        boolean disabled;

        public LoungeDTO() {
        }

        public LoungeDTO(String name, RoomDTO... rooms) {
            this(name, false, rooms);
        }

        public LoungeDTO(String name, boolean isDisabled, RoomDTO... rooms) {
            if (rooms != null) {
                this.RoomName = rooms;
            }
            this.name = name;
            this.disabled = isDisabled;
        }

        public RoomDTO[] getRoomDTO() {
            return RoomName;
        }

        public void setRoomDTO(RoomDTO[] rooms) {
            this.RoomName = rooms;
        }

        public boolean getDisabled() {
            return disabled;
        }

        public String toString() {
            return Arrays.toString(RoomName);
        }

        private String prettyPrintDTO(RoomDTO[] dtos) {
            StringBuilder sb = new StringBuilder();

            for(RoomDTO dto : dtos) {
                if(dto != null) {
                    sb.append(dto.getName()).append("=[").append(Arrays.toString(RoomName)).append("]");
                }
            }
            return sb.toString();
        }
    }
}