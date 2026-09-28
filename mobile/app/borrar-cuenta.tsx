import { useRouter } from 'expo-router';
import { useState } from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorDeApi } from '../src/api/cliente';
import { Boton } from '../src/componentes/Boton';
import { Campo } from '../src/features/auth/componentes/Campo';
import { useSesion } from '../src/features/auth/sesion';
import { useCola } from '../src/features/gastos/hooks/useCola';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';

/**
 * Borrar la cuenta.
 *
 * Tres frenos, y cada uno por un motivo distinto:
 *
 *  1. **Esta pantalla**, que dice que pasa con cada cosa ANTES de pedir nada.
 *     Es lo que promete la politica de privacidad, dicho igual: lo personal se
 *     va, lo compartido queda en el historial de la otra persona.
 *  2. **La contrasena**, porque el token dura 30 dias y vive en el telefono: un
 *     telefono desbloqueado en otras manos no puede alcanzar para esto.
 *  3. **Un Alert al final**, el mismo patron que borrar un gasto o cerrar la
 *     vaquita, las otras acciones irreversibles de la app.
 *
 * Sin nutria, a proposito. Cualquier nutria aca -- triste, preocupada -- seria
 * usar al personaje para hacer sentir culpa a quien se quiere ir, que es
 * justamente lo que la app decidio no hacer nunca.
 */
export default function BorrarCuenta() {
  const { borrarCuenta } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();
  // Al ganar foco intenta mandar lo pendiente. Lo que quede despues de eso es
  // lo que de verdad se perderia, y se avisa con el numero.
  const { enCamino, rechazados } = useCola();
  const sinMandar = enCamino.length + rechazados.length;

  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function confirmar() {
    Alert.alert('Borrar tu cuenta', 'No se puede deshacer.', [
      { text: 'No', style: 'cancel' },
      {
        text: 'Borrar',
        style: 'destructive',
        onPress: () => {
          void (async () => {
            setError(null);
            setEnviando(true);
            try {
              // Si sale bien, borrarCuenta ya navega al login.
              await borrarCuenta(password);
            } catch (e) {
              setError(e instanceof ErrorDeApi ? e.message : 'No se pudo borrar la cuenta. Probá de nuevo.');
              setEnviando(false);
            }
          })();
        },
      },
    ]);
  }

  return (
    <KeyboardAvoidingView
      style={estilos.pantalla}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={[
          estilos.contenido,
          { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 24 },
        ]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={estilos.encabezado}>
          <View style={estilos.encabezadoTexto}>
            <Text style={estilos.seccion}>Tu cuenta</Text>
            <Text style={estilos.titulo}>Borrar la cuenta</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Cancelar</Text>
          </Pressable>
        </View>

        <View style={estilos.tarjeta}>
          <Text style={estilos.rotulo}>Qué pasa si la borrás</Text>
          <Text style={estilos.punto}>Tus gastos personales se borran para siempre.</Text>
          <Text style={estilos.punto}>
            Los gastos compartidos y tus aportes a la vaquita quedan en el historial de la otra
            persona, para que sus cuentas sigan cerrando. Tu nombre pasa a decir "Cuenta eliminada".
          </Text>
          <Text style={estilos.punto}>
            Si no compartís la app con nadie, se borra todo.
          </Text>
          {sinMandar > 0 ? (
            <Text style={[estilos.punto, estilos.aviso]}>
              {sinMandar === 1
                ? 'Tenés 1 gasto que todavía no se mandó. Se va a perder.'
                : `Tenés ${sinMandar} gastos que todavía no se mandaron. Se van a perder.`}
            </Text>
          ) : null}
        </View>

        <Campo
          etiqueta="Tu contraseña"
          valor={password}
          alCambiar={setPassword}
          secreto
          autoComplete="current-password"
          ayuda="Para confirmar que sos vos."
        />

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        <Boton
          titulo="Borrar mi cuenta"
          onPress={confirmar}
          cargando={enviando}
          deshabilitado={password.length === 0}
        />
      </ScrollView>
    </KeyboardAvoidingView>
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

  tarjeta: {
    backgroundColor: colores.tarjeta,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: colores.borde,
    padding: 20,
    gap: 10,
  },
  rotulo: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  punto: { fontFamily: fuentes.cuerpo, fontSize: 15, lineHeight: 21, color: colores.texto },
  aviso: { fontFamily: fuentes.cuerpoSemi, color: colores.terracotaProfunda },
  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
