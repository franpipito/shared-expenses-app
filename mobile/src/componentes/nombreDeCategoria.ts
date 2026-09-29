/**
 * Como se MUESTRA una categoria, que no es como se guarda.
 *
 * El backend siembra las seis categorias con nombres en minuscula y sin tilde
 * ("cafe"), y esos nombres son tambien la clave con la que el sembrador las
 * busca y el snapshot que queda en cada gasto. Renombrarlas en la base seria
 * una migracion de datos en produccion (categorias + todos los gastos ya
 * cargados) para arreglar algo que es solo de presentacion.
 *
 * Asi que la base sigue igual y la app traduce al mostrar. El costo conocido:
 * cuando llegue la web, tiene que usar esta misma tabla. Una categoria que no
 * esta aca (si algun dia hay nuevas) se muestra con la primera letra en
 * mayuscula, que nunca queda peor que el nombre crudo.
 */
const NOMBRES: Record<string, string> = {
  cafe: 'Café',
  uber: 'Uber',
  comida: 'Comida',
  ropa: 'Ropa',
  regalos: 'Regalos',
  otros: 'Otros',
};

export function nombreDeCategoria(nombre: string): string {
  return NOMBRES[nombre] ?? nombre.charAt(0).toUpperCase() + nombre.slice(1);
}
