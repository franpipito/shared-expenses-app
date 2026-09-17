import { StyleSheet, Text, TextInput, View } from 'react-native';

import { colores } from '../../../tema/colores';
import { fuentes } from '../../../tema/tipografia';

/**
 * Un campo de texto con su etiqueta en versalitas.
 *
 * Estaba definido adentro de `login.tsx` y se subio aca cuando aparecio el
 * registro, que usa exactamente el mismo. Vive en `features/auth/componentes/` y
 * no en `src/componentes/`, siguiendo la regla de `docs/diseno.md`: login y
 * registro son la MISMA feature, asi que esto todavia no es compartido. El dia
 * que un formulario de otra feature lo necesite, ahi sube.
 *
 * Los inputs del alta de gasto no lo usan a proposito: el del monto es de otro
 * tamano y con otra tipografia, porque ahi el numero es el protagonista.
 */
type Props = {
  etiqueta: string;
  valor: string;
  alCambiar: (v: string) => void;
  secreto?: boolean;
  teclado?: 'default' | 'email-address';
  autoComplete?: 'email' | 'current-password' | 'new-password' | 'name' | 'off';
  /** Texto chico debajo del campo: la regla ANTES de tipear, no despues de fallar. */
  ayuda?: string;
  /**
   * `false` para un campo que se compara tal cual, letra por letra, contra un
   * valor fijo -- el codigo de invitacion es el caso real que lo justifico.
   *
   * BUG ENCONTRADO EN VIVO, mostrandole la app a Viole: con el default
   * `sentences`, iOS le puso mayuscula a la primera letra del codigo
   * (`nutriasYpollitos` -> `Nutrias...`) mientras lo tipeaba. El backend
   * compara con `.equals()` exacto, asi que "El codigo de invitacion no es
   * valido" salia con el codigo bien escrito. Nombre y contrasena no lo sufren
   * porque ahi lo que se tipea Y lo que se compara son la misma cosa -- no hay
   * un valor de referencia externo que el teclado pueda desalinear.
   */
  capitalizar?: boolean;
};

export function Campo({
  etiqueta,
  valor,
  alCambiar,
  secreto,
  teclado = 'default',
  autoComplete,
  ayuda,
  capitalizar = true,
}: Props) {
  return (
    <View style={estilos.campo}>
      <Text style={estilos.etiqueta}>{etiqueta}</Text>
      <TextInput
        value={valor}
        onChangeText={alCambiar}
        secureTextEntry={secreto}
        keyboardType={teclado}
        autoComplete={autoComplete}
        // Sin esto iOS pone mayuscula al primer caracter del email (o de
        // cualquier campo con `capitalizar={false}`) y la comparacion contra
        // el valor guardado falla sin que se entienda por que.
        autoCapitalize={teclado === 'email-address' || !capitalizar ? 'none' : 'sentences'}
        autoCorrect={false}
        style={estilos.input}
        placeholderTextColor={colores.textoSuave}
      />
      {ayuda ? <Text style={estilos.ayuda}>{ayuda}</Text> : null}
    </View>
  );
}

const estilos = StyleSheet.create({
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
  ayuda: { fontFamily: fuentes.cuerpo, fontSize: 13, color: colores.textoSuave },
});
