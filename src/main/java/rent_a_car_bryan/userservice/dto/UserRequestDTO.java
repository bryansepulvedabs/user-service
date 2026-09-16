package rent_a_car_bryan.userservice.dto;

import lombok.Data;
import rent_a_car_bryan.userservice.entity.EnumRole;

@Data
public class UserRequestDTO {
    private String rut;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phone;
    private String address;
    private String city;
    private String country;
    private EnumRole role;
}
