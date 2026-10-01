package rent_a_car_bryan.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import rent_a_car_bryan.userservice.entity.EnumRole;

@Data
public class UserRequestDTO {

    // Solo formato (12345678-9, 12.345.678-9, 1234567K...). No se valida el digito
    // verificador a proposito: hay datos de prueba con RUT de digito incorrecto y
    // quedarian sin poder editarse.
    @NotBlank(message = "El RUT es obligatorio")
    @Pattern(regexp = "^\\d{1,2}\\.?\\d{3}\\.?\\d{3}-?[\\dkK]$", message = "El RUT no tiene un formato válido")
    private String rut;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre es demasiado largo")
    private String firstName;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100, message = "El apellido es demasiado largo")
    private String lastName;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El correo no es válido")
    private String email;

    // Obligatoria solo al crear (grupo OnCreate). Al editar puede venir nula o vacia y se
    // conserva la actual (ver UserService.update); si trae texto, debe tener 6 a 100 caracteres.
    // Por eso se usa @Pattern y no @Size: @Size rechazaria el string vacio que manda el formulario.
    @NotBlank(message = "La contraseña es obligatoria", groups = OnCreate.class)
    @Pattern(regexp = "^(|.{6,100})$", message = "La contraseña debe tener entre 6 y 100 caracteres")
    private String password;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^[+0-9 ()-]{8,20}$", message = "El teléfono no es válido")
    private String phone;

    @NotBlank(message = "La dirección es obligatoria")
    private String address;

    @NotBlank(message = "La ciudad es obligatoria")
    private String city;

    @NotBlank(message = "El país es obligatorio")
    private String country;

    // Opcional: lo decide el servidor (ver UserService.resolveRoleForNewUser)
    private EnumRole role;
}