-- Permite invalidar todos los tokens de una persona.
--
-- El problema que resuelve: un JWT es stateless, asi que una vez emitido vale
-- hasta que expire y no hay forma de matarlo. Con 30 dias de vida, si Viole
-- pierde el celular, quien lo tenga entra a su cuenta durante un mes. El
-- "cerrar sesion" del cliente solo borra el token local, no lo invalida.
--
-- La solucion: el token lleva adentro el token_version que tenia el usuario
-- cuando se emitio, y cada request compara ese numero contra el de la base.
-- Subirle uno a esta columna deja fuera, al instante, a todos los tokens
-- emitidos antes.
--
-- Cuesta un numero en el token y una comparacion en memoria (el Usuario ya se
-- cargaba igual en cada request), asi que se recupera la capacidad de revocar
-- sin perder lo que hace comodo a JWT.

ALTER TABLE usuario
    ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
