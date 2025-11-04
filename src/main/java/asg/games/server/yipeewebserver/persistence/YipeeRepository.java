package asg.games.server.yipeewebserver.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

@NoRepositoryBean
public interface YipeeRepository<T, ID> extends JpaRepository<T, ID> {
    T findByName(ID name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from #{#entityName} e where e.name = :name")
    T findByNameForUpdate(@Param("name") String name);
}