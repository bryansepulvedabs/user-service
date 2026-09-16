-- =============================================
-- user-service: initial data
-- =============================================

INSERT INTO users (rut, first_name, last_name, email, password, phone, address, city, country, role)
VALUES
    ('12345678-9', 'Carlos',    'Muñoz',     'carlos.munoz@email.cl',    'admin123',    '+56912345678', 'Av. Providencia 1234', 'Santiago',     'Chile', 'ADMIN'),
    ('98765432-1', 'Valentina', 'Rojas',     'vale.rojas@email.cl',      'client123',   '+56923456789', 'Calle Los Leones 567', 'Santiago',     'Chile', 'CLIENT'),
    ('15678234-5', 'Andrés',    'Pérez',     'andres.perez@email.cl',    'employee123', '+56934567890', 'Av. Ossa 890',         'Santiago',     'Chile', 'EMPLOYEE'),
    ('11223344-5', 'Camila',    'Torres',    'camila.torres@email.cl',   'client567',   '+56945678901', 'Pasaje El Monte 321',  'Viña del Mar', 'Chile', 'CLIENT'),
    ('22334455-6', 'Felipe',    'Soto',      'felipe.soto@email.cl',     'employee567', '+56956789012', 'Calle Prat 100',       'Valparaíso',   'Chile', 'EMPLOYEE');