package com.gastoscompartidos.modelo;

/**
 * El snapshot de un usuario que vive EMBEBIDO adentro de cada gasto.
 *
 * Es la decision de modelado central de la migracion a Mongo, y la que mas da
 * para explicar.
 *
 * En Postgres, `gasto.pagado_por_id` era una foreign key y el nombre se traia
 * con un join. Aca el nombre se copia adentro del documento del gasto. Eso
 * significa que la consulta mas frecuente de la app —- listar los gastos del
 * mes -— **lee un solo documento por gasto y no necesita ningun $lookup**.
 *
 * El precio, que es real y hay que poder decirlo: si un usuario cambiara de
 * nombre, los gastos ya cargados seguirian mostrando el nombre viejo. En este
 * dominio es aceptable porque no existe un endpoint para cambiarlo. Si algun
 * dia existe, la opciones son actualizar todos los gastos con un updateMany, o
 * aceptar el snapshot como historico ("asi se llamaba cuando lo cargo"), que en
 * facturacion es lo correcto y aca seria discutible.
 *
 * Es un record: inmutable por construccion, que es exactamente lo que uno
 * quiere de un snapshot.
 *
 * OJO CON EL NOMBRE DEL CAMPO: es `usuarioId` y NO `id`, a proposito.
 *
 * Spring Data trata cualquier propiedad llamada `id` como el identificador de
 * la entidad: la guarda en el campo `_id` y, si el String parece un ObjectId
 * hexadecimal, LO CONVIERTE A ObjectId. Eso pasa tambien adentro de un documento
 * embebido, en silencio.
 *
 * El sintoma fue feo: el snapshot quedaba como {_id: ObjectId(...), nombre},
 * mientras las etapas de agregacion escritas a mano preguntaban por
 * `pagadoPor.id` con un String. Ninguna comparacion daba verdadera, y el saldo
 * salia con el signo invertido sin ningun error.
 *
 * Ademas de evitar el bug, el nombre es mas honesto: un snapshot no es una
 * entidad con identidad propia, es una copia. No le corresponde un `_id`.
 */
public record ReferenciaUsuario(String usuarioId, String nombre) {
}
