/**
 * El mes como lo espera y lo devuelve la API: `yyyy-MM`.
 *
 * Vive en `api/` y no adentro de una feature porque **es parte del contrato del
 * backend**, no de una pantalla: es el `YearMonth` de Java escrito como texto.
 * Lo usan resumen y gastos, y la regla de `docs/diseno.md` es que lo que usan
 * dos features sube; lo que usa una sola se queda adentro.
 *
 * Vale saber que **el backend NO confia en este valor**: tiene su propio bean
 * `Clock` con zona `America/Argentina/Buenos_Aires` y calcula el periodo
 * `[desde, hasta)` el mismo. Si el telefono estuviera en otra zona, el mes
 * pedido podria no ser el que el backend considera "actual" -- y la respuesta
 * del resumen trae `desde` y `hasta` justamente para que se pueda ver.
 */
export function mesActual(): string {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`;
}

const NOMBRE_DE_MES = new Intl.DateTimeFormat('es-AR', { month: 'long' });

/**
 * `"2026-09"` -> `"septiembre"`.
 *
 * OJO CON EL PARSEO: `new Date("2026-09")` interpreta el string como UTC, y en
 * Buenos Aires (UTC-3) eso cae el 31 de agosto a las 21:00 -- o sea que el mes
 * que se muestra seria el ANTERIOR. Por eso se parte el texto y se construye la
 * fecha con el constructor de tres argumentos, que es local. Es el mismo
 * problema de zona horaria que motivo el bean `Clock` del backend.
 */
export function nombreDelMes(mes: string): string {
  const [anio, numeroDeMes] = mes.split('-').map(Number);
  return NOMBRE_DE_MES.format(new Date(anio, numeroDeMes - 1, 1));
}
