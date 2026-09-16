import { useRouter } from 'expo-router';

import { FormularioDeGasto } from '../../src/features/gastos/componentes/FormularioDeGasto';
import { crearGasto } from '../../src/features/gastos/api';

/**
 * Cargar un gasto.
 *
 * Quedo asi de corta porque el formulario vive en `FormularioDeGasto`, que
 * comparte con la pantalla de edicion. Esta decide una sola cosa: que pasa al
 * guardar.
 *
 * Y lo que pasa es que **se encola**. `crearGasto` escribe en el telefono y
 * vuelve en el acto, sin esperar a la red: por eso `router.back()` corre
 * enseguida y con una barra de senial se siente igual que con wifi.
 */
export default function NuevoGasto() {
  const router = useRouter();

  return (
    <FormularioDeGasto
      titulo="Nuevo gasto"
      textoDeAccion="Guardar"
      onGuardar={crearGasto}
      // `back` y no `replace`: esto es un modal que se cierra. El resumen que
      // queda abajo se recarga solo, porque escucha el foco.
      onCancelar={() => router.back()}
    />
  );
}
