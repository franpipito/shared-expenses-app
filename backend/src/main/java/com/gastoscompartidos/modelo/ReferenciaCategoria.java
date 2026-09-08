package com.gastoscompartidos.modelo;

/**
 * El snapshot de una categoria embebido en cada gasto. Mismo razonamiento que
 * {@link ReferenciaUsuario}.
 *
 * Aca el trade-off pincha un poco mas fuerte, porque las categorias SI se
 * pueden llegar a renombrar. Si eso pasa, los gastos viejos quedan con el
 * nombre viejo hasta que se corran a mano.
 *
 * Se acepta igual porque la alternativa es un $lookup contra la coleccion de
 * categorias en cada listado y en cada resumen, para un dato que en la practica
 * no cambia: son las seis palabras que uso la usuaria en la entrevista.
 */
// Igual que en ReferenciaUsuario: el campo NO se llama `id` para que Spring
// Data no lo trate como identificador y lo convierta a ObjectId.
public record ReferenciaCategoria(String categoriaId, String nombre, String icono) {
}
