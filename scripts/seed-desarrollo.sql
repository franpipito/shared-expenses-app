-- Datos de desarrollo. Idempotente: se puede correr las veces que haga falta.
--
--   docker exec -i gastos-postgres psql -U gastos -d gastos < scripts/seed-desarrollo.sql
--
-- Esto NO es una migracion. En la sesion 5 las categorias pasan a ser una
-- migracion de Flyway versionada; el grupo y los usuarios de prueba se quedan
-- solo aca, porque son datos de desarrollo y no van a produccion.

-- 1. Categorias, con las palabras que uso la usuaria en la entrevista.
--    Dijo "uber", no "transporte": ver docs/entrevista-usuaria.md.
--    "otros" no lo nombro ella, pero sin un cajon de sastre un gasto que no
--    encaja en ninguna no se puede cargar, y eso es friccion en el peor momento.
INSERT INTO categoria (nombre, icono) VALUES
    ('cafe',    'coffee'),
    ('uber',    'car'),
    ('comida',  'utensils'),
    ('ropa',    'shirt'),
    ('regalos', 'gift'),
    ('otros',   'ellipsis')
ON CONFLICT (nombre) DO NOTHING;

-- 2. Un grupo de prueba.
INSERT INTO grupo (nombre)
SELECT 'Casa'
WHERE NOT EXISTS (SELECT 1 FROM grupo WHERE nombre = 'Casa');

-- 3. Los dos integrantes.
--    El password_hash es un placeholder a proposito: la autenticacion real
--    llega en la sesion 4 y ahi se reemplaza por un hash de BCrypt.
INSERT INTO usuario (nombre, email, password_hash, grupo_id)
SELECT 'Franco', 'franco@local', 'PENDIENTE_SESION_4',
       (SELECT id FROM grupo WHERE nombre = 'Casa' LIMIT 1)
ON CONFLICT (email) DO NOTHING;

INSERT INTO usuario (nombre, email, password_hash, grupo_id)
SELECT 'Ella', 'ella@local', 'PENDIENTE_SESION_4',
       (SELECT id FROM grupo WHERE nombre = 'Casa' LIMIT 1)
ON CONFLICT (email) DO NOTHING;

-- Que quedo cargado:
SELECT 'categorias' AS tabla, count(*) FROM categoria
UNION ALL SELECT 'grupos',   count(*) FROM grupo
UNION ALL SELECT 'usuarios', count(*) FROM usuario;

SELECT id, nombre, email FROM usuario ORDER BY id;
