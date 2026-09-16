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
 * vuelve en el acto, sin esperar a la red.
 */
export default function NuevoGasto() {
  const router = useRouter();

  return (
    <FormularioDeGasto
      titulo="Nuevo gasto"
      textoDeAccion="Guardar"
      onGuardar={async (datos) => {
        await crearGasto(datos);
        // `back` y no `replace`: esto es un modal que se cierra. El resumen que
        // queda abajo se recarga solo, porque escucha el foco.
        //
        // Cierra EN EL ACTO porque `crearGasto` encola y vuelve sin esperar a la
        // red. Es lo que hace que guardar se sienta igual con una barra de
        // senial que con wifi.
        router.back();
      }}
      onCancelar={() => router.back()}
    />
  );
}
