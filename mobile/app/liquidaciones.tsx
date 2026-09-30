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
import { Nutria } from '../src/componentes/Nutria';
import { useSesion } from '../src/features/auth/sesion';
import { useSaldoTotal } from '../src/features/saldo/hooks/useSaldoTotal';
import { colores } from '../src/tema/colores';
import { fuentes, numerosTabulares } from '../src/tema/tipografia';
import { Cargando } from './_layout';

/**
 * Saldar cuentas: el saldo de toda la historia, y anotar un pago (v1.1,
 * seccion 2.3). Aparte de `saldo.tsx`, que sigue siendo el pulso del mes --
 * ver el porque en `src/features/saldo/api.ts`.
 *
 * LA DIRECCION DEL PAGO NO SE PREGUNTA. `RegistrarLiquidacionRequest` admite
 * `meLoPagaron` porque cualquiera de los dos puede abrir esta pantalla para
 * anotar un pago, pero ACA no hace falta un selector: si hoy le debo a la
 * otra persona, el pago que anoto es el mio; si me deben a mi, el pago que
 * anoto es el de la otra persona. La direccion ya la dice quien debe hoy, y
 * preguntarla de nuevo seria el mismo campo dos veces.
 */
export default function Liquidaciones() {
  const { total, historial, cargando, error, recargar, registrar } = useSaldoTotal();
  const { usuario } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  useFocusEffect(
    useCallback(() => {
      void recargar();
    }, [recargar]),
  );

  const [mostrarFormulario, setMostrarFormulario] = useState(false);
  const [monto, setMonto] = useState('');
  const [errorPago, setErrorPago] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  if (cargando && !total) return <Cargando />;

  const aFavor = total?.aFavorMio ?? 0;
  const aMano = aFavor === 0;
  const meLoPagaron = aFavor > 0;

  const montoNumero = Number(monto.replace(',', '.'));
  const montoValido = monto.trim() !== '' && Number.isFinite(montoNumero) && montoNumero > 0;

  function abrirFormulario() {
    // Prellenado con lo que se debe: liquidar todo de una es el caso comun,
    // y no escribir el numero de nuevo es lo que hace que valga la pena
    // tocar el boton.
    setMonto(total ? String(total.monto) : '');
    setErrorPago(null);
    setMostrarFormulario(true);
  }

  async function confirmar() {
    setErrorPago(null);
    setEnviando(true);
    try {
      await registrar(montoNumero, meLoPagaron);
      setMostrarFormulario(false);
      setMonto('');
    } catch (e) {
      setErrorPago(e instanceof ErrorDeApi ? e.message : 'No se pudo registrar el pago.');
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
            <Text style={estilos.seccion}>Toda la historia</Text>
            <Text style={estilos.titulo}>Saldar cuentas</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Saldo</Text>
          </Pressable>
        </View>

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        {total ? (
          <View style={estilos.tarjeta}>
            <Nutria animo="NOSOTROS" tamano={130} />

            {aMano ? (
              <Text style={estilos.aMano}>Están a mano</Text>
            ) : (
              <>
                <Text style={estilos.rotulo}>
                  {aFavor > 0 ? `${total.deudorNombre} te debe` : `Le debés a ${total.acreedorNombre}`}
                </Text>
                <Text style={estilos.numeroGrande} numberOfLines={1} adjustsFontSizeToFit>
                  {formatearMonto(total.monto)}
                </Text>
              </>
            )}
          </View>
        ) : null}

        {total && !aMano ? (
          mostrarFormulario ? (
            <View style={estilos.bloque}>
              <Text style={estilos.rotuloSeccion}>
                {meLoPagaron
                  ? `Cuánto te pagó ${total.deudorNombre}`
                  : `Cuánto le pagaste a ${total.acreedorNombre}`}
              </Text>
              <TextInput
                value={monto}
                onChangeText={setMonto}
                keyboardType="decimal-pad"
                placeholder="0,00"
                placeholderTextColor={colores.borde}
                style={[estilos.input, numerosTabulares]}
                autoFocus
              />
              {errorPago ? <Text style={estilos.error}>{errorPago}</Text> : null}
              <View style={estilos.accion}>
                <Boton
                  titulo="Confirmar"
                  onPress={() => void confirmar()}
                  cargando={enviando}
                  deshabilitado={!montoValido}
                />
              </View>
            </View>
          ) : (
            <View style={estilos.accion}>
              <Boton titulo="Marcar como pagado" onPress={abrirFormulario} />
            </View>
          )
        ) : null}

        <View style={estilos.bloque}>
          <Text style={estilos.rotuloSeccion}>
            {historial.length === 0 ? 'Todavía no registraron ningún pago' : 'Historial de pagos'}
          </Text>
          {historial.map((l) => {
            const yoPague = l.de.id === usuario?.id;
            const etiqueta = yoPague
              ? `Vos le pagaste a ${l.para.nombre}`
              : `${l.de.nombre} te pagó`;
            return (
              <View key={l.id} style={estilos.fila}>
                <Text style={estilos.filaEtiqueta}>{etiqueta}</Text>
                <Text style={estilos.filaMonto}>{formatearMonto(l.monto)}</Text>
              </View>
            );
          })}
        </View>
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
  rotulo: {
    fontFamily: fuentes.cuerpo,
    fontSize: 16,
    color: colores.textoSuave,
    marginTop: 8,
    textAlign: 'center',
  },
  numeroGrande: {
    fontFamily: fuentes.displayBold,
    fontSize: 40,
    // Teal: el color de lo compartido. Nunca ambar (docs/diseno.md).
    color: colores.rioProfundo,
    marginTop: 4,
    ...numerosTabulares,
  },
  aMano: {
    fontFamily: fuentes.displaySemi,
    fontSize: 26,
    color: colores.hoja,
    marginTop: 8,
    textAlign: 'center',
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
  accion: { alignSelf: 'stretch' },

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
