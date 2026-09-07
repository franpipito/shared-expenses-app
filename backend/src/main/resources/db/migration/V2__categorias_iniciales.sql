-- Las categorias son datos de referencia: la app no funciona sin ellas, asi que
-- viajan con el esquema y no en un script aparte que alguien puede olvidarse de
-- correr en produccion.
--
-- Son las palabras que uso Viole en la entrevista, no las que suponiamos
-- nosotros. Dijo "uber", no "transporte". Ver docs/entrevista-usuaria.md.
--
-- "otros" no lo nombro ella, pero sin un cajon de sastre un gasto que no encaja
-- en ninguna categoria no se puede cargar, y eso es friccion justo en el peor
-- momento: parada en el mostrador con el pedido listo.

INSERT INTO categoria (nombre, icono) VALUES
    ('cafe',    'coffee'),
    ('uber',    'car'),
    ('comida',  'utensils'),
    ('ropa',    'shirt'),
    ('regalos', 'gift'),
    ('otros',   'ellipsis');
