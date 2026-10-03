import { pedir } from '../../api/cliente';
import type { CategoriaRespuesta } from '../../api/tipos';

/**
 * Las categorias, 100% personalizables por grupo (v1.1, seccion 2.5).
 *
 * A DIFERENCIA de `traerCategorias()` en `features/gastos/api.ts` -- que
 * tiene respaldo offline, porque sin categorias el formulario de alta no
 * puede ni abrirse -- estas tres son SIN CACHE. Administrar categorias
 * necesita de todos modos estar online (son escrituras), y mostrar una
 * lista vieja mientras se administra seria mas confuso que un error.
 */

export function listarCategorias(): Promise<CategoriaRespuesta[]> {
  return pedir<CategoriaRespuesta[]>('/categorias');
}

export function crearCategoria(nombre: string, icono: string): Promise<CategoriaRespuesta> {
  return pedir<CategoriaRespuesta>('/categorias', { metodo: 'POST', cuerpo: { nombre, icono } });
}

/**
 * Cualquier categoria se puede borrar, inclusive una de las seis default --
 * no hay "las originales" como caso especial. Lo unico que el backend
 * protege es no dejar el grupo sin ninguna.
 */
export function borrarCategoria(id: string): Promise<void> {
  return pedir<void>(`/categorias/${id}`, { metodo: 'DELETE' });
}
