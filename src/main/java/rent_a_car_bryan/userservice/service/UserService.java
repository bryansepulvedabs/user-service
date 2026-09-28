package rent_a_car_bryan.userservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import rent_a_car_bryan.userservice.dto.LoginRequestDTO;
import rent_a_car_bryan.userservice.dto.LoginResponseDTO;
import rent_a_car_bryan.userservice.dto.UserRequestDTO;
import rent_a_car_bryan.userservice.dto.UserResponseDTO;
import rent_a_car_bryan.userservice.entity.UserEntity;
import rent_a_car_bryan.userservice.exception.InvalidCredentialsException;
import rent_a_car_bryan.userservice.exception.ResourceNotFoundException;
import rent_a_car_bryan.userservice.repository.UserRepository;
import rent_a_car_bryan.userservice.security.JwtService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public List<UserResponseDTO> findAll(){
        return userRepository.findAllByOrderByIdAsc()
                .stream()
                .map(this::toResponseDTO)
                .toList();
    }

    public UserResponseDTO findById(Long id){
        UserEntity user = findEntityById(id);
        return toResponseDTO(user);
    }

    public UserResponseDTO findByRut(String rut){
        UserEntity user = userRepository.findByRut(rut)
                .orElseThrow(() -> new ResourceNotFoundException( "Usuario no encontrado con rut : " + rut));
        return toResponseDTO(user);
    }

    public UserResponseDTO save(UserRequestDTO userRequestDTO){
        UserEntity user = toEntity(userRequestDTO);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        UserEntity savedUser = userRepository.save(user);
        return toResponseDTO(savedUser);
    }

    public UserResponseDTO update(Long id, UserRequestDTO userRequestDTO){
        UserEntity user = findEntityById(id);

        user.setRut(userRequestDTO.getRut());
        user.setFirstName(userRequestDTO.getFirstName());
        user.setLastName(userRequestDTO.getLastName());
        user.setEmail(userRequestDTO.getEmail());
        // Solo se cambia la contraseña si viene una nueva; y siempre se hashea
        if (userRequestDTO.getPassword() != null && !userRequestDTO.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(userRequestDTO.getPassword()));
        }
        user.setPhone(userRequestDTO.getPhone());
        user.setAddress(userRequestDTO.getAddress());
        user.setCity(userRequestDTO.getCity());
        user.setCountry(userRequestDTO.getCountry());
        user.setRole(userRequestDTO.getRole());

        UserEntity updatedUser = userRepository.save(user);
        return toResponseDTO(updatedUser);
    }

    public void deleteById(Long id){
        UserEntity user = findEntityById(id);
        userRepository.deleteById(user.getId());
    }


    private UserEntity findEntityById(Long id){
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException( "Usuario no encontrado con id : " + id));
    }

    private UserEntity toEntity(UserRequestDTO dto){
        UserEntity user = new UserEntity();
        user.setRut(dto.getRut());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPassword(dto.getPassword());
        user.setPhone(dto.getPhone());
        user.setAddress(dto.getAddress());
        user.setCity(dto.getCity());
        user.setCountry(dto.getCountry());
        return user;
    }

    private UserResponseDTO toResponseDTO(UserEntity user){
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(user.getId());
        dto.setRut(user.getRut());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAddress(user.getAddress());
        dto.setCity(user.getCity());
        dto.setCountry(user.getCountry());
        dto.setRole(user.getRole());
        return dto;
    }

    public LoginResponseDTO login(LoginRequestDTO loginRequestDTO) {
        UserEntity user = userRepository.findByEmail(loginRequestDTO.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Correo o contraseña incorrectos"));

        if (!passwordEncoder.matches(loginRequestDTO.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Correo o contraseña incorrectos");
        }

        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(jwtService.generateToken(user));
        response.setId(user.getId());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setEmail(user.getEmail());
        response.setRole(user.getRole());
        return response;
    }

}