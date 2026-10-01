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
import type { IngresoRespuesta } from '../src/api/tipos';
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
 * fechas ni corte de mes.
 *
 * CORREGIR UN INGRESO ES EDITARLO O BORRARLO DE VERDAD (sección 2.3c) --
 * A DIFERENCIA DE UN APORTE A LA VAQUITA. Probándolo en el teléfono, un
 * asiento en contrario para arreglar un error de tipeo se sintió como
 * vueltas de más. Tocar una fila del historial abre un menú nativo
 * (Editar / Borrar / Cancelar), mismo espíritu que el menú contextual de
 * WhatsApp sobre un mensaje, pero disparado con un toque simple.
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
 * individual. Positivo va `hoja` (buenas noticias); negativo, fondo
 * `terracotaSuave` con texto `terracotaProfunda` (sección 2.3c: antes era
 * solo texto en rojo, y probándolo no alcanzaba para que se note de un
 * vistazo).
 */
export default function MiPlata() {
  const { balance, cargando, error, recargar, registrar, editar, borrar } = useBalance();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [monto, setMonto] = useState('');
  const [editando, setEditando] = useState<IngresoRespuesta | null>(null);
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

  function empezarAEditar(ingreso: IngresoRespuesta) {
    setErrorAlta(null);
    setEditando(ingreso);
    setMonto(String(ingreso.monto));
  }

  function cancelarEdicion() {
    setErrorAlta(null);
    setEditando(null);
    setMonto('');
  }

  function tocarFila(ingreso: IngresoRespuesta) {
    ActionSheetIOS.showActionSheetWithOptions(
      {
        title: `${formatearMonto(ingreso.monto)} · ${ingreso.fecha}`,
        options: ['Editar', 'Borrar', 'Cancelar'],
        destructiveButtonIndex: 1,
        cancelButtonIndex: 2,
      },
      (indice) => {
        if (indice === 0) empezarAEditar(ingreso);
        else if (indice === 1) confirmarBorrado(ingreso);
      },
    );
  }

  function confirmarBorrado(ingreso: IngresoRespuesta) {
    Alert.alert(
      'Borrar este ingreso',
      `Se va a sacar ${formatearMonto(ingreso.monto)} del historial. No se puede deshacer.`,
      [
        { text: 'Dejarlo', style: 'cancel' },
        {
          text: 'Borrar',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              setErrorAlta(null);
              try {
                await borrar(ingreso.id);
                // Si justo se estaba editando ESTE ingreso, el formulario
                // quedaria apuntando a un id que ya no existe.
                if (editando?.id === ingreso.id) cancelarEdicion();
              } catch (e) {
                setErrorAlta(e instanceof ErrorDeApi ? e.message : 'No se pudo borrar el ingreso.');
              }
            })();
          },
        },
      ],
    );
  }

  async function confirmarFormulario() {
    setErrorAlta(null);
    setEnviando(true);
    try {
      if (editando) {
        await editar(editando.id, montoNumero);
      } else {
        await registrar(montoNumero);
      }
      setMonto('');
      setEditando(null);
    } catch (e) {
      setErrorAlta(e instanceof ErrorDeApi ? e.message : 'No se pudo guardar el ingreso.');
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
          <View style={[estilos.tarjeta, enRojo && estilos.tarjetaEnRojo]}>
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
                <Text style={[estilos.rotulo, enRojo && estilos.rotuloEnRojo]}>Te queda</Text>
                {/*
                  El signo negativo se muestra tal cual (sin Math.abs): probando
                  en el telefono, "Te falta $X" en ambar... digo, en rojo, se leia
                  menos claro que el numero con el signo puesto. Intl.NumberFormat
                  ya sabe poner el "-" en el lugar correcto para es-AR.
                */}
                <Text
                  style={[estilos.numeroGrande, enRojo && estilos.numeroEnRojo]}
                  numberOfLines={1}
                  adjustsFontSizeToFit
                >
                  {formatearMonto(balance.restante)}
                </Text>
                {/*
                  "en total": sin esto, "gastaste $23.000" al lado de "Gastaste
                  este mes: $10.000" en Resumen se lee como un error. No lo es --
                  Mi Plata no tiene corte de mes a proposito (es un balance que
                  se arrastra) -- pero hay que decirlo, no solo documentarlo.
                */}
                <Text style={[estilos.detalle, enRojo && estilos.detalleEnRojo]}>
                  Ingresaste {formatearMonto(balance.ingresado)} en total · gastaste{' '}
                  {formatearMonto(balance.gastado)} en total
                </Text>
                {enRojo ? (
                  <Text style={estilos.aviso}>Cargá un ingreso para ponerte al día.</Text>
                ) : null}
              </>
            )}
          </View>
        ) : null}

        {/*
          Oculto mientras se edita una fila (ver mas abajo): el input de ahi
          abajo y el de esta seccion comparten el estado `monto`, y mostrar los
          dos a la vez se veria como si escribieran en espejo sin motivo.
        */}
        {!editando ? (
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
                onPress={() => void confirmarFormulario()}
                cargando={enviando}
                deshabilitado={!montoValido}
              />
            </View>
          </View>
        ) : null}

        {errorAlta ? <Text style={estilos.error}>{errorAlta}</Text> : null}

        {!nuncaCargoNada && balance ? (
          <View style={estilos.bloque}>
            <Text style={estilos.rotuloSeccion}>Historial</Text>
            <Text style={estilos.ayuda}>Tocá un ingreso para editarlo o borrarlo.</Text>
            {[...balance.ingresos].reverse().map((ingreso) =>
              editando?.id === ingreso.id ? (
                // Editar de verdad EN LA FILA, sin ir al formulario de arriba
                // (pedido de Franco probando en el telefono): el input queda
                // justo donde estaba el numero que se esta corrigiendo.
                <View key={ingreso.id} style={estilos.filaEditando}>
                  <TextInput
                    value={monto}
                    onChangeText={setMonto}
                    keyboardType="decimal-pad"
                    autoFocus
                    placeholder="0,00"
                    placeholderTextColor={colores.borde}
                    style={[estilos.inputInline, numerosTabulares]}
                  />
                  <View style={estilos.accionesInline}>
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
              ) : (
                <Pressable
                  key={ingreso.id}
                  onPress={() => tocarFila(ingreso)}
                  accessibilityRole="button"
                  accessibilityLabel={`Ingreso de ${formatearMonto(ingreso.monto)} del ${ingreso.fecha}, tocar para editar o borrar`}
                  style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
                >
                  <Text style={estilos.filaEtiqueta}>{ingreso.fecha}</Text>
                  <Text style={estilos.filaMonto}>{formatearMonto(ingreso.monto)}</Text>
                </Pressable>
              ),
            )}
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
  // Fondo tintado en vez de solo texto en rojo (seccion 2.3c): mas facil de
  // notar de un vistazo, sin ser una alarma.
  tarjetaEnRojo: { backgroundColor: colores.terracotaSuave, borderColor: colores.terracotaProfunda },
  rotulo: { fontFamily: fuentes.cuerpo, fontSize: 16, color: colores.textoSuave },
  rotuloEnRojo: { color: colores.terracotaProfunda },
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
  detalleEnRojo: { color: colores.terracotaProfunda },
  // El aviso accionable: reemplaza a "Te pasaste por" como unica senial,
  // probando en el telefono no alcanzaba para invitar a hacer algo.
  aviso: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 14,
    color: colores.terracotaProfunda,
    textAlign: 'center',
    marginTop: 10,
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

  // La fila en modo edicion (seccion 2.3c, segunda vuelta): mismo tamano que
  // una fila normal, con un borde mas marcado para que se note cual se esta
  // corrigiendo.
  filaEditando: {
    backgroundColor: colores.tarjeta,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: colores.rioProfundo,
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 10,
  },
  inputInline: {
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
  accionesInline: { flexDirection: 'row', justifyContent: 'flex-end', gap: 24 },
  cancelarInline: { fontFamily: fuentes.cuerpoSemi, fontSize: 14, color: colores.textoSuave },
  guardarInline: { fontFamily: fuentes.cuerpoSemi, fontSize: 14, color: colores.rioProfundo },

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

  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
