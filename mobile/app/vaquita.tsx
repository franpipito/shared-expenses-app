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
import type { AporteRespuesta, GastoRespuesta, PozoRespuesta } from '../src/api/tipos';
import { Boton } from '../src/componentes/Boton';
import { formatearMonto } from '../src/componentes/Monto';
import { Nutria } from '../src/componentes/Nutria';
import { useSesion } from '../src/features/auth/sesion';
import { FilaGasto } from '../src/features/gastos/componentes/FilaGasto';
import { aportar, borrarAporte, cerrarPozo, crearPozo, editarAporte } from '../src/features/vaquita/api';
import { useVaquita } from '../src/features/vaquita/hooks/useVaquita';
import { colores } from '../src/tema/colores';
import { fuentes, numerosTabulares } from '../src/tema/tipografia';
import { Cargando } from './_layout';

/**
 * La vaquita del viaje.
 *
 * El numero grande es **cuanto queda**, y ese es todo el producto de esta
 * pantalla: la pregunta que se hace alguien de viaje no es "cuanto llevamos
 * gastado" sino "nos alcanza para la cena buena".
 *
 * TRES REGLAS QUE NO SE NEGOCIAN:
 *
 * 1. **Nada de ambar.** El ambar es del gasto hormiga y de nada mas
 *    (`docs/diseno.md`). El numero de esta pantalla es teal, que es el color de
 *    lo compartido. Un gasto del viaje puede ser hormiga o no, y eso se ve en
 *    la lista de abajo, gasto por gasto -- no en el numero grande.
 * 2. **El restante puede ser negativo y hay que dibujarlo.** Si se les acabo la
 *    vaquita, el gasto se cargo igual: bloquear una carga parada en el mostrador
 *    es el pecado capital de esta app. En rojo va terracota, que es el color de
 *    "mira esto", nunca ambar.
 * 3. **La app no hace ninguna cuenta con plata.** `restante` viene resuelto del
 *    backend. Los montos llegan como `number`, o sea punto flotante, y restar
 *    aca reintroduce el problema del centavo.
 *
 * Las dos nutrias (`NOSOTROS`) son las mismas de la pantalla de saldo: Viole
 * pidio explicitamente "dos nutrias que sean ellos" al evaluar los mockups, y
 * esta es la otra seccion de a dos.
 */
export default function Vaquita() {
  const { pozo, gastos, cerrados, cargando, error, recargar, fijarPozo } = useVaquita();
  const { usuario } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  useFocusEffect(
    useCallback(() => {
      void recargar();
    }, [recargar]),
  );

  if (cargando && !pozo) return <Cargando />;

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
            <Text style={estilos.seccion}>La vaquita</Text>
            <Text style={estilos.titulo}>{pozo ? pozo.nombre : 'Para un viaje'}</Text>
          </View>
          <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
            <Text style={estilos.volver}>Resumen</Text>
          </Pressable>
        </View>

        {error ? <Text style={estilos.error}>{error}</Text> : null}

        {/*
          `error ? null` antes del estado vacio, igual que hace la lista de
          gastos. Sin esto, un fallo de red dibuja el formulario de "abrir la
          vaquita" -- porque el estado vacio ES ese formulario -- y alguien sin
          senial en la ruta a Bariloche concluye que la vaquita se perdio y
          trata de crear otra. Confundir "fallo" con "no hay" es peor que
          mostrar un error.
        */}
        {pozo ? (
          <PozoAbierto
            pozo={pozo}
            gastos={gastos}
            idUsuarioActual={usuario?.id}
            onCambio={fijarPozo}
            onRecargar={recargar}
          />
        ) : error ? null : (
          <SinVaquita onCreada={fijarPozo} />
        )}

        {/*
          Los viajes terminados. Es la unica puerta a sus gastos: la lista del
          mes los filtra y /pozos/activo deja de devolverlos al cerrarse. Sin
          esto, un cero de mas visto al volver de Bariloche quedaba para siempre.
        */}
        {cerrados.length > 0 ? (
          <View style={estilos.bloque}>
            <Text style={estilos.rotuloSeccion}>Viajes anteriores</Text>
            {cerrados.map((c) => (
              <Pressable
                key={c.id}
                onPress={() => router.push(`/viaje/${c.id}`)}
                accessibilityRole="button"
                style={({ pressed }) => [estilos.fila, pressed && { opacity: 0.7 }]}
              >
                <Text style={estilos.filaEtiqueta}>{c.nombre}</Text>
                <Text style={estilos.flecha}>›</Text>
              </Pressable>
            ))}
          </View>
        ) : null}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

/* -------------------------------------------------------------------------- */

type PropsSinVaquita = {
  onCreada: (pozo: PozoRespuesta) => void;
};

/**
 * El estado vacio, que **es un formulario y no un cartel**.
 *
 * Un estado vacio que solo dice "no hay nada todavia" obliga a un tap mas para
 * llegar a la accion. Como abrir la vaquita es algo que se hace una vez, el
 * camino mas corto es que la pantalla vacia YA sea el formulario.
 *
 * Solo se pide el nombre. El objetivo es opcional a proposito: abrir la vaquita
 * tiene que costar un tap, no un tramite. Si abrirla es tramite, no la abren.
 */
function SinVaquita({ onCreada }: PropsSinVaquita) {
  const [nombre, setNombre] = useState('');
  const [objetivo, setObjetivo] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const objetivoNumero = Number(objetivo.replace(',', '.'));
  const objetivoValido = objetivo.trim() === '' || (Number.isFinite(objetivoNumero) && objetivoNumero > 0);
  const listo = nombre.trim() !== '' && objetivoValido;

  async function guardar() {
    setError(null);
    setEnviando(true);
    try {
      onCreada(
        await crearPozo({
          nombre: nombre.trim(),
          objetivo: objetivo.trim() === '' ? undefined : objetivoNumero,
        }),
      );
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo abrir la vaquita.');
    } finally {
      setEnviando(false);
    }
  }

  return (
    <View style={estilos.tarjeta}>
      <Nutria animo="NOSOTROS" tamano={140} />
      <Text style={estilos.vacioTitulo}>Junten plata para un viaje</Text>
      <Text style={estilos.vacioBajada}>
        Los dos aportan, y los gastos del viaje salen de ahí. Lo que sacan de la
        vaquita no genera deuda entre ustedes: la plata ya se repartió al aportarla.
      </Text>

      <View style={estilos.formulario}>
        <Text style={estilos.etiqueta}>Cómo se llama</Text>
        <TextInput
          value={nombre}
          onChangeText={setNombre}
          placeholder="Bariloche"
          placeholderTextColor={colores.textoSuave}
          maxLength={60}
          style={estilos.input}
        />

        <Text style={estilos.etiqueta}>Cuánto quieren juntar (opcional)</Text>
        <TextInput
          value={objetivo}
          onChangeText={setObjetivo}
          keyboardType="decimal-pad"
          placeholder="800000"
          placeholderTextColor={colores.borde}
          style={[estilos.input, numerosTabulares]}
        />
        {/*
          "No es un tope" no es una aclaracion de cortesia: los presupuestos
          estan descartados en este producto porque la usuaria es freelance con
          ingresos irregulares, y un numero que parece un limite se lee como
          presupuesto. Esto es plata que ya existe, no una meta que te juzga.
        */}
        <Text style={estilos.ayuda}>
          No es un tope: nada se rechaza por pasarlo. Sirve para ver cuánto falta.
        </Text>
      </View>

      {error ? <Text style={estilos.error}>{error}</Text> : null}

      <View style={estilos.accion}>
        <Boton
          titulo="Abrir la vaquita"
          onPress={guardar}
          cargando={enviando}
          deshabilitado={!listo}
        />
      </View>
    </View>
  );
}

/* -------------------------------------------------------------------------- */

type PropsPozo = {
  pozo: PozoRespuesta;
  gastos: GastoRespuesta[];
  idUsuarioActual: string | undefined;
  onCambio: (pozo: PozoRespuesta | null) => void;
  onRecargar: () => void;
};

function PozoAbierto({ pozo, gastos, idUsuarioActual, onCambio, onRecargar }: PropsPozo) {
  const router = useRouter();
  const [monto, setMonto] = useState('');
  const [editando, setEditando] = useState<AporteRespuesta | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  const montoNumero = Number(monto.replace(',', '.'));
  const montoValido = monto.trim() !== '' && Number.isFinite(montoNumero) && montoNumero > 0;
  const enRojo = pozo.restante < 0;

  function empezarAEditar(aporte: AporteRespuesta) {
    setError(null);
    setEditando(aporte);
    setMonto(String(aporte.monto));
  }

  function cancelarEdicion() {
    setError(null);
    setEditando(null);
    setMonto('');
  }

  /**
   * Tocar una fila DEL HISTORIAL (no la del otro integrante: esas no son
   * tocables, ver mas abajo). Mismo menu nativo que Mi Plata -- Editar /
   * Borrar / Cancelar -- disparado con un toque simple.
   */
  function tocarFila(aporte: AporteRespuesta) {
    ActionSheetIOS.showActionSheetWithOptions(
      {
        title: `${formatearMonto(aporte.monto)} · ${aporte.fecha}`,
        options: ['Editar', 'Borrar', 'Cancelar'],
        destructiveButtonIndex: 1,
        cancelButtonIndex: 2,
      },
      (indice) => {
        if (indice === 0) empezarAEditar(aporte);
        else if (indice === 1) confirmarBorrado(aporte);
      },
    );
  }

  function confirmarBorrado(aporte: AporteRespuesta) {
    Alert.alert(
      'Borrar este aporte',
      `Se van a sacar ${formatearMonto(aporte.monto)} del pozo. No se puede deshacer.`,
      [
        { text: 'Dejarlo', style: 'cancel' },
        {
          text: 'Borrar',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              setError(null);
              try {
                onCambio(await borrarAporte(pozo.id, aporte.id));
                if (editando?.id === aporte.id) cancelarEdicion();
              } catch (e) {
                setError(e instanceof ErrorDeApi ? e.message : 'No se pudo borrar el aporte.');
              }
            })();
          },
        },
      ],
    );
  }

  async function confirmarFormulario() {
    setError(null);
    setEnviando(true);
    try {
      if (editando) {
        onCambio(await editarAporte(pozo.id, editando.id, montoNumero));
      } else {
        onCambio(await aportar(pozo.id, { monto: montoNumero }));
      }
      setMonto('');
      setEditando(null);
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo registrar el aporte.');
    } finally {
      setEnviando(false);
    }
  }

  /**
   * Cerrar es irreversible: no hay endpoint para reabrir un pozo. Por eso pasa
   * por un Alert de confirmacion, que es lo unico de esta app que lo hace.
   */
  function confirmarCierre() {
    Alert.alert(
      `Cerrar ${pozo.nombre}`,
      enRojo
        ? 'Se pasaron de lo que aportaron. Al cerrarla no se va a poder aportar ni cargar más gastos, y no se puede reabrir.'
        : `Quedan ${formatearMonto(pozo.restante)} sin usar. Al cerrarla no se va a poder aportar ni cargar más gastos, y no se puede reabrir.`,
      [
        { text: 'Dejarla abierta', style: 'cancel' },
        {
          text: 'Cerrar',
          style: 'destructive',
          onPress: () => {
            void (async () => {
              try {
                await cerrarPozo(pozo.id);
                // Se cerro: ya no hay pozo activo, asi que la pantalla vuelve al
                // estado vacio. El sobrante lo arreglan ellos por fuera de la app
                // -- no hay devolucion automatica, y es deliberado.
                onCambio(null);
                onRecargar();
              } catch (e) {
                setError(e instanceof ErrorDeApi ? e.message : 'No se pudo cerrar la vaquita.');
              }
            })();
          },
        },
      ],
    );
  }

  return (
    <>
      <View style={estilos.tarjeta}>
        <Nutria animo="NOSOTROS" tamano={130} />

        <Text style={estilos.rotulo}>{enRojo ? 'Se pasaron por' : 'Queda'}</Text>
        {/*
          El numero grande. `numberOfLines` + `adjustsFontSizeToFit` para que se
          achique antes que partirse en dos lineas, que es el bug conocido del
          resumen. Con montos de seis cifras y coma decimal, aca pasaria seguro.

          Teal, o terracota si estan en rojo. NUNCA ambar.
        */}
        <Text
          style={[estilos.numeroGrande, enRojo && estilos.numeroEnRojo]}
          numberOfLines={1}
          adjustsFontSizeToFit
        >
          {formatearMonto(Math.abs(pozo.restante))}
        </Text>

        <Text style={estilos.detalle}>
          Aportaron {formatearMonto(pozo.aportado)} · gastaron {formatearMonto(pozo.gastado)}
        </Text>

        {pozo.objetivo !== null ? (
          <Text style={estilos.detalle}>
            La meta era {formatearMonto(pozo.objetivo)}
          </Text>
        ) : null}

        {/*
          Atajo a la pantalla de siempre, con el chip Vaquita ya puesto -- NO
          un formulario nuevo ni una segunda copia de FormularioDeGasto. El
          default automatico por fecha (sacado en la auditoria de la 6.10) no
          cubre comprar algo del viaje antes de que arranque o despues de que
          termine; este boton es la salida a mano para ese caso, pidiendola la
          persona en vez de que la pantalla la adivine.

          Chico y con borde, no el `Boton` ancho de terracota: esta pantalla ya
          tiene uno (Aportar), y docs/diseno.md es explicito -- un solo boton
          terracota por pantalla, o ninguno es el principal.
        */}
        <Pressable
          onPress={() => router.push({ pathname: '/gasto/nuevo', params: { destino: 'VAQUITA' } })}
          accessibilityRole="button"
          style={({ pressed }) => [estilos.agregarGasto, pressed && estilos.agregarGastoPresionado]}
        >
          <Text style={estilos.agregarGastoTexto}>+ Agregar gasto</Text>
        </Pressable>
      </View>

      {/*
        Cuanto puso cada uno. Se listan los dos SIEMPRE, incluso el que puso
        cero: "Viole $0" es justo lo que hay que mirar antes de salir de viaje,
        no un hueco que convenga esconder.

        Si pusieron distinto, la diferencia es la unica deuda que una vaquita
        puede generar. La app no la calcula todavia: con dos personas mirando
        estos dos numeros, la cuenta la hacen ellos.
      */}
      <View style={estilos.bloque}>
        <Text style={estilos.rotuloSeccion}>Quién aportó cuánto</Text>
        {pozo.porPersona.map((p) => (
          <View key={p.usuarioId} style={estilos.fila}>
            <Text style={estilos.filaEtiqueta}>{p.nombre}</Text>
            <Text style={estilos.filaMonto}>{formatearMonto(p.total)}</Text>
          </View>
        ))}
      </View>

      {/*
        Puramente informativo, igual que el bloque de arriba: a diferencia de
        "quién aportó cuánto", una diferencia ACA no genera ninguna deuda --
        la plata ya se repartió al aportar. Contesta una pregunta distinta
        ("¿quién gastó más del viaje?"), no "¿quién le debe a quién?".
      */}
      <View style={estilos.bloque}>
        <Text style={estilos.rotuloSeccion}>Quién gastó cuánto</Text>
        {pozo.gastadoPorPersona.map((p) => (
          <View key={p.usuarioId} style={estilos.fila}>
            <Text style={estilos.filaEtiqueta}>{p.nombre}</Text>
            <Text style={estilos.filaMonto}>{formatearMonto(p.total)}</Text>
          </View>
        ))}
      </View>

      {/*
        Oculto mientras se edita una fila del historial (mas abajo): el input
        de aca y el de la fila comparten el estado `monto`, y mostrar los dos
        a la vez se veria como si escribieran en espejo sin motivo -- mismo
        criterio que "Mi Plata".
      */}
      {!editando ? (
        <View style={estilos.bloque}>
          {/*
            "Aportar" y no "poner": "ponerla" tiene doble sentido en
            Argentina, y aportar es ademas la palabra del dominio (Aporte en
            el backend).
          */}
          <Text style={estilos.rotuloSeccion}>Tu aporte</Text>
          <View style={estilos.filaAporte}>
            <TextInput
              value={monto}
              onChangeText={setMonto}
              keyboardType="decimal-pad"
              placeholder="0,00"
              placeholderTextColor={colores.borde}
              style={[estilos.input, estilos.inputAporte, numerosTabulares]}
            />
          </View>
          {/*
            Queda a nombre tuyo y no hay forma de anotar plata a nombre del
            otro: el backend lo saca del token. Decirlo evita que alguien
            busque el selector que no existe.
          */}
          <Text style={estilos.ayuda}>Queda a tu nombre.</Text>
          <View style={estilos.accion}>
            <Boton
              titulo="Aportar"
              onPress={() => void confirmarFormulario()}
              cargando={enviando}
              deshabilitado={!montoValido}
            />
          </View>
        </View>
      ) : null}

      {error ? <Text style={estilos.error}>{error}</Text> : null}

      {/*
        Cada aporte de la lista, tocable -- pero SOLO el tuyo. El del otro
        integrante se ve pero no se toca: un aporte es la afirmacion de una
        persona sobre SU plata ("puse esto"), y alterarlo por otra abriria
        la puerta a vaciar en silencio lo que esa persona puso. El backend
        rechaza igual un intento directo por la API (404, sin distinguir del
        inexistente); esto es solo que la UI no invite a algo que va a fallar.
      */}
      {pozo.aportes.length > 0 ? (
        <View style={estilos.bloque}>
          <Text style={estilos.rotuloSeccion}>Historial de aportes</Text>
          <Text style={estilos.ayuda}>Tocá uno de los tuyos para editarlo o borrarlo.</Text>
          {[...pozo.aportes].reverse().map((aporte) => {
            if (editando?.id === aporte.id) {
              // Editar de verdad EN LA FILA, mismo patron que Mi Plata: el
              // input queda justo donde estaba el numero que se corrige.
              return (
                <View key={aporte.id} style={estilos.filaEditando}>
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
              );
            }

            const esMio = aporte.usuario.id === idUsuarioActual;
            if (!esMio) {
              return (
                <View key={aporte.id} style={estilos.fila}>
                  <Text style={estilos.filaEtiqueta}>{aporte.usuario.nombre} · {aporte.fecha}</Text>
                  <Text style={estilos.filaMonto}>{formatearMonto(aporte.monto)}</Text>
                </View>
              );
            }

            return (
              <Pressable
                key={aporte.id}
                onPress={() => tocarFila(aporte)}
                accessibilityRole="button"
                accessibilityLabel={`Tu aporte de ${formatearMonto(aporte.monto)} del ${aporte.fecha}, tocar para editar o borrar`}
                style={({ pressed }) => [estilos.fila, pressed && estilos.filaPresionada]}
              >
                <Text style={estilos.filaEtiqueta}>{aporte.usuario.nombre} · {aporte.fecha}</Text>
                <Text style={estilos.filaMonto}>{formatearMonto(aporte.monto)}</Text>
              </Pressable>
            );
          })}
        </View>
      ) : null}

      <View style={estilos.bloque}>
        <Text style={estilos.rotuloSeccion}>
          {gastos.length === 0 ? 'Todavía no gastaron nada' : 'Los gastos del viaje'}
        </Text>
        {/*
          Esta lista NO se corta por mes, a diferencia de todo el resto de la
          app: el pozo es el recorte. Bariloche cruza de septiembre a octubre y
          una vista mensual lo partiria al medio.

          Se reusa FilaGasto tal cual, y con eso viene gratis lo que importa: la
          marca ambar de gasto hormiga se sigue viendo gasto por gasto. Un
          souvenir carisimo es hormiga aunque haya salido de la vaquita.
        */}
        {gastos.map((g) => (
          <FilaGasto
            key={g.id}
            gasto={g}
            idUsuarioActual={idUsuarioActual}
            // SE PUEDEN TOCAR, y no es un detalle: la lista del mes filtra los
            // gastos del pozo con `sinPozo()`, asi que **esta pantalla es el
            // unico lugar de la app donde aparecen**. Si las filas no abrieran
            // la edicion, un gasto del viaje cargado mal quedaria mal para
            // siempre -- justo lo que la pantalla de edicion vino a arreglar
            // para los gastos normales.
            alTocar={() => router.push(`/gasto/${g.id}`)}
          />
        ))}
      </View>

      <Pressable onPress={confirmarCierre} accessibilityRole="button" hitSlop={8}>
        <Text style={estilos.cerrar}>Cerrar la vaquita</Text>
      </Pressable>
    </>
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
  },
  numeroGrande: {
    fontFamily: fuentes.displayBold,
    fontSize: 40,
    // Teal: el color de lo compartido. El ambar es del gasto hormiga y de nada
    // mas -- ver docs/diseno.md.
    color: colores.rioProfundo,
    marginTop: 4,
    ...numerosTabulares,
  },
  // Terracota, que es el color de "mira esto". Tampoco ambar.
  numeroEnRojo: { color: colores.terracotaProfunda },
  detalle: {
    fontFamily: fuentes.cuerpo,
    fontSize: 14,
    color: colores.textoSuave,
    marginTop: 6,
    textAlign: 'center',
    ...numerosTabulares,
  },

  // Chico y con borde a proposito: el `Boton` ancho de terracota ya lo usa
  // "Aportar" mas abajo, y esta pantalla no puede tener dos.
  agregarGasto: {
    alignSelf: 'center',
    marginTop: 16,
    borderWidth: 1,
    borderColor: colores.rio,
    borderRadius: 999,
    paddingHorizontal: 18,
    minHeight: 44,
    justifyContent: 'center',
  },
  agregarGastoPresionado: { backgroundColor: colores.rioSuave },
  agregarGastoTexto: { fontFamily: fuentes.cuerpoSemi, fontSize: 15, color: colores.rioProfundo },

  vacioTitulo: {
    fontFamily: fuentes.displaySemi,
    fontSize: 22,
    color: colores.texto,
    marginTop: 12,
    textAlign: 'center',
  },
  vacioBajada: {
    fontFamily: fuentes.cuerpo,
    fontSize: 14,
    color: colores.textoSuave,
    textAlign: 'center',
    marginTop: 8,
  },

  formulario: { alignSelf: 'stretch', gap: 8, marginTop: 20 },
  etiqueta: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
    marginTop: 8,
  },
  input: {
    backgroundColor: colores.fondo,
    borderWidth: 1,
    borderColor: colores.borde,
    borderRadius: 12,
    paddingHorizontal: 16,
    minHeight: 52,
    fontFamily: fuentes.cuerpo,
    fontSize: 17,
    color: colores.texto,
  },
  inputAporte: {
    backgroundColor: colores.tarjeta,
    fontFamily: fuentes.displaySemi,
    fontSize: 24,
  },
  filaAporte: { alignSelf: 'stretch' },
  ayuda: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.textoSuave },
  accion: { alignSelf: 'stretch', marginTop: 16 },

  bloque: { gap: 8 },
  rotuloSeccion: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 11,
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: colores.textoSuave,
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
  filaPresionada: { backgroundColor: colores.arena },
  filaEtiqueta: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.texto },
  filaMonto: {
    fontFamily: fuentes.displaySemi,
    fontSize: 17,
    color: colores.texto,
    ...numerosTabulares,
  },

  // La fila en modo edicion, mismo molde que Mi Plata: mismo tamano que una
  // fila normal, con un borde mas marcado para que se note cual se corrige.
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

  flecha: { fontFamily: fuentes.cuerpoSemi, fontSize: 20, color: colores.rioProfundo },
  cerrar: {
    fontFamily: fuentes.cuerpoSemi,
    fontSize: 15,
    color: colores.terracotaProfunda,
    textAlign: 'center',
    paddingVertical: 8,
  },

  error: { fontFamily: fuentes.cuerpo, fontSize: 14, color: colores.terracotaProfunda },
});
