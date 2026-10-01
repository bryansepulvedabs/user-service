package rent_a_car_bryan.userservice.controller;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import rent_a_car_bryan.userservice.config.SecurityConfig;
import rent_a_car_bryan.userservice.dto.UserRequestDTO;
import rent_a_car_bryan.userservice.dto.UserResponseDTO;
import rent_a_car_bryan.userservice.entity.EnumRole;
import rent_a_car_bryan.userservice.exception.ResourceNotFoundException;
import rent_a_car_bryan.userservice.security.JwtAuthFilter;
import rent_a_car_bryan.userservice.security.JwtService;
import rent_a_car_bryan.userservice.service.UserService;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Capa web de user-service: reglas de acceso (SecurityConfig + JwtAuthFilter reales), validación
 * del cuerpo, mapeo de rutas y el contrato JSON que consume el frontend. El servicio y el
 * generador/validador de JWT son mocks: no hay base de datos ni claves reales.
 *
 * Los tests del bloque "Errores" asumen que el GlobalExceptionHandler de user-service traduce
 * igual que el de car-service (404, 409, 400 por validación y 500 genérico). Si el tuyo difiere,
 * ese es el bloque que se ajusta.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthFilter.class})
class UserControllerWebTest {

    // Asegura que Spring Security esté activo en el slice de test
    @TestConfiguration
    @EnableWebSecurity
    static class EnableSecurity {
    }

    @Autowired private WebApplicationContext context;
    @Autowired @Qualifier("springSecurityFilterChain") private Filter springSecurityFilterChain;

    @MockitoBean private UserService userService;
    @MockitoBean private JwtService jwtService;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    // =====================================================================================
    // Reglas de acceso
    // =====================================================================================
    @Nested
    @DisplayName("Reglas de acceso")
    class Access {

        private static final String ANONYMOUS = "ANONIMO";
        private static final Set<String> ANY_ROLE = Set.of("CLIENT", "EMPLOYEE", "SERVICE", "ADMIN");

        // Cada fila es una regla de negocio: quién puede llamar a qué.
        private static final List<Endpoint> ENDPOINTS = List.of(
                // Públicos: login y registro de cuenta nueva
                // (el login no está en este slice: aquí solo se comprueba que la regla no lo bloquee)
                new Endpoint(HttpMethod.POST, "/api/auth/login", true, Set.of()),
                new Endpoint(HttpMethod.POST, "/api/users", true, Set.of()),
                // Cuenta propia: cualquier usuario con sesión, sea cual sea su rol
                new Endpoint(HttpMethod.GET, "/api/users/me", false, ANY_ROLE),
                new Endpoint(HttpMethod.PUT, "/api/users/me", false, ANY_ROLE),
                new Endpoint(HttpMethod.PUT, "/api/users/me/password", false, ANY_ROLE),
                // Un usuario por id: ADMIN, o rental-service (SERVICE); un cliente NO puede ver a otros
                new Endpoint(HttpMethod.GET, "/api/users/1", false, Set.of("ADMIN", "SERVICE")),
                new Endpoint(HttpMethod.GET, "/api/users/admin/1", false, Set.of("ADMIN", "SERVICE")),
                // Buscar un cliente por RUT: personal de mostrador
                new Endpoint(HttpMethod.GET, "/api/users/rut/12345678-5", false, Set.of("ADMIN", "EMPLOYEE")),
                // Listados y administración: solo ADMIN
                new Endpoint(HttpMethod.GET, "/api/users", false, Set.of("ADMIN")),
                new Endpoint(HttpMethod.GET, "/api/users/deleted", false, Set.of("ADMIN")),
                new Endpoint(HttpMethod.PUT, "/api/users/1", false, Set.of("ADMIN")),
                new Endpoint(HttpMethod.DELETE, "/api/users/1", false, Set.of("ADMIN")),
                new Endpoint(HttpMethod.PATCH, "/api/users/1/restore", false, Set.of("ADMIN"))
        );

        private static final List<String> ROLES =
                Arrays.asList(ANONYMOUS, "CLIENT", "EMPLOYEE", "SERVICE", "ADMIN");

        static Stream<Arguments> accessMatrix() {
            return ENDPOINTS.stream().flatMap(endpoint ->
                    ROLES.stream().map(role -> Arguments.of(endpoint, role)));
        }

        @ParameterizedTest(name = "{0} · {1}")
        @MethodSource("accessMatrix")
        @DisplayName("quién puede llamar a cada endpoint")
        void whoCanCallWhat(Endpoint endpoint, String role) throws Exception {
            String authorization = ANONYMOUS.equals(role) ? null : tokenFor(role, "1");

            int result = call(endpoint.method(), endpoint.path(), authorization)
                    .andReturn().getResponse().getStatus();

            if (endpoint.isPublic()) {
                assertThat(result).as("endpoint público").isNotIn(401, 403);
            } else if (ANONYMOUS.equals(role)) {
                assertThat(result).as("sin token").isEqualTo(401);
            } else if (endpoint.allowedRoles().contains(role)) {
                assertThat(result).as("rol permitido").isNotIn(401, 403);
            } else {
                assertThat(result).as("rol sin permiso").isEqualTo(403);
            }
        }

        @Test
        @DisplayName("un token inválido en un endpoint protegido responde 401")
        void invalidTokenOnProtectedEndpoint() throws Exception {
            when(jwtService.parseClaims("bad")).thenThrow(new JwtException("firma inválida"));

            call(HttpMethod.GET, "/api/users", "Bearer bad").andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("un token inválido en el registro público no lo bloquea: se trata como anónimo")
        void invalidTokenOnRegistration() throws Exception {
            when(jwtService.parseClaims("bad")).thenThrow(new JwtException("firma inválida"));

            int result = callWithBody(HttpMethod.POST, "/api/users", "Bearer bad", validUserJson("secret1"))
                    .andReturn().getResponse().getStatus();

            assertThat(result).isNotIn(401, 403);
        }

        @Test
        @DisplayName("un rol desconocido en el token no da ningún permiso")
        void unknownRoleHasNoPermissions() throws Exception {
            call(HttpMethod.GET, "/api/users", tokenFor("HACKER", "1")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("un cliente no puede leer la ficha de otro usuario por id")
        void clientCannotReadOthersById() throws Exception {
            call(HttpMethod.GET, "/api/users/2", tokenFor("CLIENT", "1")).andExpect(status().isForbidden());
        }

        record Endpoint(HttpMethod method, String path, boolean isPublic, Set<String> allowedRoles) {
            @Override
            public String toString() {
                return method + " " + path;
            }
        }
    }

    // =====================================================================================
    // Validación del cuerpo
    // =====================================================================================
    @Nested
    @DisplayName("Validación")
    class Validation {

        @Test
        @DisplayName("el registro exige contraseña")
        void registrationRequiresPassword() throws Exception {
            callWithBody(HttpMethod.POST, "/api/users", null, validUserJson(null))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("La contraseña es obligatoria")));
        }

        @Test
        @DisplayName("editar NO exige contraseña: un usuario se puede actualizar sin cambiarla")
        void updateDoesNotRequirePassword() throws Exception {
            callWithBody(HttpMethod.PUT, "/api/users/1", tokenFor("ADMIN", "1"), validUserJson(null))
                    .andExpect(status().isOk());

            verify(userService).update(any(), any(UserRequestDTO.class));
        }

        @Test
        @DisplayName("editar acepta una contraseña vacía (el formulario la manda así para conservar la actual)")
        void updateAcceptsEmptyPassword() throws Exception {
            callWithBody(HttpMethod.PUT, "/api/users/1", tokenFor("ADMIN", "1"), validUserJson(""))
                    .andExpect(status().isOk());
        }

        @ParameterizedTest(name = "contraseña \"{0}\"")
        @ValueSource(strings = {"12345", "x"})
        @DisplayName("una contraseña de menos de 6 caracteres se rechaza al registrar y al editar")
        void shortPasswordIsRejected(String password) throws Exception {
            callWithBody(HttpMethod.POST, "/api/users", null, validUserJson(password))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("entre 6 y 100")));
            callWithBody(HttpMethod.PUT, "/api/users/1", tokenFor("ADMIN", "1"), validUserJson(password))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "RUT {0}")
        @ValueSource(strings = {"12345678-5", "12.345.678-5", "123456785", "1234567K", "1.234.567-8"})
        @DisplayName("acepta los formatos de RUT habituales (con o sin puntos y guion)")
        void validRutFormats(String rut) throws Exception {
            when(userService.save(any(UserRequestDTO.class))).thenReturn(new UserResponseDTO());

            callWithBody(HttpMethod.POST, "/api/users", null, userJson(rut, "ana@ejemplo.cl", "+56912345678", "secret1"))
                    .andExpect(status().isCreated());
        }

        @ParameterizedTest(name = "RUT {0}")
        @ValueSource(strings = {"abc", "12345-6", "12.345.678-", "12345678-55", ""})
        @DisplayName("rechaza RUT con formato inválido")
        void invalidRutFormats(String rut) throws Exception {
            callWithBody(HttpMethod.POST, "/api/users", null, userJson(rut, "ana@ejemplo.cl", "+56912345678", "secret1"))
                    .andExpect(status().isBadRequest());
        }

        @ParameterizedTest(name = "{3}")
        @CsvSource({
                "12345678-5,no-es-correo,+56912345678,El correo no es válido",
                "12345678-5,ana@ejemplo.cl,abc,El teléfono no es válido",
                "abc,ana@ejemplo.cl,+56912345678,El RUT no tiene un formato válido"
        })
        @DisplayName("devuelve el mensaje del campo inválido")
        void fieldMessages(String rut, String email, String phone, String expectedMessage) throws Exception {
            callWithBody(HttpMethod.POST, "/api/users", null, userJson(rut, email, phone, "secret1"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString(expectedMessage)));
        }

        @Test
        @DisplayName("un cuerpo vacío lista los campos obligatorios")
        void emptyBody() throws Exception {
            callWithBody(HttpMethod.POST, "/api/users", null, "{}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("El RUT es obligatorio")))
                    .andExpect(jsonPath("$.message", containsString("El nombre es obligatorio")));
        }
    }

    // =====================================================================================
    // Rutas y contrato JSON
    // =====================================================================================
    @Nested
    @DisplayName("Rutas y contrato")
    class Routes {

        @Test
        @DisplayName("el registro responde 201 y el rol pedido llega al servicio, que es quien decide")
        void registrationDelegatesRoleDecisionToService() throws Exception {
            UserResponseDTO created = response(1L, EnumRole.CLIENT);
            when(userService.save(any(UserRequestDTO.class))).thenReturn(created);

            callWithBody(HttpMethod.POST, "/api/users", null, validUserJson("secret1", "ADMIN"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.role").value("CLIENT"));

            ArgumentCaptor<UserRequestDTO> sent = ArgumentCaptor.forClass(UserRequestDTO.class);
            verify(userService).save(sent.capture());
            // El controlador no filtra el rol: la regla vive en UserService.resolveRoleForNewUser
            assertThat(sent.getValue().getRole()).isEqualTo(EnumRole.ADMIN);
        }

        @Test
        @DisplayName("la respuesta usa 'role' en minúscula (el frontend lo espera así) y nunca incluye la contraseña")
        void responseContract() throws Exception {
            when(userService.findMe()).thenReturn(response(7L, EnumRole.EMPLOYEE));

            call(HttpMethod.GET, "/api/users/me", tokenFor("EMPLOYEE", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7))
                    .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                    .andExpect(jsonPath("$.email").value("ana@ejemplo.cl"))
                    .andExpect(jsonPath("$.password").doesNotExist());
        }

        @Test
        @DisplayName("GET /me no recibe ningún id: usa al usuario del token")
        void meTakesNoId() throws Exception {
            call(HttpMethod.GET, "/api/users/me", tokenFor("CLIENT", "7")).andExpect(status().isOk());

            verify(userService).findMe();
        }

        @Test
        @DisplayName("DELETE responde 204 y llega al servicio")
        void deleteReturns204() throws Exception {
            call(HttpMethod.DELETE, "/api/users/5", tokenFor("ADMIN", "1")).andExpect(status().isNoContent());

            verify(userService).deleteById(5L);
        }

        @Test
        @DisplayName("PATCH /{id}/restore llega al servicio")
        void restoreRoute() throws Exception {
            call(HttpMethod.PATCH, "/api/users/5/restore", tokenFor("ADMIN", "1")).andExpect(status().isOk());

            verify(userService).restore(5L);
        }

        @Test
        @DisplayName("GET /rut/{rut} y GET /deleted llegan al servicio")
        void rutAndDeletedRoutes() throws Exception {
            call(HttpMethod.GET, "/api/users/rut/12345678-5", tokenFor("EMPLOYEE", "2")).andExpect(status().isOk());
            call(HttpMethod.GET, "/api/users/deleted", tokenFor("ADMIN", "1")).andExpect(status().isOk());

            verify(userService).findByRut("12345678-5");
            verify(userService).findAllDeleted();
        }

        @Test
        @DisplayName("PUT /me/password responde 204 sin cuerpo")
        void changePasswordReturns204() throws Exception {
            callWithBody(HttpMethod.PUT, "/api/users/me/password", tokenFor("CLIENT", "7"),
                    "{\"currentPassword\":\"current-pass\",\"newPassword\":\"newSecret1\"}")
                    .andExpect(status().isNoContent());
        }
    }

    // =====================================================================================
    // Errores (asumen un GlobalExceptionHandler equivalente al de car-service)
    // =====================================================================================
    @Nested
    @DisplayName("Errores")
    class Errors {

        @Test
        @DisplayName("usuario inexistente → 404 con el mensaje")
        void notFound() throws Exception {
            when(userService.findByRut("99999999-9"))
                    .thenThrow(new ResourceNotFoundException("Usuario no encontrado con rut : 99999999-9"));

            call(HttpMethod.GET, "/api/users/rut/99999999-9", tokenFor("EMPLOYEE", "2"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message", containsString("99999999-9")));
        }

        @Test
        @DisplayName("correo o RUT duplicado al reactivar → 409 en vez de 500")
        void duplicateOnRestore() throws Exception {
            when(userService.restore(5L)).thenThrow(new DataIntegrityViolationException("duplicate key"));

            call(HttpMethod.PATCH, "/api/users/5/restore", tokenFor("ADMIN", "1"))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("un error inesperado → 500 genérico, sin filtrar detalles internos")
        void unexpectedErrorDoesNotLeakDetails() throws Exception {
            when(userService.findAll()).thenThrow(new IllegalStateException("detalle secreto de la base"));

            call(HttpMethod.GET, "/api/users", tokenFor("ADMIN", "1"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(not(containsString("detalle secreto"))));
        }
    }

    // =====================================================================================
    // Utilidades
    // =====================================================================================

    // Simula un JWT válido: el validador (mock) devuelve claims con el rol y el id indicados
    private String tokenFor(String role, String subject) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(subject);
        when(claims.get("role", String.class)).thenReturn(role);
        when(jwtService.parseClaims("token-" + role + "-" + subject)).thenReturn(claims);
        return "Bearer token-" + role + "-" + subject;
    }

    private ResultActions call(HttpMethod method, String path, String authorization) throws Exception {
        return callWithBody(method, path, authorization, "{}");
    }

    private ResultActions callWithBody(HttpMethod method, String path, String authorization, String json)
            throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return mvc.perform(request);
    }

    private UserResponseDTO response(long id, EnumRole role) {
        UserResponseDTO dto = new UserResponseDTO();
        dto.setId(id);
        dto.setRut("12345678-5");
        dto.setFirstName("Ana");
        dto.setLastName("Pérez");
        dto.setEmail("ana@ejemplo.cl");
        dto.setRole(role);
        dto.setDeleted(false);
        return dto;
    }

    private static String validUserJson(String password) {
        return userJson("12345678-5", "ana@ejemplo.cl", "+56912345678", password);
    }

    private static String validUserJson(String password, String role) {
        return userJson("12345678-5", "ana@ejemplo.cl", "+56912345678", password, role);
    }

    private static String userJson(String rut, String email, String phone, String password) {
        return userJson(rut, email, phone, password, null);
    }

    // password o role en null → el campo se omite del JSON
    private static String userJson(String rut, String email, String phone, String password, String role) {
        StringBuilder json = new StringBuilder("{")
                .append("\"rut\":\"").append(rut).append("\",")
                .append("\"firstName\":\"Ana\",\"lastName\":\"Pérez\",")
                .append("\"email\":\"").append(email).append("\",")
                .append("\"phone\":\"").append(phone).append("\",")
                .append("\"address\":\"Av. Ejemplo 123\",\"city\":\"Santiago\",\"country\":\"Chile\"");
        if (password != null) {
            json.append(",\"password\":\"").append(password).append("\"");
        }
        if (role != null) {
            json.append(",\"role\":\"").append(role).append("\"");
        }
        return json.append("}").toString();
    }
}