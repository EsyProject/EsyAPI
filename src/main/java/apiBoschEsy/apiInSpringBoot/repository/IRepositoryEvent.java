package apiBoschEsy.apiInSpringBoot.repository;

import apiBoschEsy.apiInSpringBoot.entity.Assessment;
import apiBoschEsy.apiInSpringBoot.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IRepositoryEvent extends JpaRepository<Event, Long> {
    Page<Event> findAll(Pageable pageable);
    Optional<Event> findEventByNameOfEvent(String nameOfEvent);
}
