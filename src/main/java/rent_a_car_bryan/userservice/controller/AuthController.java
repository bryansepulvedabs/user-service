package rent_a_car_bryan.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import rent_a_car_bryan.userservice.dto.LoginRequestDTO;
import rent_a_car_bryan.userservice.dto.LoginResponseDTO;
import rent_a_car_bryan.userservice.service.UserService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @PostMapping("/login")
    public LoginResponseDTO login(@RequestBody LoginRequestDTO loginRequestDTO) {
        return userService.login(loginRequestDTO);
    }
}