package rent_a_car_bryan.userservice.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import rent_a_car_bryan.userservice.dto.ChangePasswordRequestDTO;
import rent_a_car_bryan.userservice.dto.LoginRequestDTO;
import rent_a_car_bryan.userservice.dto.LoginResponseDTO;
import rent_a_car_bryan.userservice.dto.ProfileUpdateRequestDTO;
import rent_a_car_bryan.userservice.dto.UserRequestDTO;
import rent_a_car_bryan.userservice.dto.UserResponseDTO;
import rent_a_car_bryan.userservice.entity.EnumRole;
import rent_a_car_bryan.userservice.entity.UserEntity;
import rent_a_car_bryan.userservice.exception.InvalidCredentialsException;
import rent_a_car_bryan.userservice.exception.InvalidRequestException;
import rent_a_car_bryan.userservice.exception.ResourceNotFoundException;
import rent_a_car_bryan.userservice.repository.UserRepository;
import rent_a_car_bryan.userservice.security.JwtService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitarios de la lógica de negocio de UserService. Sin base de datos: el repositorio, el
 * codificador de contraseñas y el generador de JWT son mocks.
 *
 * El PasswordEncoder es un doble sencillo: encode("x") = "HASH(x)" y matches compara contra ese
 * formato. Así las pruebas distinguen una contraseña en claro de una hasheada sin depender de BCrypt.
 *
 * Qué NO cubren (a propósito): que un usuario dado de baja no pueda iniciar sesión depende de
 * @SQLRestriction en la base (findByEmail no lo encuentra); eso va en la ronda de integración.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final long USER_ID = 7L;

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private UserService service;

    // Quién llama al servicio
    enum Caller { NONE, ANONYMOUS, CLIENT, EMPLOYEE, SERVICE, ADMIN }

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.save(any(UserEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(passwordEncoder.encode(any())).thenAnswer(inv -> "HASH(" + inv.getArgument(0) + ")");
        lenient().when(passwordEncoder.matches(any(), any())).thenAnswer(
                inv -> ("HASH(" + inv.getArgument(0) + ")").equals(inv.getArgument(1)));
        lenient().when(jwtService.generateToken(any(UserEntity.class))).thenReturn("jwt-token");
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // =====================================================================================
    // Registro: quién decide el rol
    // =====================================================================================
    @Nested
    @DisplayName("Crear usuario (registro)")
    class Register {

        @ParameterizedTest(name = "{0} pide {1} → queda {2}")
        @CsvSource({
                // El registro público y cualquiera que no sea ADMIN no puede elegir el rol: queda CLIENT
                "NONE,ADMIN,CLIENT",
                "NONE,,CLIENT",
                "ANONYMOUS,ADMIN,CLIENT",
                "CLIENT,ADMIN,CLIENT",
                "CLIENT,EMPLOYEE,CLIENT",
                "EMPLOYEE,ADMIN,CLIENT",
                "SERVICE,ADMIN,CLIENT",
                // Solo un ADMIN autenticado puede dar de alta personal
                "ADMIN,ADMIN,ADMIN",
                "ADMIN,EMPLOYEE,EMPLOYEE",
                "ADMIN,CLIENT,CLIENT",
                // Un ADMIN que no indica rol crea un cliente
                "ADMIN,,CLIENT"
        })
        @DisplayName("el rol lo decide el servidor, no el cuerpo de la petición")
        void roleIsResolvedByServer(Caller caller, EnumRole requested, EnumRole expected) {
            authenticate(caller);

            UserResponseDTO result = service.save(userRequest(requested));

            ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getRole()).isEqualTo(expected);
            assertThat(result.getRole()).isEqualTo(expected);
        }

        @Test
        @DisplayName("la contraseña se guarda hasheada, nunca en claro")
        void passwordIsHashed() {
            service.save(userRequest(null));

            ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(saved.capture());
            assertThat(saved.getValue().getPassword()).isEqualTo("HASH(secret1)").isNotEqualTo("secret1");
        }

        @Test
        @DisplayName("copia los datos de contacto del request")
        void copiesContactData() {
            service.save(userRequest(null));

            ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
            verify(userRepository).save(saved.capture());
            UserEntity user = saved.getValue();
            assertThat(user.getRut()).isEqualTo("12345678-5");
            assertThat(user.getFirstName()).isEqualTo("Ana");
            assertThat(user.getLastName()).isEqualTo("Pérez");
            assertThat(user.getEmail()).isEqualTo("ana@ejemplo.cl");
            assertThat(user.getPhone()).isEqualTo("+56912345678");
            assertThat(user.getAddress()).isEqualTo("Av. Ejemplo 123");
            assertThat(user.getCity()).isEqualTo("Santiago");
            assertThat(user.getCountry()).isEqualTo("Chile");
        }
    }

    // =====================================================================================
    // Editar (admin)
    // =====================================================================================
    @Nested
    @DisplayName("Editar usuario")
    class Update {

        @ParameterizedTest(name = "contraseña = [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        @DisplayName("sin contraseña nueva (nula, vacía o en blanco) se conserva la actual")
        void keepsCurrentPasswordWhenNoneProvided(String password) {
            UserEntity existing = givenUser(USER_ID, EnumRole.CLIENT);
            UserRequestDTO request = userRequest(null);
            request.setPassword(password);

            service.update(USER_ID, request);

            assertThat(existing.getPassword()).isEqualTo("HASH(current-pass)");
            verify(passwordEncoder, never()).encode(any());
        }

        @Test
        @DisplayName("con una contraseña nueva se guarda hasheada")
        void hashesNewPassword() {
            UserEntity existing = givenUser(USER_ID, EnumRole.CLIENT);
            UserRequestDTO request = userRequest(null);
            request.setPassword("newSecret1");

            service.update(USER_ID, request);

            assertThat(existing.getPassword()).isEqualTo("HASH(newSecret1)");
        }

        @Test
        @DisplayName("actualiza los datos personales")
        void updatesPersonalData() {
            givenUser(USER_ID, EnumRole.CLIENT);
            UserRequestDTO request = userRequest(null);
            request.setFirstName("Beatriz");
            request.setCity("Valparaíso");
            request.setEmail("beatriz@ejemplo.cl");

            UserResponseDTO result = service.update(USER_ID, request);

            assertThat(result.getFirstName()).isEqualTo("Beatriz");
            assertThat(result.getCity()).isEqualTo("Valparaíso");
            assertThat(result.getEmail()).isEqualTo("beatriz@ejemplo.cl");
        }

        @Test
        @DisplayName("si el cuerpo no trae rol, se conserva el que tenía")
        void keepsRoleWhenMissing() {
            givenUser(USER_ID, EnumRole.EMPLOYEE);

            UserResponseDTO result = service.update(USER_ID, userRequest(null));

            assertThat(result.getRole()).isEqualTo(EnumRole.EMPLOYEE);
        }

        @Test
        @DisplayName("si el cuerpo trae rol, se cambia (la ruta es solo ADMIN)")
        void changesRoleWhenProvided() {
            givenUser(USER_ID, EnumRole.CLIENT);

            UserResponseDTO result = service.update(USER_ID, userRequest(EnumRole.EMPLOYEE));

            assertThat(result.getRole()).isEqualTo(EnumRole.EMPLOYEE);
        }

        @Test
        @DisplayName("editar un usuario inexistente responde 404")
        void notFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(USER_ID, userRequest(null)))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(userRepository, never()).save(any());
        }
    }

    // =====================================================================================
    // Login
    // =====================================================================================
    @Nested
    @DisplayName("Login")
    class Login {

        @Test
        @DisplayName("con credenciales correctas devuelve el token y los datos de sesión")
        void successfulLogin() {
            UserEntity user = user(USER_ID, EnumRole.EMPLOYEE);
            when(userRepository.findByEmail("ana@ejemplo.cl")).thenReturn(Optional.of(user));

            LoginResponseDTO result = service.login(loginRequest("ana@ejemplo.cl", "current-pass"));

            assertThat(result.getToken()).isEqualTo("jwt-token");
            assertThat(result.getId()).isEqualTo(USER_ID);
            assertThat(result.getFirstName()).isEqualTo("Ana");
            assertThat(result.getLastName()).isEqualTo("Pérez");
            assertThat(result.getEmail()).isEqualTo("ana@ejemplo.cl");
            assertThat(result.getRole()).isEqualTo(EnumRole.EMPLOYEE);
            verify(jwtService).generateToken(user);
        }

        @Test
        @DisplayName("un correo que no existe se rechaza y no se genera token")
        void unknownEmail() {
            when(userRepository.findByEmail("nadie@ejemplo.cl")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.login(loginRequest("nadie@ejemplo.cl", "x")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage("Correo o contraseña incorrectos");

            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("una contraseña incorrecta se rechaza y no se genera token")
        void wrongPassword() {
            when(userRepository.findByEmail("ana@ejemplo.cl")).thenReturn(Optional.of(user(USER_ID, EnumRole.CLIENT)));

            assertThatThrownBy(() -> service.login(loginRequest("ana@ejemplo.cl", "otra")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verify(jwtService, never()).generateToken(any());
        }

        @Test
        @DisplayName("el mensaje es idéntico para correo inexistente y contraseña incorrecta (no revela cuál falló)")
        void sameMessageForBothFailures() {
            when(userRepository.findByEmail("nadie@ejemplo.cl")).thenReturn(Optional.empty());
            when(userRepository.findByEmail("ana@ejemplo.cl")).thenReturn(Optional.of(user(USER_ID, EnumRole.CLIENT)));

            Throwable unknownEmail = catchThrowable(() -> service.login(loginRequest("nadie@ejemplo.cl", "x")));
            Throwable wrongPassword = catchThrowable(() -> service.login(loginRequest("ana@ejemplo.cl", "otra")));

            assertThat(unknownEmail).isInstanceOf(InvalidCredentialsException.class);
            assertThat(wrongPassword).isInstanceOf(InvalidCredentialsException.class);
            assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
        }
    }

    // =====================================================================================
    // Cuenta propia ("Mi perfil")
    // =====================================================================================
    @Nested
    @DisplayName("Cuenta propia")
    class OwnAccount {

        @Test
        @DisplayName("findMe devuelve al usuario del token, sin recibir ningún id")
        void findMeUsesTokenSubject() {
            authenticateAs(String.valueOf(USER_ID), "ROLE_CLIENT");
            givenUser(USER_ID, EnumRole.CLIENT);

            UserResponseDTO result = service.findMe();

            assertThat(result.getId()).isEqualTo(USER_ID);
            verify(userRepository).findById(USER_ID);
        }

        @Test
        @DisplayName("si el usuario del token ya no existe (o fue dado de baja), responde 404")
        void findMeWhenAccountIsGone() {
            authenticateAs(String.valueOf(USER_ID), "ROLE_CLIENT");
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findMe()).isInstanceOf(ResourceNotFoundException.class);
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Caller.class, names = {"NONE", "ANONYMOUS"})
        @DisplayName("sin sesión, las tres operaciones de cuenta propia piden iniciar sesión")
        void requireSession(Caller caller) {
            authenticate(caller);

            assertThatThrownBy(() -> service.findMe())
                    .isInstanceOf(InvalidCredentialsException.class).hasMessageContaining("iniciar sesión");
            assertThatThrownBy(() -> service.updateMe(profile()))
                    .isInstanceOf(InvalidCredentialsException.class);
            assertThatThrownBy(() -> service.changePassword(passwordChange("current-pass", "newSecret1")))
                    .isInstanceOf(InvalidCredentialsException.class);

            verifyNoInteractions(userRepository);
        }

        @Test
        @DisplayName("una autenticación sin confirmar tampoco vale")
        void unauthenticatedTokenIsRejected() {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.unauthenticated(String.valueOf(USER_ID), "x"));

            assertThatThrownBy(() -> service.findMe()).isInstanceOf(InvalidCredentialsException.class);
        }

        @Test
        @DisplayName("updateMe cambia solo los datos de contacto: RUT, rol y contraseña no se tocan")
        void updateMeChangesOnlyContactData() {
            authenticateAs(String.valueOf(USER_ID), "ROLE_CLIENT");
            UserEntity existing = givenUser(USER_ID, EnumRole.CLIENT);
            ProfileUpdateRequestDTO dto = profile();
            dto.setFirstName("Beatriz");
            dto.setPhone("+56999999999");

            UserResponseDTO result = service.updateMe(dto);

            assertThat(result.getFirstName()).isEqualTo("Beatriz");
            assertThat(result.getPhone()).isEqualTo("+56999999999");
            assertThat(existing.getRut()).isEqualTo("12345678-5");
            assertThat(existing.getRole()).isEqualTo(EnumRole.CLIENT);
            assertThat(existing.getPassword()).isEqualTo("HASH(current-pass)");
        }

        @Test
        @DisplayName("updateMe edita la cuenta del token, nunca otra")
        void updateMeTargetsTokenUser() {
            authenticateAs("12", "ROLE_CLIENT");
            when(userRepository.findById(12L)).thenReturn(Optional.of(user(12L, EnumRole.CLIENT)));

            service.updateMe(profile());

            verify(userRepository).findById(12L);
            verify(userRepository, never()).findById(USER_ID);
        }

        @Test
        @DisplayName("cambiar contraseña: con la actual correcta, guarda la nueva hasheada")
        void changePasswordSucceeds() {
            authenticateAs(String.valueOf(USER_ID), "ROLE_CLIENT");
            UserEntity existing = givenUser(USER_ID, EnumRole.CLIENT);

            service.changePassword(passwordChange("current-pass", "newSecret1"));

            assertThat(existing.getPassword()).isEqualTo("HASH(newSecret1)");
            verify(userRepository).save(existing);
        }

        @Test
        @DisplayName("cambiar contraseña: una actual incorrecta se rechaza y no se guarda nada")
        void changePasswordWithWrongCurrent() {
            authenticateAs(String.valueOf(USER_ID), "ROLE_CLIENT");
            givenUser(USER_ID, EnumRole.CLIENT);

            assertThatThrownBy(() -> service.changePassword(passwordChange("incorrecta", "newSecret1")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("actual no es correcta");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("cambiar contraseña: la nueva no puede ser igual a la actual")
        void changePasswordToTheSameOne() {
            authenticateAs(String.valueOf(USER_ID), "ROLE_CLIENT");
            givenUser(USER_ID, EnumRole.CLIENT);

            assertThatThrownBy(() -> service.changePassword(passwordChange("current-pass", "current-pass")))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("distinta");

            verify(userRepository, never()).save(any());
        }
    }

    // =====================================================================================
    // Eliminar y reactivar
    // =====================================================================================
    @Nested
    @DisplayName("Eliminar y reactivar")
    class DeleteAndRestore {

        @Test
        @DisplayName("eliminar un usuario inexistente responde 404")
        void deleteNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.deleteById(USER_ID)).isInstanceOf(ResourceNotFoundException.class);

            verify(userRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("eliminar delega en el repositorio (el borrado lógico lo hace la entidad)")
        void deleteDelegates() {
            givenUser(USER_ID, EnumRole.CLIENT);

            service.deleteById(USER_ID);

            verify(userRepository).deleteById(USER_ID);
        }

        @Test
        @DisplayName("reactivar un usuario que no estaba eliminado responde 404")
        void restoreNotFound() {
            when(userRepository.findDeletedById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.restore(USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("no estaba dado de baja");

            verify(userRepository, never()).restoreById(any());
        }

        @Test
        @DisplayName("reactivar devuelve al usuario con deleted = false")
        void restoreReturnsActiveUser() {
            UserEntity deleted = user(USER_ID, EnumRole.CLIENT);
            deleted.setDeleted(true);
            when(userRepository.findDeletedById(USER_ID)).thenReturn(Optional.of(deleted));

            UserResponseDTO result = service.restore(USER_ID);

            verify(userRepository).restoreById(USER_ID);
            assertThat(result.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("si el correo o el RUT fueron ocupados mientras estaba de baja, el error de integridad sube (el handler lo hace 409)")
        void restoreConflictPropagates() {
            UserEntity deleted = user(USER_ID, EnumRole.CLIENT);
            deleted.setDeleted(true);
            when(userRepository.findDeletedById(USER_ID)).thenReturn(Optional.of(deleted));
            when(userRepository.restoreById(USER_ID)).thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> service.restore(USER_ID)).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // =====================================================================================
    // Lectura
    // =====================================================================================
    @Nested
    @DisplayName("Lectura")
    class Reads {

        @Test
        @DisplayName("el listado se pide ordenado por id")
        void findAllIsOrdered() {
            when(userRepository.findAllByOrderByIdAsc()).thenReturn(
                    List.of(user(1L, EnumRole.ADMIN), user(2L, EnumRole.CLIENT)));

            assertThat(service.findAll()).extracting(UserResponseDTO::getId).containsExactly(1L, 2L);
        }

        @Test
        @DisplayName("la lista de eliminados marca deleted = true")
        void findAllDeletedMarksDeleted() {
            UserEntity deleted = user(USER_ID, EnumRole.CLIENT);
            deleted.setDeleted(true);
            when(userRepository.findAllDeleted()).thenReturn(List.of(deleted));

            assertThat(service.findAllDeleted()).singleElement()
                    .extracting(UserResponseDTO::getDeleted).isEqualTo(true);
        }

        @Test
        @DisplayName("la respuesta incluye el rol y los datos de contacto")
        void responseMapsFields() {
            givenUser(USER_ID, EnumRole.EMPLOYEE);

            UserResponseDTO dto = service.findById(USER_ID);

            assertThat(dto.getRole()).isEqualTo(EnumRole.EMPLOYEE);
            assertThat(dto.getRut()).isEqualTo("12345678-5");
            assertThat(dto.getEmail()).isEqualTo("ana@ejemplo.cl");
            assertThat(dto.getDeleted()).isFalse();
        }

        @Test
        @DisplayName("un deleted nulo en la entidad se muestra como false")
        void nullDeletedIsFalse() {
            UserEntity user = givenUser(USER_ID, EnumRole.CLIENT);
            user.setDeleted(null);

            assertThat(service.findById(USER_ID).getDeleted()).isFalse();
        }

        @Test
        @DisplayName("findById de un usuario inexistente responde 404 con el id")
        void findByIdNotFound() {
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("7");
        }

        @Test
        @DisplayName("la ficha de admin incluye eliminados y lo marca")
        void adminDetailIncludesDeleted() {
            UserEntity deleted = user(USER_ID, EnumRole.CLIENT);
            deleted.setDeleted(true);
            when(userRepository.findAnyById(USER_ID)).thenReturn(Optional.of(deleted));

            assertThat(service.findByIdIncludingDeleted(USER_ID).getDeleted()).isTrue();
        }

        @Test
        @DisplayName("la ficha de admin responde 404 si no existe ni eliminado")
        void adminDetailNotFound() {
            when(userRepository.findAnyById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findByIdIncludingDeleted(USER_ID))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("buscar por RUT devuelve al usuario; si no existe, 404 con el RUT")
        void findByRut() {
            when(userRepository.findByRut("12345678-5")).thenReturn(Optional.of(user(USER_ID, EnumRole.CLIENT)));
            when(userRepository.findByRut("99999999-9")).thenReturn(Optional.empty());

            assertThat(service.findByRut("12345678-5").getId()).isEqualTo(USER_ID);
            assertThatThrownBy(() -> service.findByRut("99999999-9"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("99999999-9");
        }
    }

    // =====================================================================================
    // Utilidades
    // =====================================================================================

    private void authenticate(Caller caller) {
        switch (caller) {
            case NONE -> SecurityContextHolder.clearContext();
            case ANONYMOUS -> SecurityContextHolder.getContext().setAuthentication(
                    new AnonymousAuthenticationToken("key", "anonymousUser",
                            List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
            default -> authenticateAs("1", "ROLE_" + caller.name());
        }
    }

    private void authenticateAs(String name, String... authorities) {
        var granted = java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(name, null, granted));
    }

    // Usuario con contraseña "current-pass" (guardada como HASH(current-pass))
    private UserEntity user(long id, EnumRole role) {
        UserEntity user = new UserEntity();
        ReflectionTestUtils.setField(user, "id", id);
        user.setRut("12345678-5");
        user.setFirstName("Ana");
        user.setLastName("Pérez");
        user.setEmail("ana@ejemplo.cl");
        user.setPassword("HASH(current-pass)");
        user.setPhone("+56912345678");
        user.setAddress("Av. Ejemplo 123");
        user.setCity("Santiago");
        user.setCountry("Chile");
        user.setRole(role);
        user.setDeleted(false);
        return user;
    }

    private UserEntity givenUser(long id, EnumRole role) {
        UserEntity user = user(id, role);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return user;
    }

    private UserRequestDTO userRequest(EnumRole role) {
        UserRequestDTO dto = new UserRequestDTO();
        dto.setRut("12345678-5");
        dto.setFirstName("Ana");
        dto.setLastName("Pérez");
        dto.setEmail("ana@ejemplo.cl");
        dto.setPassword("secret1");
        dto.setPhone("+56912345678");
        dto.setAddress("Av. Ejemplo 123");
        dto.setCity("Santiago");
        dto.setCountry("Chile");
        dto.setRole(role);
        return dto;
    }

    private ProfileUpdateRequestDTO profile() {
        ProfileUpdateRequestDTO dto = new ProfileUpdateRequestDTO();
        dto.setFirstName("Ana");
        dto.setLastName("Pérez");
        dto.setEmail("ana@ejemplo.cl");
        dto.setPhone("+56912345678");
        dto.setAddress("Av. Ejemplo 123");
        dto.setCity("Santiago");
        dto.setCountry("Chile");
        return dto;
    }

    private ChangePasswordRequestDTO passwordChange(String current, String next) {
        ChangePasswordRequestDTO dto = new ChangePasswordRequestDTO();
        dto.setCurrentPassword(current);
        dto.setNewPassword(next);
        return dto;
    }

    private LoginRequestDTO loginRequest(String email, String password) {
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setEmail(email);
        dto.setPassword(password);
        return dto;
    }
}