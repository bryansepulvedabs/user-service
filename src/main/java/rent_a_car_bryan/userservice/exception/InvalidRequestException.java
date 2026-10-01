package rent_a_car_bryan.userservice.exception;

// Peticion invalida por una regla de negocio (ej. contraseña actual incorrecta). Sale como 400.
// Se usa 400 y no 401 a proposito: un 401 podria hacer que el frontend cierre la sesion
// por un simple error de tipeo al cambiar la contraseña.
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}