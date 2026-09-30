-- user-service/src/main/resources/data.sql
--
-- Las contrasenas van en texto plano a proposito: PasswordSeeder las hashea con
-- BCrypt al arrancar (solo si no empiezan con $2a$), asi que en la segunda partida
-- ya quedan hasheadas y no se vuelven a tocar.
--   admin / empleados -> clave123
--   clientes          -> cliente123
--
-- Ids explicitos: rental-service guarda user_id apuntando a esta base.
-- Los RUT llevan digito verificador valido, por si mas adelante agregas validacion.

INSERT INTO users (id, rut, first_name, last_name, email, password, phone, address, city, country, role)
VALUES
    -- Personal: NO tienen arriendos a su nombre, solo operan el sistema
    (1, '11111111-1', 'Carlos',   'Muñoz',    'admin@rentacar.cl',              'clave123',   '+56912345678', 'Av. Providencia 1234', 'Santiago',   'Chile', 'ADMIN'),
    (2, '12345678-5', 'Daniela',  'Fuentes',  'daniela.fuentes@rentacar.cl',    'clave123',   '+56911223344', 'Av. Vitacura 2890',    'Santiago',   'Chile', 'EMPLOYEE'),
    (3, '13456789-9', 'Rodrigo',  'Salas',    'rodrigo.salas@rentacar.cl',      'clave123',   '+56933445566', 'Av. Apoquindo 4501',   'Santiago',   'Chile', 'EMPLOYEE'),

    -- Clientes
    (4,  '15234567-4', 'Javiera',  'Contreras', 'javiera.contreras@gmail.com',  'cliente123', '+56955667788', 'Los Alerces 456',      'Santiago',   'Chile', 'CLIENT'),
    (5,  '16345678-8', 'Matías',   'Rojas',     'matias.rojas@gmail.com',       'cliente123', '+56944556677', 'Pasaje El Roble 120',  'Valparaíso', 'Chile', 'CLIENT'),
    (6,  '17456789-1', 'Camila',   'Vergara',   'camila.vergara@outlook.com',   'cliente123', '+56977889900', 'Manuel Montt 780',     'Santiago',   'Chile', 'CLIENT'),
    (7,  '18567890-3', 'Ignacio',  'Herrera',   'ignacio.herrera@gmail.com',    'cliente123', '+56966778899', 'Av. Alemania 1550',    'Temuco',     'Chile', 'CLIENT'),
    (8,  '19678901-4', 'Fernanda', 'Pizarro',   'fernanda.pizarro@gmail.com',   'cliente123', '+56922334455', 'Calle Prat 233',       'La Serena',  'Chile', 'CLIENT'),
    (9,  '20789012-K', 'Sebastián','Navarro',   'sebastian.navarro@gmail.com',  'cliente123', '+56988990011', 'Los Carrera 909',      'Concepción', 'Chile', 'CLIENT'),
(10, '21890123-9', 'Antonia',  'Lagos',     'antonia.lagos@outlook.com',    'cliente123', '+56900112233', 'Av. Perú 67',          'Antofagasta','Chile', 'CLIENT')
ON CONFLICT DO NOTHING;

SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 1));