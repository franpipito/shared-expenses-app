import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ErrorDeApi } from '../../src/api/cliente';
import type { GastoRespuesta, PozoRespuesta } from '../../src/api/tipos';
import { formatearMonto } from '../../src/componentes/Monto';
import { useSesion } from '../../src/features/auth/sesion';
import { FilaGasto } from '../../src/features/gastos/componentes/FilaGasto';
import { traerGastosDelPozo, traerPozos } from '../../src/features/vaquita/api';
import { colores } from '../../src/tema/colores';
import { fuentes, numerosTabulares } from '../../src/tema/tipografia';
import { Cargando } from '../_layout';

/**
 * Un viaje ya cerrado, con sus gastos.
 *
 * POR QUE EXISTE: los gastos de una vaquita cerrada quedaban inalcanzables. No
 * salen en la lista del mes (los filtra `sinPozo()`), y `/pozos/activo` deja de
 * devolver la vaquita apenas se cierra. O sea que el permiso de corregirlos --
 * agregado a proposito para que un cero de mas visto al volver del viaje no
 * quedara para siempre -- no tenia ninguna pantalla desde la cual ejercerse.
 *
 * Es de solo lectura salvo por lo unico que importa: **las filas se tocan para
 * editar el gasto.**
 */
export default function Viaje() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const { usuario } = useSesion();
  const router = useRouter();
  const insets = useSafeAreaInsets();

  const [pozo, setPozo] = useState<PozoRespuesta | null>(null);
  const [gastos, setGastos] = useState<GastoRespuesta[]>([]);
  const [error, setError] = useState<string | null>(null);

  const cargar = useCallback(async () => {
    try {
      // No hay `GET /pozos/{id}`, asi que se busca en el listado. Son unos pocos
      // viajes: un endpoint mas para esto seria superficie de API sin valor.
      const todos = await traerPozos();
      const elegido = todos.find((p) => p.id === id) ?? null;
      setPozo(elegido);
      setGastos(elegido ? await traerGastosDelPozo(elegido.id) : []);
    } catch (e) {
      setError(e instanceof ErrorDeApi ? e.message : 'No se pudo traer el viaje.');
    }
  }, [id]);

  useEffect(() => {
    void cargar();
  }, [cargar]);

  if (error) {
    return (
      <View style={estilos.centrada}>
        <Text style={estilos.error}>{error}</Text>
        <Pressable onPress={() => router.back()} accessibilityRole="button" hitSlop={12}>
          <Text style={estilos.volver}>Volver</Text>
        </Pressable>
      </View>
    );
  }

  if (!pozo) return <Cargando />;

  const enRojo = pozo.restante < 0;

  return (
    <ScrollView
      style={estilos.pantalla}
      contentContainerStyle={[
        estilos.contenido,
        { paddingTop: insets.top + 16, paddingBottom: insets.bottom + 24 },
      ]}
    >
      <View style={estilos.encabezado}>
        <View style={estilos.encabezadoTexto}>
          <Text style={estilos.seccion}>Viaje terminado</Text>
          <Text style={estilos.titulo}>{pozo.nombre}</Text>
        </View>
        <Pressable onPress={() => router.back()} hitSlop={12} accessibilityRole="button">
          <Text style={estilos.volver}>Volver</Text>
        </Pressable>
      </View>

      <View style={estilos.tarjeta}>
        <Text style={estilos.rotulo}>{enRojo ? 'Se pasaron por' : 'Sobro'}</Text>
        <Text style={estilos.numero} numberOfLines={1} adjustsFontSizeToFit>
          {formatearMonto(Math.abs(pozo.restante))}
        </Text>
        <Text style={estilos.detalle}>
          Pusieron {formatearMonto(pozo.aportado)} · gastaron {formatearMonto(pozo.gastado)}
        </Text>
      </View>

      <View style={estilos.bloque}>
        <Text style={estilos.rotuloSeccion}>Quien puso que</Text>
        {pozo.porPersona.map((p) => (
          <View key={p.usuarioId} style={estilos.fila}>
            <Text style={estilos.filaEtiqueta}>{p.nombre}</Text>
            <Text style={estilos.filaMonto}>{formatearMonto(p.total)}</Text>
          </View>
        ))}
      </View>

      <View style={estilos.bloque}>
        <Text style={estilos.rotuloSeccion}>Los gastos del viaje</Text>
        {/*
          Tocables, que es todo el punto de esta pantalla: es el unico lugar
          desde el cual se puede corregir un gasto de un viaje ya cerrado.
        */}
        {gastos.map((g) => (
          <FilaGasto
            key={g.id}
            gasto={g}
            idUsuarioActual={usuario?.id}
            alTocar={() => router.push(`/gasto/${g.id}`)}
          />
        ))}
      </View>
    </ScrollView>
  );
}

const estilos = StyleSheet.create({
  pantalla: { flex: 1, backgroundColor: colores.fondo },
  contenido: { paddingHorizontal: 20, gap: 20 },
  centrada: {
    flex: 1,
    backgroundColor: colores.fondo,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 16,
    padding: 24,
  },
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
  numero: {
    fontFamily: fuentes.displayBold,
    fontSize: 36,
    color: colores.rioProfundo,
    marginTop: 4,
    ...numerosTabulares,
  },
  detalle: {
    fontFamily: fuentes.cuerpo,
    fontSize: 14,
    color: colores.textoSuave,
    marginTop: 6,
    textAlign: 'center',
    ...numerosTabulares,
  },
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
  filaEtiqueta: { fontFamily: fuentes.cuerpo, fontSize: 15, color: colores.texto },
  filaMonto: {
    fontFamily: fuentes.displaySemi,
    fontSize: 17,
    color: colores.texto,
    ...numerosTabulares,
  },
  error: {
    fontFamily: fuentes.cuerpo,
    fontSize: 15,
    color: colores.terracotaProfunda,
    textAlign: 'center',
  },
});
