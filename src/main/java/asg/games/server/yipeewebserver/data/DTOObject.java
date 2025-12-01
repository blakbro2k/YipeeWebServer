package asg.games.server.yipeewebserver.data;

/**
 * The {@code DTOObject} interface defines a standard structure for Data Transfer Objects (DTOs)
 * in the Yipee Webserver project. It enforces common properties
 * such as ID, name, creation timestamp, and modification timestamp.
 */
public interface DTOObject {

    /**
     * Sets the unique identifier for this object.
     *
     * @param id the unique identifier to set
     */
    void setId(String id);

    /**
     * Retrieves the unique identifier of this object.
     *
     * @return the unique identifier
     */
    String getId();

    /**
     * Sets the name of this object.
     *
     * @param name the name to set
     */
    void setName(String name);

    /**
     * Retrieves the name of this object.
     *
     * @return the name of the object
     */
    String getName();

    /**
     * Sets the timestamp representing when this object was created.
     *
     * @param created the creation timestamp in milliseconds
     */
    void setCreated(long created);

    /**
     * Retrieves the creation timestamp of this object.
     *
     * @return the creation timestamp in milliseconds
     */
    long getCreated();

    /**
     * Sets the timestamp representing when this object was last modified.
     *
     * @param modified the modification timestamp in milliseconds
     */
    void setModified(long modified);

    /**
     * Retrieves the last modification timestamp of this object.
     *
     * @return the last modification timestamp in milliseconds
     */
    long getModified();
}