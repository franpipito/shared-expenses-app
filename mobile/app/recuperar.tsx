import { useRouter } from 'expo-router';
import { useState } from 'react';
import {
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
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';

/**
 * "Me olvide la contrasena", en una sola pantalla con dos pasos: pedir el codigo,
 * y escribirlo con la contrasena nueva.
 *
 * Una pantalla y no dos a proposito: el email del paso 1 hace falta en el 2, y
 * la persona puede ir y venir a la app de Mail sin perder lo que escribio.
 *
 * El mensaje del paso 1 dice "si tiene cuenta, te llega", y no "te mandamos un
 * codigo": el backend responde igual exista o no el email, para no revelar
 * quien tiene cuenta, y la app no tiene que prometer algo que no sabe.
 */
export default function Recuperar() {
  const { pedirCodigo, restablecer } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [paso, setPaso] = useState<'email' | 'codigo'>('email');
  const [email, setEmail] = useState('');
  const [codigo, setCodigo] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function alPedirCodigo() {
    setError(null);
    setEnviando(true);
    try {
      await pedirCodigo(email.trim());
      setPaso('codigo');
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'Algo salió mal. Probá de nuevo.');
    } finally {
      setEnviando(false);
    }
  }

  async function alRestablecer() {
    setError(null);
    setEnviando(true);
    try {
      await restablecer({ email: email.trim(), codigo: codigo.trim(), password });
      router.replace('/resumen');
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'Algo salió mal. Probá de nuevo.');
      setEnviando(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={estilos.pantalla}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={[
          estilos.contenido,
          { paddingTop: insets.top + 24, paddingBottom: insets.bottom + 24 },
        ]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={estilos.encabezado}>
          <Text style={estilos.seccion}>Tu cuenta</Text>
          <Text style={estilos.titulo}>Cambiar la contraseña</Text>
        </View>

        {paso === 'email' ? (
          <View style={estilos.campos}>
            <Text style={estilos.bajada}>
              Te mandamos un código de seis dígitos al mail de tu cuenta.
            </Text>
            <Campo
              etiqueta="Email"
              valor={email}
              alCambiar={setEmail}
              teclado="email-address"
              autoComplete="email"
            />
            {error ? <Text style={estilos.error}>{error}</Text> : null}
            <Boton
              titulo="Mandarme el código"
              onPress={alPedirCodigo}
              cargando={enviando}
              deshabilitado={!email.trim()}
            />
          </View>
        ) : (
          <View style={estilos.campos}>
            <Text style={estilos.bajada}>
              Si {email.trim()} tiene cuenta, te llegó un código. Vence en 15 minutos.
              Si no lo ves, fijate en correo no deseado.
            </Text>
            <Campo
              etiqueta="Código"
              valor={codigo}
              alCambiar={setCodigo}
              teclado="number-pad"
              // iOS ofrece el codigo arriba del teclado cuando llega a la app de Mail.
              autoComplete="one-time-code"
            />
            <Campo
              etiqueta="Contraseña nueva"
              valor={password}
              alCambiar={setPassword}
              secreto
              autoComplete="new-password"
              ayuda="Mínimo 12 caracteres. Una frase que te acuerdes sirve mejor que algo corto y raro."
            />
            {error ? <Text style={estilos.error}>{error}</Text> : null}
            <Boton
              titulo="Cambiar la contraseña"
              onPress={alRestablecer}
              cargando={enviando}
              deshabilitado={codigo.trim().length !== 6 || password.length < 12}
            />
            <Pressable
              onPress={() => {
                setPaso('email');
                setCodigo('');
                setError(null);
              }}
              hitSlop={12}
              accessibilityRole="button"
            >
              <Text style={estilos.link}>No me llegó: pedir otro código</Text>
            </Pressable>
          </View>
        )}

        <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
          <Text style={estilos.link}>Volver a entrar</Text>
        </Pressable>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { flexGrow: 1, paddingHorizontal: 24, justifyContent: 'center', gap: 24 },
  encabezado: { gap: 4 },
  seccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  titulo: { fontFamily: fuentes.displaySemi, fontSize: 26, color: colores.texto },
  bajada: { fontFamily: fuentes.cuerpo, fontSize: 15, lineHeight: 21, color: colores.textoSuave },
  campos: { gap: 16 },
  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
  link: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 15,
    color: colores.rioProfundo,
    textAlign: 'center',
    paddingVertical: 8,
  },
});
