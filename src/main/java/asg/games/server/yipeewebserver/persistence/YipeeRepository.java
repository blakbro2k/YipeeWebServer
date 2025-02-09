package asg.games.server.yipeewebserver.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface YipeeRepository<T, ID> extends JpaRepository<T, ID> {
    T findByName(ID name);
}