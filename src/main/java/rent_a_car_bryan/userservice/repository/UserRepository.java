package rent_a_car_bryan.userservice.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rent_a_car_bryan.userservice.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long>{

    Optional<UserEntity> findByRut(String rut);

    Optional<UserEntity> findByEmail(String email);

    List<UserEntity> findAllByOrderByIdAsc();

    // nativeQuery salta @SQLRestriction: es la unica forma de ver a los borrados,
    // porque cualquier consulta JPA sobre UserEntity los oculta.
    @Query(value = "SELECT * FROM users WHERE deleted = true ORDER BY id", nativeQuery = true)
    List<UserEntity> findAllDeleted();

    @Query(value = "SELECT * FROM users WHERE id = :id AND deleted = true", nativeQuery = true)
    Optional<UserEntity> findDeletedById(Long id);

    // El UPDATE se hace por SQL: userRepository.save() no serviria, porque para
    // llamarlo antes hay que cargar la entidad, y las consultas JPA no la ven.
    @Modifying
    @Query(value = "UPDATE users SET deleted = false WHERE id = :id AND deleted = true", nativeQuery = true)
    int restoreById(Long id);
}