package com.gastoscompartidos.controlador;

import com.gastoscompartidos.dto.LiquidacionRespuesta;
import com.gastoscompartidos.dto.RegistrarLiquidacionRequest;
import com.gastoscompartidos.dto.SaldoTotalRespuesta;
import com.gastoscompartidos.servicio.LiquidacionServicio;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * El saldo de toda la historia, y las liquidaciones que lo mueven. Vive bajo
 * {@code /saldo} junto al saldo del mes ({@link ResumenControlador}), en un
 * controlador propio porque acá además se escribe (registrar un pago), no
 * solo se lee.
 */
@RestController
@RequestMapping("/saldo")
public class LiquidacionControlador {

    private final LiquidacionServicio servicio;

    public LiquidacionControlador(LiquidacionServicio servicio) {
        this.servicio = servicio;
    }

    /** GET /saldo/total */
    @GetMapping("/total")
    public SaldoTotalRespuesta total() {
        return servicio.saldoTotal();
    }

    /** GET /saldo/liquidaciones */
    @GetMapping("/liquidaciones")
    public List<LiquidacionRespuesta> listar() {
        return servicio.listar();
    }

    /** POST /saldo/liquidaciones: "ya le pagué esto". Devuelve el saldo ya actualizado. */
    @PostMapping("/liquidaciones")
    public SaldoTotalRespuesta registrar(@Valid @RequestBody RegistrarLiquidacionRequest req) {
        return servicio.registrar(req);
    }
}
