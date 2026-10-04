import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
import { Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useSesion } from '../src/features/auth/sesion';
import { traerGrupo } from '../src/features/grupo/api';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';

/**
 * La politica de privacidad publicada. Es la misma URL que se carga en App
 * Store Connect: Apple pide las dos cosas, el link en la ficha de la tienda y
 * que tambien se pueda abrir desde adentro de la app.
 */
const POLITICA_DE_PRIVACIDAD =
  'https://app.notion.com/p/Pol-tica-de-privacidad-MiNutria-3e52ae33cd65802c9d72cc8b9725914f';

/**
 * El menú único (sección 2.3c), a pedido de Franco mirando cómo Instagram
 * junta todo bajo un solo ícono: reemplaza DOS cosas que antes eran
 * separadas en `resumen.tsx` --- el `Modal` chico con las tres filas de
 * navegación (sección 2.3b, pedido de Viole) y la pantalla `ajustes.tsx`
 * completa (que nacía con el borrado de cuenta) --- con una sola pantalla de
 * dos secciones, mismo molde que la captura de "Settings and activity".
 *
 * "Navegación" son las mismas tres filas que ya existían. "Tu cuenta" es
 * `ajustes.tsx` movida tal cual, con el mismo `useEffect` de `traerGrupo()`:
 * las DOS secciones necesitan saber `tienePareja` (la de navegación para
 * esconder "Gastos compartidos"/"La vaquita"; la de cuenta para elegir entre
 * "Sumarse" y "Salir del grupo"), así que se pide una sola vez acá arriba en
 * vez de dos veces en dos pantallas separadas.
 */
export default function Menu() {
  const { usuario, salir } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  // null mientras carga o si el pedido falla: en los dos casos no se muestra
  // ninguna fila que dependa del grupo, en vez de arriesgarse a mostrar la
  // que no corresponde (p. ej. "Sumarse" a alguien que ya tiene pareja).
  const [tienePareja, setTienePareja] = useState<boolean | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const grupo = await traerGrupo();
        setTienePareja(grupo.integrantes.length >= 2);
      } catch {
        setTienePareja(null);
      }
    })();
  }, [usuario?.id]);

  function irA(
    ruta: '/gastos' | '/saldo' | '/vaquita' | '/grupo' | '/salir-del-grupo' | '/borrar-cuenta',
  ) {
    router.push(ruta);
  }

  return (
    <View style={estilos.pantalla}>
      <ScrollView
        contentContainerStyle={[
          estilos.contenido,
          { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 24 },
        ]}
      >
        <View style={estilos.encabezado}>
          <View style={estilos.encabezadoTexto}>
            <Text style={estilos.seccion}>Menú</Text>
            <Text style={estilos.titulo}>{usuario?.nombre ?? 'MiNutria'}</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Resumen</Text>
          </Pressable>
        </View>

        <View style={estilos.grupo}>
          <Text style={estilos.rotuloSeccion}>Navegación</Text>

          <Pressable
            onPress={() => irA('/gastos')}
            accessibilityRole="button"
            style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
          >
            <Text style={estilos.filaTexto}>Ver los gastos del mes</Text>
            <Text style={estilos.flecha}>›</Text>
          </Pressable>

          {/*
            Mismo criterio que ya regia en el Modal viejo: sin pareja no hay
            saldo con nadie ni pozo que armar, y el backend rechazaria las dos
            cosas. `!== false` y no `=== true`: mientras `tienePareja` todavia
            no cargo (null) no se esconde nada de mas.
          */}
          {tienePareja !== false ? (
            <>
              <Pressable
                onPress={() => irA('/saldo')}
                accessibilityRole="button"
                style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
              >
                <Text style={estilos.filaTexto}>Gastos compartidos</Text>
                <Text style={estilos.flecha}>›</Text>
              </Pressable>
              <Pressable
                onPress={() => irA('/vaquita')}
                accessibilityRole="button"
                style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
              >
                <Text style={estilos.filaTexto}>La vaquita del viaje</Text>
                <Text style={estilos.flecha}>›</Text>
              </Pressable>
            </>
          ) : null}
        </View>

        <View style={estilos.grupo}>
          <Text style={estilos.rotuloSeccion}>Tu cuenta</Text>

          {/*
            Orden pedido por Franco revisando el menu nuevo en el telefono:
            lo informativo primero, la salida del grupo en el medio, "Cerrar
            sesion" al final -- que es ademas la unica fila sin flecha, porque
            no navega a ningun lado, solo actua.
          */}
          <Pressable
            onPress={() => void Linking.openURL(POLITICA_DE_PRIVACIDAD)}
            accessibilityRole="link"
            style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
          >
            <Text style={estilos.filaTexto}>Política de privacidad</Text>
            <Text style={estilos.flecha}>›</Text>
          </Pressable>

          {tienePareja === false ? (
            <Pressable
              onPress={() => irA('/grupo')}
              accessibilityRole="button"
              style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
            >
              <Text style={estilos.filaTexto}>Sumarse a un grupo</Text>
              <Text style={estilos.flecha}>›</Text>
            </Pressable>
          ) : null}

          {/*
            Texto plano "Salir del grupo", sin personalizar con el nombre de
            la otra persona: Franco lo pidio mas directo, en linea con como
            se lee "Sumarse a un grupo" arriba.
          */}
          {tienePareja === true ? (
            <Pressable
              onPress={() => irA('/salir-del-grupo')}
              accessibilityRole="button"
              style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
            >
              <Text style={estilos.filaTexto}>Salir del grupo</Text>
              <Text style={estilos.flecha}>›</Text>
            </Pressable>
          ) : null}

          <Pressable
            // `() => salir()` y no `salir` a secas: onPress le pasaria el evento
            // del toque como motivo.
            onPress={() => void salir('manual')}
            accessibilityRole="button"
            style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
          >
            <Text style={estilos.filaTexto}>Cerrar sesión</Text>
          </Pressable>
        </View>

        {/*
          Aparte y abajo, como "Borrar este gasto" en la edicion: la accion que
          no se puede deshacer no comparte grupo con las de todos los dias, y
          lleva el terracota profundo que la app ya usa para lo destructivo.
          Es un link a otra pantalla y no un Alert directo: antes de borrar hay
          que poder leer que pasa con cada cosa, y pedir la contrasena.
        */}
        <Pressable
          onPress={() => irA('/borrar-cuenta')}
          accessibilityRole="button"
          style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
        >
          <Text style={estilos.borrar}>Borrar mi cuenta</Text>
          <Text style={[estilos.flecha, estilos.borrar]}>›</Text>
        </Pressable>
      </ScrollView>
    </View>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { paddingHorizontal: 20, gap: 24 },

  encabezado: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' },
  encabezadoTexto: { flex: 1 },
  seccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  titulo: { fontFamily: fuentes.displaySemi, fontSize: 24, color: colores.texto, marginTop: 4 },
  volver: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },

  grupo: { gap: 8 },
  rotuloSeccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
    marginBottom: 2,
  },
  fila: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    backgroundColor: colores.tarjeta,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colores.borde,
    paddingHorizontal: 18,
    paddingVertical: 14,
  },
  filaPresionada: { backgroundColor: colores.arena },
  filaTexto: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },
  flecha: { fontFamily: fuentes.cuerpoSemi, fontSize: 20, color: colores.rioProfundo },
  borrar: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.terracotaProfunda },
});
