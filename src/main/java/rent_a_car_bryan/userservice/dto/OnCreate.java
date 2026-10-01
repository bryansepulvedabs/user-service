package rent_a_car_bryan.userservice.dto;

import jakarta.validation.groups.Default;

// Grupo de validacion para exigir campos SOLO al crear un usuario (ej. la contrasena).
// Al editar, la contrasena es opcional: si no viene, se conserva la actual.
// Extiende Default para que, al crear, tambien se validen las reglas normales.
public interface OnCreate extends Default {
}