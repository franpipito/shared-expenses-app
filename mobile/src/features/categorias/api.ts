import { pedir } from '../../api/cliente';
import type { CategoriaRespuesta } from '../../api/tipos';

/**
 * Las categorias de quien esta adentro: cada persona tiene las suyas (seccion
 * 2.9) y nadie nace con ninguna (seccion 2.7).
 *
 * A DIFERENCIA de `traerCategorias()` en `features/gastos/api.ts` -- que tiene
 * respaldo offline, porque sin categorias el formulario de alta no puede ni
 * abrirse -- estas son SIN CACHE: crear, editar y borrar son escrituras
 * deliberadas, que necesitan la red de verdad, y la pantalla que administra la
 * lista no puede mostrar una copia vieja.
 */

export function listarCategorias(): Promise<CategoriaRespuesta[]> {
  return pedir<CategoriaRespuesta[]>('/categorias');
}

export function crearCategoria(nombre: string, icono: string): Promise<CategoriaRespuesta> {
  return pedir<CategoriaRespuesta>('/categorias', { metodo: 'POST', cuerpo: { nombre, icono } });
}

/**
 * Cambia nombre e icono. El backend lo propaga a los gastos ya cargados, asi
 * que despues de esto la lista del mes y "Por categoria" muestran el nombre
 * nuevo sin que la app tenga que hacer nada mas.
 */
export function editarCategoria(
  id: string,
  nombre: string,
  icono: string,
): Promise<CategoriaRespuesta> {
  return pedir<CategoriaRespuesta>(`/categorias/${id}`, {
    metodo: 'PUT',
    cuerpo: { nombre, icono },
  });
}

/** Los gastos que la usaban NO cambian: conservan su nombre e icono. */
export function borrarCategoria(id: string): Promise<void> {
  return pedir<void>(`/categorias/${id}`, { metodo: 'DELETE' });
}
