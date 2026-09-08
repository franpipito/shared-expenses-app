import { useRouter } from 'expo-router';
import { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
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

export default function Login() {
  const { entrar } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  async function alEntrar() {
    setError(null);
    setEnviando(true);
    try {
      await entrar(email.trim(), password);
      // `replace` y no `push`: si fuera push, el gesto de "atras" volveria al
      // login estando ya logueado.
      router.replace('/resumen');
    } catch (e) {
      // El backend devuelve el MISMO mensaje para email inexistente y para
      // contrasena incorrecta, a proposito, para no delatar que cuentas existen.
      // La app no tiene que arruinar eso agregando detalle de su cosecha.
      setError(e instanceof ErrorDeApi ? e.message : 'Algo salio mal. Proba de nuevo.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={estilos.pantalla}
      // En iOS el teclado tapa los campos si no se corre la vista. En Android el
      // sistema ya lo maneja, y forzarlo produce saltos.
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView
        contentContainerStyle={[
          estilos.contenido,
          { paddingTop: insets.top + 40, paddingBottom: insets.bottom + 24 },
        ]}
        keyboardShouldPersistTaps="handled"
      >
        <View style={estilos.encabezado}>
          <Nutria animo="TRANQUILA" tamano={150} />
          <Text style={estilos.marca}>MiNutria</Text>
          <Text style={estilos.bajada}>Los gastos de Viole y Fran</Text>
        </View>

        <View style={estilos.campos}>
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
            autoComplete="current-password"
          />

          {error ? <Text style={estilos.error}>{error}</Text> : null}

          <Boton
            titulo="Entrar"
            onPress={alEntrar}
            cargando={enviando}
            deshabilitado={!email.trim() || !password}
          />
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
  autoComplete?: 'email' | 'current-password';
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
        // Sin esto iOS pone mayuscula al primer caracter del email y el login
        // falla sin que se entienda por que.
        autoCapitalize="none"
        autoCorrect={false}
        style={estilos.input}
        placeholderTextColor={colores.textoSuave}
      />
    </View>
  );
}

const estilos = StyleSheet.create({
  // El fondo es `fondo` (crema) y no `tarjeta` (casi blanco). La pantalla entera
  // es el papel; `tarjeta` es solo para lo que se apoya arriba.
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { flexGrow: 1, paddingHorizontal: 24, justifyContent: 'center' },
  encabezado: { alignItems: 'center', marginBottom: 36 },
  marca: {
    fontFamily: fuentes.displayBold,
    fontSize: 34,
    color: colores.texto,
    marginTop: 8,
  },
  bajada: {
    fontFamily: fuentes.cuerpo,
    fontSize: 15,
    color: colores.textoSuave,
    marginTop: 4,
  },
  campos: { gap: 16 },
  campo: { gap: 6 },
  etiqueta: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 12,
    letterSpacing: 1.2,
    // Versalitas arriba de cada campo: es lo que Viole eligio del mockup de
    // Vercel.
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
  error: {
    fontFamily: fuentes.cuerpo,
    fontSize: 14,
    color: colores.terracotaProfunda,
  },
});
