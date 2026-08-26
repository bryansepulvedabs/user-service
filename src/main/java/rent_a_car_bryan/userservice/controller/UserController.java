package rent_a_car_bryan.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import rent_a_car_bryan.userservice.entity.UserEntity;
import rent_a_car_bryan.userservice.service.UserService;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public List<UserEntity> findAll(){
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public UserEntity findById(@PathVariable Long id){
        return userService.findById(id);
    }

    @GetMapping("/rut/{rut}")
    public UserEntity findByRut(@PathVariable String rut){
        return userService.findByRut(rut);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserEntity create(@RequestBody UserEntity user){
        return userService.save(user);
    }

    @PutMapping("/{id}")
    public UserEntity update(@PathVariable Long id, @RequestBody UserEntity user){
        return userService.update(id,user);
    }

}
