import { pedir } from '../../api/cliente';
import type { CategoriaRespuesta } from '../../api/tipos';

/**
 * Las categorias, 100% personalizables por grupo y sin ningun default
 * (v1.1, secciones 2.5 y 2.7).
 *
 * A DIFERENCIA de `traerCategorias()` en `features/gastos/api.ts` -- que
 * tiene respaldo offline, porque sin categorias el formulario de alta no
 * puede ni abrirse -- estas dos son SIN CACHE: crear es una escritura, que
 * de todos modos necesita estar online.
 *
 * El backend tambien expone `DELETE /categorias/{id}` (no se tocó: borrar
 * sigue protegiendo la última categoría del grupo), pero ningún lugar de la
 * app lo llama desde que se borró `app/categorias.tsx` -- la sección 2.7
 * quitó la única pantalla de administración. Si alguna vez hace falta borrar
 * desde la app de nuevo, el wrapper es un `pedir` igual de chico que
 * `crearCategoria`.
 */

export function listarCategorias(): Promise<CategoriaRespuesta[]> {
  return pedir<CategoriaRespuesta[]>('/categorias');
}

export function crearCategoria(nombre: string, icono: string): Promise<CategoriaRespuesta> {
  return pedir<CategoriaRespuesta>('/categorias', { metodo: 'POST', cuerpo: { nombre, icono } });
}
