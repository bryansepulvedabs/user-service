package rent_a_car_bryan.userservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// Cuerpo de PUT /api/users/me/password
@Data
public class ChangePasswordRequestDTO {

    @NotBlank(message = "Debes indicar tu contraseña actual")
    private String currentPassword;

    @NotBlank(message = "Debes indicar la nueva contraseña")
    @Size(min = 6, max = 100, message = "La nueva contraseña debe tener entre 6 y 100 caracteres")
    private String newPassword;
}