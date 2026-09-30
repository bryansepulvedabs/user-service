package rent_a_car_bryan.userservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "users")
@Data
// Borrado logico: userRepository.deleteById() se traduce a un UPDATE, nunca a un DELETE.
@SQLDelete(sql = "UPDATE users SET deleted = true WHERE id = ?")
// Y todas las consultas filtran los borrados. Importante: eso incluye findByEmail(),
// asi que un usuario dado de baja tampoco puede iniciar sesion.
@SQLRestriction("deleted = false")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 12)
    private String rut;

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EnumRole role;

    // columnDefinition con DEFAULT: sin el, ddl-auto=update falla al agregar una
    // columna NOT NULL sobre una tabla que ya tiene filas.
    @Column(nullable = false, columnDefinition = "boolean not null default false")
    private Boolean deleted = false;

}