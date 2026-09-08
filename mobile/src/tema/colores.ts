/**
 * Los colores de MiNutria.
 *
 * La fuente de verdad es `docs/diseno.md`, donde estan en oklch. Aca estan en
 * hex porque React Native no parsea oklch: su parser acepta hex, rgb(), hsl() y
 * los nombres CSS, y nada mas. La conversion se hizo una sola vez; si hay que
 * cambiar un color, se cambia primero el oklch del doc y se vuelve a convertir,
 * nunca al reves.
 */
export const colores = {
  /** Crema de fondo. Es el papel: va en la pantalla entera. */
  fondo: '#F8F3E6', // oklch(0.965 0.018 92)
  /** Superficie elevada: tarjetas apoyadas sobre el fondo. NO es el fondo. */
  tarjeta: '#FFFDF8', // oklch(0.995 0.008 95)

  texto: '#4F3724', // oklch(0.36 0.045 60)
  textoSuave: '#8A7665', // oklch(0.58 0.035 62)
  borde: '#E6DDCC', // oklch(0.9 0.025 85)
  arena: '#F2E7CE', // oklch(0.93 0.035 88)
  corteza: '#64452E', // oklch(0.42 0.055 58)

  /** Teal: lo compartido, lo tranquilo. */
  rio: '#3B969A', // oklch(0.62 0.085 200)
  rioProfundo: '#157079', // oklch(0.5 0.08 205)
  rioSuave: '#B6E1E1', // oklch(0.88 0.045 195)

  /**
   * AMBAR. Es del gasto hormiga y de nada mas.
   * Si decora un boton o un titulo, deja de significar algo, y significar algo
   * es todo su trabajo. Ver `docs/diseno.md`.
   */
  hormiga: '#D49838', // oklch(0.72 0.13 75)
  hormigaSuave: '#F7E6C3', // oklch(0.93 0.05 85)

  /** Terracota: la accion principal. Un solo boton terracota por pantalla. */
  terracota: '#BC6A49', // oklch(0.61 0.115 42)
  terracotaProfunda: '#8D4C33', // oklch(0.49 0.095 42)

  /** Buenas noticias. */
  hoja: '#3F8B6D', // oklch(0.58 0.09 165)
  nota: '#D7ECE0', // oklch(0.925 0.028 160)
} as const;
