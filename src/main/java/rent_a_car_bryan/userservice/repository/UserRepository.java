package rent_a_car_bryan.userservice.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rent_a_car_bryan.userservice.entity.UserEntity;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long>{

    Optional<UserEntity> findByRut(String rut);

    Optional<UserEntity> findByEmail(String email);

    List<UserEntity> findAllByOrderByIdAsc();
}
