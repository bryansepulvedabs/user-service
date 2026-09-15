-- =============================================
-- user-service: initial data
-- =============================================

INSERT INTO users (rut, first_name, last_name, email, password, phone, address, city, country)
VALUES
    ('12345678-9', 'Carlos',    'Muñoz',     'carlos.munoz@email.cl',    'hashed_pass_1', '+56912345678', 'Av. Providencia 1234', 'Santiago', 'Chile'),
    ('98765432-1', 'Valentina', 'Rojas',     'vale.rojas@email.cl',      'hashed_pass_2', '+56923456789', 'Calle Los Leones 567', 'Santiago', 'Chile'),
    ('15678234-5', 'Andrés',    'Pérez',     'andres.perez@email.cl',    'hashed_pass_3', '+56934567890', 'Av. Ossa 890',         'Santiago', 'Chile'),
    ('11223344-5', 'Camila',    'Torres',    'camila.torres@email.cl',   'hashed_pass_4', '+56945678901', 'Pasaje El Monte 321',  'Viña del Mar', 'Chile'),
    ('22334455-6', 'Felipe',    'Soto',      'felipe.soto@email.cl',     'hashed_pass_5', '+56956789012', 'Calle Prat 100',       'Valparaíso', 'Chile');