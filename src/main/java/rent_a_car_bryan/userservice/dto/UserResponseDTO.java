package rent_a_car_bryan.userservice.dto;

import lombok.Data;
import rent_a_car_bryan.userservice.entity.EnumRole;

@Data
public class UserResponseDTO {
    private Long id;
    private String rut;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String country;
    private EnumRole role;
    private Boolean deleted;
}
