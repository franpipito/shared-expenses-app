import { useRouter } from 'expo-router';
import { Linking, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useSesion } from '../src/features/auth/sesion';
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
 * Ajustes: lo que es sobre la cuenta y no sobre los gastos.
 *
 * NACE CON EL BORRADO DE CUENTA, que la App Store exige que se pueda hacer desde
 * la app (guideline 5.1.1(v)) y que la politica de privacidad promete en
 * "Ajustes -> Borrar cuenta". Cerrar sesion se mudo aca desde el encabezado del
 * resumen: con dos acciones sobre la cuenta, una pastilla por cada una en la
 * pantalla principal era ruido, y la pantalla que se abre todos los dias tiene
 * que ser de los gastos.
 *
 * Sin boton terracota: esta pantalla no tiene una accion principal, y el
 * terracota es para la accion que la gente viene a hacer.
 */
export default function Ajustes() {
  const { usuario, salir } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

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
            <Text style={estilos.seccion}>Tu cuenta</Text>
            <Text style={estilos.titulo}>{usuario?.nombre ?? 'Ajustes'}</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Resumen</Text>
          </Pressable>
        </View>

        <View style={estilos.grupo}>
          <Pressable
            // `() => salir()` y no `salir` a secas: onPress le pasaria el evento
            // del toque como motivo. Ver el mismo comentario en resumen.tsx.
            onPress={() => void salir('manual')}
            accessibilityRole="button"
            style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
          >
            <Text style={estilos.filaTexto}>Cerrar sesión</Text>
          </Pressable>

          <Pressable
            onPress={() => void Linking.openURL(POLITICA_DE_PRIVACIDAD)}
            accessibilityRole="link"
            style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
          >
            <Text style={estilos.filaTexto}>Política de privacidad</Text>
            <Text style={estilos.flecha}>›</Text>
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
          onPress={() => router.push('/borrar-cuenta')}
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
  contenido: { paddingHorizontal: 20, gap: 20 },

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
