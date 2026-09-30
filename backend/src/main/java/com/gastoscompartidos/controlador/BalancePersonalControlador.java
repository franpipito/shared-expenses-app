package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.BalancePersonalRespuesta;
import com.gastoscompartidos.dto.RegistrarIngresoRequest;
import com.gastoscompartidos.servicio.BalancePersonalServicio;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Mi Plata" (sección 2.3b): cuánta plata declaró tener una persona, menos
 * sus gastos personales. El nombre de ruta es el concepto técnico; "Mi
 * Plata" queda como copy de la UI únicamente -- mismo criterio que "Saldar
 * cuentas" (UI) vs {@code Liquidacion}/{@code /saldo/...} (código).
 */
@RestController
@RequestMapping("/balance-personal")
public class BalancePersonalControlador {

    private final BalancePersonalServicio servicio;

    public BalancePersonalControlador(BalancePersonalServicio servicio) {
        this.servicio = servicio;
    }

    /** GET /balance-personal */
    @GetMapping
    public BalancePersonalRespuesta ver() {
        return servicio.ver();
    }

    /** POST /balance-personal/ingresos. Devuelve el balance ya actualizado. */
    @PostMapping("/ingresos")
    public BalancePersonalRespuesta agregarIngreso(@Valid @RequestBody RegistrarIngresoRequest req) {
        return servicio.agregarIngreso(req);
    }
}
