import { FormularioDeGasto } from '../../src/features/gastos/componentes/FormularioDeGasto';

/**
 * Ruta de alta. El formulario vive en `features/gastos/componentes/` porque lo
 * comparte con la edicion: son la misma pantalla con dos modos.
 *
 * Tenerlo una sola vez no es prolijidad. La inversion del `porcentajePagador`
 * -- el calculo mas facil de romper de la app -- esta escrita ahi adentro, y
 * duplicar el formulario significaria duplicarla, con la garantia de que algun
 * dia las dos copias dejen de coincidir.
 */
export default function NuevoGasto() {
  return <FormularioDeGasto />;
}
