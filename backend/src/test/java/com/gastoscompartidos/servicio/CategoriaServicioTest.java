package com.gastoscompartidos.servicio;

import com.gastoscompartidos.dto.CategoriaRespuesta;
import com.gastoscompartidos.dto.GuardarCategoriaRequest;
import com.gastoscompartidos.error.RecursoNoEncontradoException;
import com.gastoscompartidos.error.ReglaDeNegocioException;
import com.gastoscompartidos.modelo.Categoria;
import com.gastoscompartidos.modelo.ReferenciaCategoria;
import com.gastoscompartidos.modelo.Usuario;
import com.gastoscompartidos.repositorio.CategoriaRepositorio;
import com.gastoscompartidos.repositorio.GastoRepositorio;
import com.gastoscompartidos.seguridad.UsuarioActual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de las categorias por usuario (seccion 2.9), sin base de datos.
 *
 * Lo que un mock NO prueba: que el indice parcial {usuario_id, nombre} exista
 * de verdad, que dos personas del mismo grupo puedan tener cada una su "cafe",
 * ni que el filtro de `GastoConsultas.actualizarCategoria` matchee los gastos
 * correctos. Eso lo cubre el smoke test contra Mongo real.
 */
class CategoriaServicioTest {

    private static final String GRUPO = "grupo-1";
    private static final String YO = "u-franco";

    private CategoriaRepositorio categorias;
    private GastoRepositorio gastos;
    private CategoriaServicio servicio;

    @BeforeEach
    void preparar() {
        categorias = mock(CategoriaRepositorio.class);
        gastos = mock(GastoRepositorio.class);
        UsuarioActual usuarioActual = mock(UsuarioActual.class);
        servicio = new CategoriaServicio(categorias, gastos, usuarioActual);

        Usuario franco = new Usuario("Franco", "franco@local", "hash", GRUPO);
        escribirCampo(franco, "id", YO);
        when(usuarioActual.requerido()).thenReturn(franco);
        // save() devuelve lo que le pasaron, como Mongo.
        when(categorias.save(any(Categoria.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Nested
    @DisplayName("Listar")
    class Listar {

        @Test
        @DisplayName("se piden por usuario, no por grupo: las de la otra persona no aparecen")
        void soloLasMias() {
            when(categorias.findByUsuarioIdOrderByNombreAsc(YO)).thenReturn(List.of(
                    categoria("cat-1", "cafe", "coffee")));

            List<CategoriaRespuesta> r = servicio.listar();

            assertThat(r).hasSize(1);
            assertThat(r.get(0).nombre()).isEqualTo("cafe");
        }
    }

    @Nested
    @DisplayName("Crear")
    class Crear {

        @Test
        @DisplayName("queda a nombre de quien la crea")
        void quedaANombreDeQuienLaCrea() {
            when(categorias.findByUsuarioIdOrderByNombreAsc(YO)).thenReturn(List.of());

            CategoriaRespuesta r = servicio.crear(new GuardarCategoriaRequest(" Netflix ", "📺"));

            assertThat(r.nombre()).isEqualTo("Netflix");
            assertThat(r.icono()).isEqualTo("📺");

            ArgumentCaptor<Categoria> captura = ArgumentCaptor.forClass(Categoria.class);
            verify(categorias).save(captura.capture());
            assertThat(captura.getValue().getUsuarioId()).isEqualTo(YO);
        }

        @Test
        @DisplayName("un nombre que ya tengo se rechaza, sin importar mayusculas")
        void nombreRepetidoSeRechaza() {
            when(categorias.findByUsuarioIdOrderByNombreAsc(YO)).thenReturn(List.of(
                    categoria("cat-1", "Netflix", "📺")));

            assertThatThrownBy(() -> servicio.crear(new GuardarCategoriaRequest("netflix", "tag")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Ya tenés");

            verify(categorias, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Editar")
    class Editar {

        @Test
        @DisplayName("renombrar propaga el snapshot a los gastos, con el nombre VIEJO para encontrarlos")
        void renombrarPropaga() {
            Categoria cafe = categoria("cat-1", "cafe", "coffee");
            when(categorias.findByIdAndUsuarioId("cat-1", YO)).thenReturn(Optional.of(cafe));
            when(categorias.findByUsuarioIdOrderByNombreAsc(YO)).thenReturn(List.of(cafe));

            CategoriaRespuesta r = servicio.editar("cat-1", new GuardarCategoriaRequest("Cafetería", "☕"));

            assertThat(r.nombre()).isEqualTo("Cafetería");
            assertThat(r.icono()).isEqualTo("☕");
            verify(gastos).actualizarCategoria(GRUPO, YO, "cafe",
                    new ReferenciaCategoria("cat-1", "Cafetería", "☕"));
        }

        @Test
        @DisplayName("los gastos se actualizan ANTES que la categoria: si falla en el medio, se reintenta")
        void gastosPrimero() {
            Categoria cafe = categoria("cat-1", "cafe", "coffee");
            when(categorias.findByIdAndUsuarioId("cat-1", YO)).thenReturn(Optional.of(cafe));
            when(categorias.findByUsuarioIdOrderByNombreAsc(YO)).thenReturn(List.of(cafe));

            servicio.editar("cat-1", new GuardarCategoriaRequest("Cafetería", "☕"));

            InOrder orden = inOrder(gastos, categorias);
            orden.verify(gastos).actualizarCategoria(any(), any(), any(), any());
            orden.verify(categorias).save(cafe);
        }

        @Test
        @DisplayName("cambiarle solo el icono, o las mayusculas, no choca contra si misma")
        void noChocaContraSiMisma() {
            Categoria cafe = categoria("cat-1", "cafe", "coffee");
            when(categorias.findByIdAndUsuarioId("cat-1", YO)).thenReturn(Optional.of(cafe));
            when(categorias.findByUsuarioIdOrderByNombreAsc(YO)).thenReturn(List.of(cafe));

            CategoriaRespuesta r = servicio.editar("cat-1", new GuardarCategoriaRequest("Cafe", "☕"));

            assertThat(r.nombre()).isEqualTo("Cafe");
        }

        @Test
        @DisplayName("no se puede renombrar al nombre de OTRA categoria mia, y no se toca ningun gasto")
        void chocaContraOtra() {
            Categoria cafe = categoria("cat-1", "cafe", "coffee");
            when(categorias.findByIdAndUsuarioId("cat-1", YO)).thenReturn(Optional.of(cafe));
            when(categorias.findByUsuarioIdOrderByNombreAsc(YO)).thenReturn(List.of(
                    cafe, categoria("cat-2", "Uber", "car")));

            assertThatThrownBy(() -> servicio.editar("cat-1", new GuardarCategoriaRequest("uber", "car")))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("Ya tenés");

            verify(gastos, never()).actualizarCategoria(any(), any(), any(), any());
            verify(categorias, never()).save(any());
        }

        @Test
        @DisplayName("una categoria ajena o inexistente da 404, y no se toca ningun gasto")
        void ajenaDa404() {
            when(categorias.findByIdAndUsuarioId("cat-de-viole", YO)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servicio.editar("cat-de-viole", new GuardarCategoriaRequest("x", "y")))
                    .isInstanceOf(RecursoNoEncontradoException.class);

            verify(gastos, never()).actualizarCategoria(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Borrar")
    class Borrar {

        @Test
        @DisplayName("se borra solo si es mia, y no toca los gastos: conservan el snapshot")
        void borraLaMia() {
            when(categorias.countByUsuarioId(YO)).thenReturn(2L);
            when(categorias.deleteByIdAndUsuarioId("cat-1", YO)).thenReturn(1L);

            servicio.borrar("cat-1");

            verify(categorias).deleteByIdAndUsuarioId("cat-1", YO);
            verify(gastos, never()).actualizarCategoria(any(), any(), any(), any());
        }

        @Test
        @DisplayName("no deja borrar la ultima: el formulario de alta se quedaria sin chip")
        void noDejaBorrarLaUltima() {
            when(categorias.countByUsuarioId(YO)).thenReturn(1L);

            assertThatThrownBy(() -> servicio.borrar("cat-1"))
                    .isInstanceOf(ReglaDeNegocioException.class)
                    .hasMessageContaining("última categoría");

            verify(categorias, never()).deleteByIdAndUsuarioId(any(), any());
        }

        @Test
        @DisplayName("una categoria ajena o inexistente da 404, sin distinguir cual")
        void ajenaOInexistenteDa404() {
            when(categorias.countByUsuarioId(YO)).thenReturn(2L);
            when(categorias.deleteByIdAndUsuarioId("cat-de-viole", YO)).thenReturn(0L);

            assertThatThrownBy(() -> servicio.borrar("cat-de-viole"))
                    .isInstanceOf(RecursoNoEncontradoException.class);
        }
    }

    private static Categoria categoria(String id, String nombre, String icono) {
        Categoria c = new Categoria(YO, nombre, icono);
        escribirCampo(c, "id", id);
        return c;
    }

    private static void escribirCampo(Object objeto, String campo, Object valor) {
        try {
            Field f = objeto.getClass().getDeclaredField(campo);
            f.setAccessible(true);
            f.set(objeto, valor);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
