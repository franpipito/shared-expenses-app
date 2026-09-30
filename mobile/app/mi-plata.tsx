import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorDeApi } from '../src/api/cliente';
import { Boton } from '../src/componentes/Boton';
import { formatearMonto } from '../src/componentes/Monto';
import { useBalance } from '../src/features/balance/hooks/useBalance';
import { colores } from '../src/tema/colores';
import { fuentes, numerosTabulares } from '../src/tema/tipografia';
import { Cargando } from './_layout';

/**
 * "Mi Plata" (v1.1, sección 2.3b): cuánta plata declaraste tener, menos tus
 * gastos personales. Se lo pidió Viole por audio de WhatsApp, usando la app
 * de verdad: "algo muy importante para mí es saber cuánta plata me queda".
 *
 * MISMO INVARIANTE QUE LA VAQUITA (`app/vaquita.tsx`), pero para una sola
 * persona: `restante = ingresos - gastos`, calculado en el backend, sin
 * fechas ni corte de mes. Un ingreso corrige un error de carga con un monto
 * NEGATIVO, nunca se edita ni se borra -- mismo mecanismo que un aporte.
 *
 * Deliberadamente NO es una app de finanzas personales completa: no hay
 * cuentas por medio de pago, ni categorías de ingreso, ni gráficos. Eso
 * contradice "todo es plata" (respuesta 11 de la entrevista) y el resto de
 * lo que este proyecto ya dejó fuera de alcance. Es un número que sube y
 * baja, nada más.
 *
 * Sin nutria: no hay un ánimo real para esta pantalla -- el único que existe
 * es el del gasto hormiga -- y ponerle una decorativa sería vaciarla de
 * sentido, igual que ya se decidió para `borrar-cuenta.tsx`.
 *
 * Nunca ámbar: es exclusivo del gasto hormiga (`docs/diseno.md`). Tampoco
 * teal: esa familia de color ya significa "lo compartido", y esto es plata
 * individual. Positivo va `hoja` (buenas noticias); negativo,
 * `terracotaProfunda`, mismo tono que usa el resto de la app para "mirá
 * esto".
 */
export default function MiPlata() {
  const { balance, cargando, error, recargar, registrar } = useBalance();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [monto, setMonto] = useState('');
  const [errorAlta, setErrorAlta] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  useFocusEffect(
    useCallback(() => {
      void recargar();
    }, [recargar]),
  );

  if (cargando && !balance) return <Cargando />;

  const montoNumero = Number(monto.replace(',', '.'));
  const montoValido = monto.trim() !== '' && Number.isFinite(montoNumero) && montoNumero > 0;
  const nuncaCargoNada = balance ? balance.ingresos.length === 0 : true;
  const enRojo = balance ? balance.restante < 0 : false;

  async function registrarIngreso(signo: 1 | -1) {
    setErrorAlta(null);
    setEnviando(true);
    try {
      await registrar(signo * montoNumero);
      setMonto('');
    } catch (e) {
      setErrorAlta(e instanceof ErrorDeApi ? e.message : 'No se pudo registrar el ingreso.');
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
          { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 24 },
        ]}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          <RefreshControl refreshing={cargando} onRefresh={recargar} tintColor={colores.rio} />
        }
      >
        <View style={estilos.encabezado}>
          <View style={estilos.encabezadoTexto}>
            <Text style={estilos.seccion}>Mi Plata</Text>
            <Text style={estilos.titulo}>Cuánto te queda</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Resumen</Text>
          </Pressable>
        </View>

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        {balance ? (
          <View style={estilos.tarjeta}>
            {nuncaCargoNada ? (
              <>
                <Text style={estilos.vacioTitulo}>Contale a la app cuánta plata tenés</Text>
                <Text style={estilos.vacioBajada}>
                  Cargá un ingreso cuando cobres, te paguen o te regalen plata. Desde
                  ahí, cada gasto personal que anotes se va a descontar solo.
                </Text>
              </>
            ) : (
              <>
                <Text style={estilos.rotulo}>{enRojo ? 'Te pasaste por' : 'Te queda'}</Text>
                <Text
                  style={[estilos.numeroGrande, enRojo && estilos.numeroEnRojo]}
                  numberOfLines={1}
                  adjustsFontSizeToFit
                >
                  {formatearMonto(Math.abs(balance.restante))}
                </Text>
                <Text style={estilos.detalle}>
                  Ingresaste {formatearMonto(balance.ingresado)} · gastaste{' '}
                  {formatearMonto(balance.gastado)}
                </Text>
              </>
            )}
          </View>
        ) : null}

        <View style={estilos.bloque}>
          <Text style={estilos.rotuloSeccion}>Agregar un ingreso</Text>
          <TextInput
            value={monto}
            onChangeText={setMonto}
            keyboardType="decimal-pad"
            placeholder="0,00"
            placeholderTextColor={colores.borde}
            style={[estilos.input, numerosTabulares]}
          />
          <Text style={estilos.ayuda}>Cobraste, te pagaron, vendiste algo: sumalo acá.</Text>
          <View style={estilos.accion}>
            <Boton
              titulo="Agregar ingreso"
              onPress={() => void registrarIngreso(1)}
              cargando={enviando}
              deshabilitado={!montoValido}
            />
          </View>
          {/*
            Mismo mecanismo que "sacar del pozo" en la vaquita: los ingresos
            son inmutables a propósito, así que corregir uno mal cargado es
            un asiento en contrario, no una edición.
          */}
          <Pressable
            onPress={() => void registrarIngreso(-1)}
            disabled={!montoValido || enviando}
            accessibilityRole="button"
            accessibilityLabel="Corregir un ingreso cargado de más"
            hitSlop={8}
          >
            <Text style={[estilos.corregir, !montoValido && { opacity: 0.4 }]}>
              Me equivoqué: cargué de más
            </Text>
          </Pressable>
          {errorAlta ? <Text style={estilos.error}>{errorAlta}</Text> : null}
        </View>

        {!nuncaCargoNada && balance ? (
          <View style={estilos.bloque}>
            <Text style={estilos.rotuloSeccion}>Historial</Text>
            {[...balance.ingresos].reverse().map((ingreso, i) => (
              <View key={`${ingreso.fecha}-${i}`} style={estilos.fila}>
                <Text style={estilos.filaEtiqueta}>{ingreso.fecha}</Text>
                <Text style={estilos.filaMonto}>{formatearMonto(ingreso.monto)}</Text>
              </View>
            ))}
          </View>
        ) : null}
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
    padding: 24,
    alignItems: 'center',
  },
  rotulo: { fontFamily: fuentes.cuerpo, fontSize: 16, color: colores.textoSuave },
  numeroGrande: {
    fontFamily: fuentes.displayBold,
    fontSize: 40,
    // Nunca ambar (es del gasto hormiga) ni teal (es de lo compartido).
    color: colores.hoja,
    marginTop: 4,
    ...numerosTabulares,
  },
  numeroEnRojo: { color: colores.terracotaProfunda },
  detalle: {
    fontFamily: fuentes.cuerpo,
    fontSize: 14,
    color: colores.textoSuave,
    marginTop: 6,
    textAlign: 'center',
    ...numerosTabulares,
  },

  vacioTitulo: {
    fontFamily: fuentes.displaySemi,
    fontSize: 20,
    color: colores.texto,
    textAlign: 'center',
  },
  vacioBajada: {
    fontFamily: fuentes.cuerpo,
    fontSize: 14,
    color: colores.textoSuave,
    textAlign: 'center',
    marginTop: 8,
  },

  bloque: { gap: 8 },
  rotuloSeccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
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
    fontFamily: fuentes.displaySemi,
    fontSize: 24,
    color: colores.texto,
  },
  ayuda: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.textoSuave },
  accion: { alignSelf: 'stretch' },
  corregir: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 14,
    color: colores.terracotaProfunda,
    textAlign: 'center',
    paddingVertical: 8,
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
  filaEtiqueta: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.texto },
  filaMonto: {
    fontFamily: fuentes.displaySemi,
    fontSize: 17,
    color: colores.texto,
    ...numerosTabulares,
  },

  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
