package rent_a_car_bryan.userservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import rent_a_car_bryan.userservice.dto.ChangePasswordRequestDTO;
import rent_a_car_bryan.userservice.dto.OnCreate;
import rent_a_car_bryan.userservice.dto.ProfileUpdateRequestDTO;
import rent_a_car_bryan.userservice.dto.UserRequestDTO;
import rent_a_car_bryan.userservice.dto.UserResponseDTO;
import rent_a_car_bryan.userservice.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserResponseDTO> findAll(){
        return userService.findAll();
    }

    // Usuarios dados de baja: solo ADMIN, ver SecurityConfig
    @GetMapping("/deleted")
    public List<UserResponseDTO> findAllDeleted(){
        return userService.findAllDeleted();
    }

    // ---- Cuenta propia: cualquier usuario con sesion (ver SecurityConfig) ----
    @GetMapping("/me")
    public UserResponseDTO me(){
        return userService.findMe();
    }

    @PutMapping("/me")
    public UserResponseDTO updateMe(@Valid @RequestBody ProfileUpdateRequestDTO dto){
        return userService.updateMe(dto);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequestDTO dto){
        userService.changePassword(dto);
    }

    @GetMapping("/{id}")
    public UserResponseDTO findById(@PathVariable Long id){
        return userService.findById(id);
    }

    // Ficha incluyendo eliminados, para revisar su historial. ADMIN, o rental-service
    // (rol SERVICE) al armar el historial de un arriendo: ver SecurityConfig.
    @GetMapping("/admin/{id}")
    public UserResponseDTO findByIdIncludingDeleted(@PathVariable Long id){
        return userService.findByIdIncludingDeleted(id);
    }

    @GetMapping("/rut/{rut}")
    public UserResponseDTO findByRut(@PathVariable String rut){
        return userService.findByRut(rut);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO create(@Validated(OnCreate.class) @RequestBody UserRequestDTO user){
        return userService.save(user);
    }

    @PutMapping("/{id}")
    public UserResponseDTO update(@PathVariable Long id, @Valid @RequestBody UserRequestDTO userUpdate){
        return userService.update(id,userUpdate);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id){
        userService.deleteById(id);
    }

    // Reactivar un usuario dado de baja: solo ADMIN
    @PatchMapping("/{id}/restore")
    public UserResponseDTO restore(@PathVariable Long id){
        return userService.restore(id);
    }

}