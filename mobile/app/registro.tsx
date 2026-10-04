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
import { Nutria } from '../src/componentes/Nutria';
import { Campo } from '../src/features/auth/componentes/Campo';
import { useSesion } from '../src/features/auth/sesion';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';

/**
 * Crear la cuenta.
 *
 * ESTA PANTALLA FALTABA, y era el agujero mas grande de la app: el backend tiene
 * `POST /auth/registro` desde la sesion 4, pero el cliente solo sabia hacer
 * login. O sea que Viole no podia crearse la cuenta desde el telefono -- hacia
 * falta que alguien corriera un curl por ella. Una app de dos usuarios donde uno
 * de los dos no puede entrar no esta terminada.
 *
 * Hasta la v1.0 pedia un codigo de invitacion, porque habia un unico grupo y
 * el codigo decidia quien entraba. Se saco para publicar en la App Store: cada
 * cuenta nueva arranca con su propio grupo, de un integrante, y la app funciona
 * como registro personal hasta que se pueda sumar a alguien (v1.1). Por eso la
 * bienvenida habla en singular y muestra una sola nutria.
 *
 * El minimo de 12 caracteres se dice ANTES de tipear. Es la recomendacion de
 * NIST SP 800-63B que ya sigue el backend: la regla se muestra de entrada, en
 * vez de rechazar despues de que la persona eligio.
 */
export default function Registro() {
  const { registrarse } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [nombre, setNombre] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  // La validacion de verdad vive en el backend (`PoliticaDeContrasenas`), que es
  // el unico que puede garantizarla. Esto es solo para no habilitar el boton
  // cuando ya se sabe que va a fallar: ahorra un viaje de ida y vuelta, no
  // reemplaza nada.
  const listo = nombre.trim() !== '' && email.trim() !== '' && password.length >= 12;

  async function alRegistrarse() {
    setError(null);
    setEnviando(true);
    try {
      await registrarse({
        nombre: nombre.trim(),
        email: email.trim(),
        password,
      });
      // Y NO a /resumen directo: un grupo recien creado no tiene ninguna
      // categoria propia (seccion 2.7, ya no se siembra ningun default), asi
      // que primero pasa por el mini-onboarding para elegir las suyas.
      router.replace('/onboarding-categorias');
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'Algo salió mal. Probá de nuevo.');
    } finally {
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
          <Nutria animo="CONTENTA" tamano={120} />
          <Text style={estilos.marca}>Sumate</Text>
          <Text style={estilos.bajada}>Anotá lo que gastás. La nutria te acompaña.</Text>
        </View>

        <View style={estilos.campos}>
          <Campo etiqueta="Nombre" valor={nombre} alCambiar={setNombre} autoComplete="name" />
          <Campo
            etiqueta="Email"
            valor={email}
            alCambiar={setEmail}
            teclado="email-address"
            autoComplete="email"
          />
          <Campo
            etiqueta="Contraseña"
            valor={password}
            alCambiar={setPassword}
            secreto
            autoComplete="new-password"
            ayuda="Mínimo 12 caracteres. Una frase que te acuerdes sirve mejor que algo corto y raro."
          />

          {error ? <Text style={estilos.error}>{error}</Text> : null}

          <Boton
            titulo="Crear mi cuenta"
            onPress={alRegistrarse}
            cargando={enviando}
            deshabilitado={!listo}
          />

          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Ya tengo cuenta</Text>
          </Pressable>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { flexGrow: 1, paddingHorizontal: 24, justifyContent: 'center' },
  encabezado: { alignItems: 'center', marginBottom: 28 },
  marca: { fontFamily: fuentes.displayBold, fontSize: 30, color: colores.texto, marginTop: 8 },
  bajada: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.textoSuave, marginTop: 4 },
  campos: { gap: 16 },
  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
  volver: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 15,
    color: colores.rioProfundo,
    textAlign: 'center',
    paddingVertical: 8,
  },
});
