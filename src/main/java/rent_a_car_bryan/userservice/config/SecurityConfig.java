package rent_a_car_bryan.userservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import rent_a_car_bryan.userservice.security.JwtAuthFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sin token (o vencido) responde 401; con token pero sin permiso, 403
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        // Login y registro de cuenta nueva: públicos
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/users").permitAll()
                        // Cuenta propia ("Mi perfil"): cualquier usuario con sesion, sea cual sea su rol.
                        // Tiene que ir ANTES de /api/users/*, que tambien cubriria "/me" y lo dejaria
                        // solo para ADMIN y SERVICE (un cliente recibiria 403 en su propio perfil).
                        .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                        // Lista de usuarios eliminados: solo ADMIN. Tiene que ir ANTES de /api/users/*,
                        // que tambien la cubriria y dejaria entrar al rol SERVICE.
                        .requestMatchers(HttpMethod.GET, "/api/users/deleted").hasRole("ADMIN")
                        // Ficha incluyendo eliminados: ADMIN, o rental-service (SERVICE) al armar el
                        // historial de un arriendo cuyo cliente fue dado de baja
                        .requestMatchers(HttpMethod.GET, "/api/users/admin/*").hasAnyRole("ADMIN", "SERVICE")
                        // Un usuario por id: ADMIN, o rental-service (SERVICE) al armar la respuesta de un arriendo
                        .requestMatchers(HttpMethod.GET, "/api/users/*").hasAnyRole("ADMIN", "SERVICE")
                        // Buscar UN cliente por RUT: ADMIN o EMPLOYEE (ej. crear un arriendo en el mostrador).
                        // Deliberadamente más permisivo que el listado completo, que sí expone a todos.
                        .requestMatchers(HttpMethod.GET, "/api/users/rut/*").hasAnyRole("ADMIN", "EMPLOYEE")
                        // Todo lo demás (listado completo, editar, eliminar, reactivar): solo ADMIN
                        .anyRequest().hasRole("ADMIN")
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}