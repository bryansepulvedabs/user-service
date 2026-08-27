package rent_a_car_bryan.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import rent_a_car_bryan.userservice.dto.UserRequestDTO;
import rent_a_car_bryan.userservice.dto.UserResponseDTO;
import rent_a_car_bryan.userservice.entity.UserEntity;
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

    @GetMapping("/{id}")
    public UserResponseDTO findById(@PathVariable Long id){
        return userService.findById(id);
    }

    @GetMapping("/rut/{rut}")
    public UserResponseDTO findByRut(@PathVariable String rut){
        return userService.findByRut(rut);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponseDTO create(@RequestBody UserRequestDTO user){
        return userService.save(user);
    }git

    @PutMapping("/{id}")
    public UserResponseDTO update(@PathVariable Long id, @RequestBody UserRequestDTO userUpdate){
        return userService.update(id,userUpdate);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id){
        userService.deleteById(id);
    }

}
