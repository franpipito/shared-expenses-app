import { pedir } from '../../api/cliente';
import type { GrupoRespuesta, InvitacionRespuesta } from '../../api/tipos';

/**
 * El grupo de quien pregunta.
 *
 * Sin respaldo en disco, a diferencia de `traerGrupo` de la feature de gastos:
 * ese existe para que el alta funcione sin señal (ver `almacenamiento/catalogo`).
 * Invitar, sumarse y salir necesitan la red de todos modos, así que no hay
 * ninguna ventaja en cachear esto acá -- cachearlo igual sería otro lugar donde
 * la copia en disco puede quedar vieja sin que nadie lo note.
 */
export function traerGrupo(): Promise<GrupoRespuesta> {
  return pedir<GrupoRespuesta>('/grupo');
}

/** Genera (o regenera, invalidando el anterior) un código para invitar a alguien. */
export function invitar(): Promise<InvitacionRespuesta> {
  return pedir<InvitacionRespuesta>('/grupo/invitar', { metodo: 'POST' });
}

/** Se suma al grupo dueño de ese código. Devuelve el grupo ya con los dos. */
export function sumarse(codigo: string): Promise<GrupoRespuesta> {
  return pedir<GrupoRespuesta>('/grupo/sumarse', { metodo: 'POST', cuerpo: { codigo } });
}

/**
 * Deja el grupo compartido y vuelve a uno propio. Simétrica: no hace falta el
 * permiso de la otra persona, y no existe una acción para sacarla a ella --
 * ver la sección "Sumarse a un grupo" de CLAUDE.md sobre por qué.
 */
export function salirDelGrupo(password: string): Promise<GrupoRespuesta> {
  return pedir<GrupoRespuesta>('/grupo/salir', { metodo: 'POST', cuerpo: { password } });
}
