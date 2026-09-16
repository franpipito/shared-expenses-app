import { useRouter } from 'expo-router';
import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorDeApi } from '../src/api/cliente';
import { Boton } from '../src/componentes/Boton';
import { Nutria } from '../src/componentes/Nutria';
import { useSesion } from '../src/features/auth/sesion';
import { colores } from '../src/tema/colores';
import { fuentes } from '../src/tema/tipografia';

/**
 * Crear la cuenta.
 *
 * ESTA PANTALLA FALTABA, y era el bloqueante mas grande del proyecto: toda la
 * app existe para Viole, y la unica forma de darle una cuenta era que Franco
 * corriera un `curl` desde la compu -- o sea, eligiendole y tipeandole el la
 * contraseña. Una usuaria que arranca su app sin saber su propia clave es un
 * arranque roto.
 *
 * Se usa dos veces en la vida del producto y vale la pena igual.
 *
 * EL MINIMO DE LA CONTRASEÑA SE DICE ANTES, no despues de que la rechacen.
 * `PoliticaDeContrasenas` exige 12 caracteres y ademas rechaza las comunes y las
 * que contienen tu nombre o tu email. Es una politica deliberada (NIST SP
 * 800-63B: largo en vez de reglas de composicion), pero para alguien que nunca
 * uso una app de finanzas, un rechazo en la primera pantalla es justo donde no
 * conviene poner fricción. Anunciar la regla cuesta una linea de texto.
 *
 * Ojo con una inconsistencia del backend: el DTO valida `min = 8` y la politica
 * exige 12. Manda la politica, asi que la pantalla dice 12.
 */
const MINIMO_CONTRASENA = 12;

export default function Registro() {
  const { registrarse } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [nombre, setNombre] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [codigo, setCodigo] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const listo =
    nombre.trim() !== '' &&
    email.trim() !== '' &&
    password.length >= MINIMO_CONTRASENA &&
    codigo.trim() !== '';

  async function alRegistrarse() {
    setError(null);
    setEnviando(true);
    try {
      await registrarse({
        nombre: nombre.trim(),
        // En minuscula y sin espacios: el backend normaliza asi para buscar, y
        // una mayuscula de iOS dejaria a esa persona sin poder entrar despues.
        email: email.trim().toLowerCase(),
        password,
        codigoInvitacion: codigo.trim(),
      });
      // Ya hay sesion: el registro devuelve token. `replace` para que el gesto
      // de "atras" no vuelva al registro estando adentro.
      router.replace('/resumen');
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo crear la cuenta.');
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
          { paddingTop: insets.top + 32, paddingBottom: insets.bottom + 24 },
        ]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={estilos.encabezado}>
          <Nutria animo="CONTENTA" tamano={120} />
          <Text style={estilos.marca}>Crear tu cuenta</Text>
          <Text style={estilos.bajada}>Son dos personas y una sola vez</Text>
        </View>

        <View style={estilos.campos}>
          <Campo etiqueta="Como te llamas" valor={nombre} alCambiar={setNombre} autoComplete="name" />
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
          />
          <Text style={estilos.ayuda}>
            Al menos {MINIMO_CONTRASENA} caracteres. Mejor una frase que te acuerdes
            que algo con simbolos raros, y que no tenga tu nombre ni tu email.
          </Text>

          <Campo etiqueta="Codigo de invitacion" valor={codigo} alCambiar={setCodigo} />
          <Text style={estilos.ayuda}>Te lo pasa la otra persona.</Text>

          {error ? <Text style={estilos.error}>{error}</Text> : null}

          <Boton
            titulo="Crear la cuenta"
            onPress={alRegistrarse}
            cargando={enviando}
            deshabilitado={!listo}
          />

          <Pressable onPress={() => router.replace('/login')} accessibilityRole="button" hitSlop={8}>
            <Text style={estilos.volver}>Ya tengo cuenta</Text>
          </Pressable>
        </View>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

type CampoProps = {
  etiqueta: string;
  valor: string;
  alCambiar: (v: string) => void;
  secreto?: boolean;
  teclado?: 'default' | 'email-address';
  autoComplete?: 'name' | 'email' | 'new-password';
};

function Campo({ etiqueta, valor, alCambiar, secreto, teclado = 'default', autoComplete }: CampoProps) {
  return (
    <View style={estilos.campo}>
      <Text style={estilos.etiqueta}>{etiqueta}</Text>
      <TextInput
        value={valor}
        onChangeText={alCambiar}
        secureTextEntry={secreto}
        keyboardType={teclado}
        autoComplete={autoComplete}
        // Igual que en el login: sin esto iOS pone mayuscula al primer caracter
        // del email y despues no se entiende por que no entra.
        autoCapitalize={autoComplete === 'name' ? 'words' : 'none'}
        autoCorrect={false}
        style={estilos.input}
        placeholderTextColor={colores.textoSuave}
      />
    </View>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { flexGrow: 1, paddingHorizontal: 24, justifyContent: 'center' },
  encabezado: { alignItems: 'center', marginBottom: 28 },
  marca: { fontFamily: fuentes.displayBold, fontSize: 30, color: colores.texto, marginTop: 8 },
  bajada: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.textoSuave, marginTop: 4 },
  campos: { gap: 16 },
  campo: { gap: 6 },
  etiqueta: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 12,
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: colores.textoSuave,
  },
  input: {
    backgroundColor: colores.tarjeta,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 12,
    paddingHorizontal: 16,
    minHeight: 52,
    fontFamily: fuentes.cuerpo,
    fontSize: 17,
    color: colores.texto,
  },
  ayuda: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.textoSuave, marginTop: -8 },
  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
  volver: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 15,
    color: colores.rioProfundo,
    textAlign: 'center',
    paddingTop: 8,
  },
});
