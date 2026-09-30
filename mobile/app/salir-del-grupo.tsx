import { useRouter } from 'expo-router';
import { useEffect, useState } from 'react';
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
import { salirDelGrupo, traerGrupo } from '../src/features/grupo/api';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';

/**
 * Salir del grupo compartido (v1.1, sección 2.1). Mismo molde que
 * `borrar-cuenta.tsx` -- explicar qué pasa con cada cosa, pedir la
 * contraseña, confirmar con un Alert -- pero las consecuencias son otras:
 *
 *  - La cuenta sigue existiendo. Solo cambia de grupo.
 *  - Los PERSONAL se mudan con la persona, no se borran.
 *  - Lo compartido que ya existía queda en el historial de la otra persona
 *    CON el nombre real: a diferencia de borrar la cuenta, nadie desapareció.
 *
 * Sin nutria, por el mismo motivo que en borrar-cuenta: no es un lugar para
 * hacer sentir culpa a quien se quiere ir.
 */
export default function SalirDelGrupo() {
  const { usuario } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [otroNombre, setOtroNombre] = useState<string | null>(null);

  useEffect(() => {
    (async () => {
      try {
        const grupo = await traerGrupo();
        const otro = grupo.integrantes.find((i) => i.id !== usuario?.id);
        setOtroNombre(otro?.nombre ?? null);
      } catch {
        // Sin el nombre el texto queda mas generico, pero no hay motivo para
        // bloquear la pantalla por esto: la accion de salir no depende de
        // poder mostrarlo.
      }
    })();
  }, [usuario?.id]);

  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  function confirmar() {
    Alert.alert('Salir del grupo', 'Vas a dejar de compartir gastos.', [
      { text: 'No', style: 'cancel' },
      {
        text: 'Salir',
        style: 'destructive',
        onPress: () => {
          void (async () => {
            setError(null);
            setEnviando(true);
            try {
              await salirDelGrupo(password);
              router.back();
            } catch (e) {
              setError(e instanceof ErrorDeApi ? e.message : 'No se pudo salir del grupo. Probá de nuevo.');
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
            <Text style={estilos.titulo}>Salir del grupo</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Cancelar</Text>
          </Pressable>
        </View>

        <View style={estilos.tarjeta}>
          <Text style={estilos.rotulo}>Qué pasa si salís</Text>
          <Text style={estilos.punto}>
            Tu cuenta sigue existiendo. Tus gastos personales siguen siendo tuyos.
          </Text>
          <Text style={estilos.punto}>
            {otroNombre
              ? `Los gastos compartidos que ya cargaron quedan en el historial de ${otroNombre}, con tu nombre real.`
              : 'Los gastos compartidos que ya cargaron quedan en el historial de la otra persona, con tu nombre real.'}
          </Text>
          <Text style={estilos.punto}>
            Si en algún momento quieren volver a compartir, alcanza con un código nuevo.
          </Text>
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
          titulo="Salir del grupo"
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
  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
