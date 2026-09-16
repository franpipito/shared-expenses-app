import { File, Paths } from 'expo-file-system';

import type { CategoriaRespuesta, GrupoRespuesta, PozoRespuesta } from '../api/tipos';

/**
 * El catalogo: lo que el formulario de alta necesita para poder abrirse util.
 *
 * POR QUE EXISTE, Y ES EL AGUJERO MAS GRANDE QUE TENIA LA COLA OFFLINE.
 *
 * La sesion 6.8 resolvio que el POST de un gasto no espere a la red. Pero nadie
 * miro que necesita la PANTALLA antes de poder encolar algo: `traerCategorias()`
 * es un GET, y sin categorias no hay chip que tocar, `categoriaId` se queda en
 * null, y **el boton Guardar nunca se habilita**. O sea que sin senial no habia
 * nada que encolar, y la cola entera no servia justo en el escenario para el que
 * se construyo.
 *
 * Lo mismo con el grupo (sin el no se puede decir "pago la otra persona") y con
 * la vaquita (sin ella desaparece el chip Vaquita, en un viaje donde el 90% de
 * los gastos salen del pozo).
 *
 * QUE SE CACHEA Y QUE NO: solo datos derivados y chiquitos, que el servidor
 * puede regenerar. Nunca gastos -- esos viven en `cola.ts`, que tiene mutex y
 * clave de idempotencia porque perderlos si importa. Si este archivo se corrompe
 * o se borra, el peor caso es que haya que abrir la app con senial una vez.
 *
 * Se pisa entero en cada lectura exitosa, sin TTL: las seis categorias las
 * siembra el backend al arrancar y no cambian, asi que un caché viejo no es un
 * riesgo real. Si algun dia se pueden crear categorias desde la app, este es el
 * primer lugar que va a mentir.
 */
const ARCHIVO = 'catalogo.json';

type Catalogo = {
  categorias?: CategoriaRespuesta[];
  grupo?: GrupoRespuesta;
  /**
   * El pozo abierto, si habia uno la ultima vez que hubo red.
   *
   * OJO CON `vigente`: lo calcula el backend a partir de las fechas del viaje y
   * de su propio reloj con zona horaria. Un `vigente: true` guardado la semana
   * pasada puede ser mentira hoy, asi que **el valor cacheado nunca se usa para
   * preseleccionar la vaquita** -- ver `esDeCache` abajo.
   */
  pozo?: PozoRespuesta | null;
};

function archivo(): File {
  return new File(Paths.document, ARCHIVO);
}

async function leer(): Promise<Catalogo> {
  try {
    const f = archivo();
    if (!f.exists) return {};
    const crudo = await f.text();
    if (!crudo) return {};
    const datos: unknown = JSON.parse(crudo);
    return typeof datos === 'object' && datos !== null ? (datos as Catalogo) : {};
  } catch {
    // Un caché ilegible es como no tener caché. Nunca tiene que romper nada.
    return {};
  }
}

async function guardar(parcial: Catalogo): Promise<void> {
  try {
    const f = archivo();
    if (!f.exists) f.create();
    f.write(JSON.stringify({ ...(await leer()), ...parcial }));
  } catch {
    // Si no se pudo escribir, la app sigue funcionando con red. No vale la pena
    // molestar a nadie con esto.
  }
}

/**
 * Envuelve una lectura de la API con su respaldo en disco.
 *
 * Con red: pide, guarda y devuelve lo fresco. Sin red: devuelve lo ultimo que
 * funciono. Si nunca hubo red, devuelve `null` y quien llama decide.
 */
async function conRespaldo<T>(
  pedir: () => Promise<T>,
  clave: keyof Catalogo,
): Promise<{ dato: T | null; esDeCache: boolean }> {
  try {
    const fresco = await pedir();
    await guardar({ [clave]: fresco } as Catalogo);
    return { dato: fresco, esDeCache: false };
  } catch {
    const guardado = (await leer())[clave];
    return { dato: (guardado as T) ?? null, esDeCache: true };
  }
}

export function categoriasConRespaldo(pedir: () => Promise<CategoriaRespuesta[]>) {
  return conRespaldo(pedir, 'categorias');
}

export function grupoConRespaldo(pedir: () => Promise<GrupoRespuesta>) {
  return conRespaldo(pedir, 'grupo');
}

export function pozoConRespaldo(pedir: () => Promise<PozoRespuesta | null>) {
  return conRespaldo(pedir, 'pozo');
}

/** Al cerrar sesion: el catalogo es del grupo de quien estaba adentro. */
export async function borrarCatalogo(): Promise<void> {
  try {
    const f = archivo();
    if (f.exists) f.delete();
  } catch {
    // idem guardar()
  }
}
