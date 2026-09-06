package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.ResumenRespuesta;
import com.gastoscompartidos.dto.SaldoRespuesta;
import com.gastoscompartidos.servicio.ResumenServicio;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

/**
 * Los dos agregados, uno por seccion de la app:
 *
 *   /gastos/resumen -> seccion PERSONAL (cuanto gaste yo, cuanto fue evitable,
 *                      y el animo de la nutria)
 *   /saldo          -> seccion PAREJA (quien le debe a quien este mes)
 *
 * Estan juntos en un controlador porque son la misma idea (mirar el mes desde
 * arriba) aunque cuelguen de rutas distintas.
 */
@RestController
public class ResumenControlador {

    private final ResumenServicio servicio;

    public ResumenControlador(ResumenServicio servicio) {
        this.servicio = servicio;
    }

    /** GET /gastos/resumen?mes=2026-09 */
    @GetMapping("/gastos/resumen")
    public ResumenRespuesta resumen(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return servicio.resumen(mes);
    }

    /** GET /saldo?mes=2026-09 */
    @GetMapping("/saldo")
    public SaldoRespuesta saldo(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth mes) {
        return servicio.saldo(mes);
    }
}
