import { useFocusEffect, useRouter } from 'expo-router';
import { useCallback, useState } from 'react';
import {
  ActionSheetIOS,
  Alert,
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
import type { LiquidacionRespuesta } from '../src/api/tipos';
import { Boton } from '../src/componentes/Boton';
import { formatearMonto } from '../src/componentes/Monto';
import { Nutria } from '../src/componentes/Nutria';
import { useSesion } from '../src/features/auth/sesion';
import { useSaldoTotal } from '../src/features/saldo/hooks/useSaldoTotal';
import { colores } from '../src/tema/colores';
import { fuentes, numerosTabulares } from '../src/tema/tipografia';
import { Cargando } from './_layout';

/**
 * Saldar cuentas: el mismo total que ya muestra `saldo.tsx` (v1.1, seccion
 * 2.3), con lo que ese numero por si solo no puede dar: anotar un pago
 * nuevo y ver el historial completo. Antes `saldo.tsx` mostraba un numero
 * DISTINTO (el del mes, sin restar pagos) -- se corrigio porque quedaba
 * desactualizado apenas alguien pagaba algo aca. Ver el comentario de esa
 * pantalla.
 *
 * LA DIRECCION DEL PAGO NO SE PREGUNTA. `RegistrarLiquidacionRequest` admite
 * `meLoPagaron` porque cualquiera de los dos puede abrir esta pantalla para
 * anotar un pago, pero ACA no hace falta un selector: si hoy le debo a la
 * otra persona, el pago que anoto es el mio; si me deben a mi, el pago que
 * anoto es el de la otra persona. La direccion ya la dice quien debe hoy, y
 * preguntarla de nuevo seria el mismo campo dos veces.
 *
 * UNA LIQUIDACION SE EDITA Y SE BORRA DE VERDAD, tocando la fila -- mismo
 * espiritu que ya tienen un ingreso de "Mi Plata" y un aporte de la vaquita
 * (`ActionSheetIOS` con Editar/Borrar/Cancelar). La diferencia con esas dos:
 * ACA CUALQUIERA DE LOS DOS puede tocar CUALQUIER fila, no solo la propia --
 * una liquidacion es un hecho entre los dos, no la propiedad de quien la
 * tipeo. Lo que no se edita es la DIRECCION: si se cargo al reves, hay que
 * borrarla y registrarla de nuevo bien.
 */
export default function Liquidaciones() {
  const { total, historial, cargando, error, recargar, registrar, editar, borrar } = useSaldoTotal();
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
  const [editando, setEditando] = useState<LiquidacionRespuesta | null>(null);
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

  function empezarAEditar(liquidacion: LiquidacionRespuesta) {
    setErrorPago(null);
    setEditando(liquidacion);
    setMonto(String(liquidacion.monto));
  }

  function cancelarEdicion() {
    setErrorPago(null);
    setEditando(null);
    setMonto('');
  }

  /** Tocar una fila del historial. Cualquiera, no solo las propias: ver el comentario de arriba. */
  function tocarFila(liquidacion: LiquidacionRespuesta) {
    ActionSheetIOS.showActionSheetWithOptions(
      {
        title: `${formatearMonto(liquidacion.monto)} · ${liquidacion.fecha}`,
        options: ['Editar', 'Borrar', 'Cancelar'],
        destructiveButtonIndex: 1,
        cancelButtonIndex: 2,
      },
      (indice) => {
        if (indice === 0) empezarAEditar(liquidacion);
        else if (indice === 1) confirmarBorrado(liquidacion);
      },
    );
  }

  function confirmarBorrado(liquidacion: LiquidacionRespuesta) {
    Alert.alert(
      'Borrar este pago',
      `Se va a sacar ${formatearMonto(liquidacion.monto)} del historial. No se puede deshacer.`,
      [
        { text: 'Dejarlo', style: 'cancel' },
        {
          text: 'Borrar',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              setErrorPago(null);
              try {
                await borrar(liquidacion.id);
                if (editando?.id === liquidacion.id) cancelarEdicion();
              } catch (e) {
                setErrorPago(e instanceof ErrorDeApi ? e.message : 'No se pudo borrar el pago.');
              }
            })();
          },
        },
      ],
    );
  }

  async function confirmarFormulario() {
    setErrorPago(null);
    setEnviando(true);
    try {
      if (editando) {
        await editar(editando.id, montoNumero);
      } else {
        await registrar(montoNumero, meLoPagaron);
      }
      setMostrarFormulario(false);
      setEditando(null);
      setMonto('');
    } catch (e) {
      setErrorPago(e instanceof ErrorDeApi ? e.message : 'No se pudo guardar el pago.');
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

        {/*
          Oculto mientras se edita una fila del historial (mas abajo): el
          input de ahi abajo y el de este bloque comparten el estado `monto`,
          mismo criterio que ya usan "Mi Plata" y la vaquita.
        */}
        {total && !aMano && !editando ? (
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
              <View style={estilos.accion}>
                <Boton
                  titulo="Confirmar"
                  onPress={() => void confirmarFormulario()}
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

        {/* Comun a las dos formas de guardar (registrar arriba, editar en la fila mas abajo). */}
        {errorPago ? <Text style={estilos.error}>{errorPago}</Text> : null}

        <View style={estilos.bloque}>
          <Text style={estilos.rotuloSeccion}>
            {historial.length === 0 ? 'Todavía no registraron ningún pago' : 'Historial de pagos'}
          </Text>
          {historial.length > 0 ? (
            <Text style={estilos.ayuda}>Tocá un pago para editarlo o borrarlo.</Text>
          ) : null}
          {historial.map((l) => {
            const yoPague = l.de.id === usuario?.id;
            const etiqueta = yoPague
              ? `Vos le pagaste a ${l.para.nombre}`
              : `${l.de.nombre} te pagó`;

            if (editando?.id === l.id) {
              // Editar de verdad EN LA FILA, mismo patron que "Mi Plata" y la
              // vaquita: el input queda justo donde estaba el numero que se
              // corrige, y la direccion (la etiqueta de arriba) no se toca.
              return (
                <View key={l.id} style={estilos.filaEditando}>
                  <Text style={estilos.filaEtiqueta}>{etiqueta}</Text>
                  <View style={estilos.filaEditandoAcciones}>
                    <TextInput
                      value={monto}
                      onChangeText={setMonto}
                      keyboardType="decimal-pad"
                      autoFocus
                      placeholder="0,00"
                      placeholderTextColor={colores.borde}
                      style={[estilos.inputInline, numerosTabulares]}
                    />
                    <Pressable onPress={cancelarEdicion} accessibilityRole="button" hitSlop={8}>
                      <Text style={estilos.cancelarInline}>Cancelar</Text>
                    </Pressable>
                    <Pressable
                      onPress={() => void confirmarFormulario()}
                      disabled={!montoValido || enviando}
                      accessibilityRole="button"
                      hitSlop={8}
                    >
                      <Text
                        style={[
                          estilos.guardarInline,
                          (!montoValido || enviando) && { opacity: 0.4 },
                        ]}
                      >
                        Guardar
                      </Text>
                    </Pressable>
                  </View>
                </View>
              );
            }

            return (
              <Pressable
                key={l.id}
                onPress={() => tocarFila(l)}
                accessibilityRole="button"
                accessibilityLabel={`${etiqueta}, ${formatearMonto(l.monto)} del ${l.fecha}, tocar para editar o borrar`}
                style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
              >
                <Text style={estilos.filaEtiqueta}>{etiqueta}</Text>
                <Text style={estilos.filaMonto}>{formatearMonto(l.monto)}</Text>
              </Pressable>
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
  ayuda: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.textoSuave },

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
  filaEtiqueta: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.texto },
  filaMonto: {
    fontFamily: fuentes.displaySemi,
    fontSize: 17,
    color: colores.texto,
    ...numerosTabulares,
  },

  // La fila en modo edicion, mismo molde que "Mi Plata" y la vaquita, con la
  // etiqueta de direccion arriba: a diferencia de un ingreso o un aporte, acá
  // SÍ hay algo que preservar a la vista mientras se corrige el monto.
  filaEditando: {
    backgroundColor: colores.tarjeta,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: colores.rioProfundo,
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 10,
  },
  filaEditandoAcciones: { flexDirection: 'row', alignItems: 'center', gap: 14 },
  inputInline: {
    flex: 1,
    backgroundColor: colores.fondo,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 10,
    paddingHorizontal: 14,
    minHeight: 44,
    fontFamily: fuentes.displaySemi,
    fontSize: 18,
    color: colores.texto,
  },
  cancelarInline: { fontFamily: fuentes.cuerpoSemi, fontSize: 14, color: colores.textoSuave },
  guardarInline: { fontFamily: fuentes.cuerpoSemi, fontSize: 14, color: colores.rioProfundo },

  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
