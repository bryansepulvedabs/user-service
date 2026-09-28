package rent_a_car_bryan.userservice.dto;

import lombok.Data;
import rent_a_car_bryan.userservice.entity.EnumRole;

@Data
public class LoginResponseDTO {
    private String token;
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private EnumRole role;
}