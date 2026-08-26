package rent_a_car_bryan.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import rent_a_car_bryan.userservice.entity.UserEntity;
import rent_a_car_bryan.userservice.repository.UserRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public List<UserEntity> findAll(){
        return userRepository.findAll();
    }

    public UserEntity findById(Long id){
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException( "Usuario no encontrado con id : " + id));
    }

    public UserEntity findByRut(String rut){
        return userRepository.findByRut(rut)
                .orElseThrow(() -> new RuntimeException( "Usuario no encontrado con rut : " + rut));
    }

    public UserEntity save(UserEntity user){
        return userRepository.save(user);
    }


}
