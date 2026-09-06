-- Datos de referencia para desarrollo. Idempotente: se puede correr las veces
-- que haga falta.
--
--   Get-Content scripts/seed-desarrollo.sql | docker exec -i gastos-postgres psql -U gastos -d gastos
--
-- Desde la sesion 4 este script YA NO crea usuarios ni grupo. Las personas se
-- dan de alta por la API (POST /auth/registro), que es lo unico que sabe hashear
-- una contrasena con BCrypt. El grupo lo crea el primero que se registra.
--
-- Esto NO es una migracion. En la sesion 5 las categorias pasan a ser una
-- migracion de Flyway versionada.

-- Categorias, con las palabras que uso la usuaria en la entrevista.
-- Dijo "uber", no "transporte": ver docs/entrevista-usuaria.md.
-- "otros" no lo nombro ella, pero sin un cajon de sastre un gasto que no encaja
-- en ninguna no se puede cargar, y eso es friccion en el peor momento.
INSERT INTO categoria (nombre, icono) VALUES
    ('cafe',    'coffee'),
    ('uber',    'car'),
    ('comida',  'utensils'),
    ('ropa',    'shirt'),
    ('regalos', 'gift'),
    ('otros',   'ellipsis')
ON CONFLICT (nombre) DO NOTHING;

SELECT 'categorias' AS tabla, count(*) FROM categoria
UNION ALL SELECT 'grupos',   count(*) FROM grupo
UNION ALL SELECT 'usuarios', count(*) FROM usuario;
