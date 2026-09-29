package com.gastoscompartidos.mail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No manda nada: escribe el mail en el log.
 *
 * Es el enviador de la maquina de desarrollo, y el de produccion mientras no se
 * configure Gmail. En ese segundo caso el codigo de reseteo queda en los logs de
 * Render, que solo ve quien administra el servicio: sirve para ayudar a mano a
 * alguien que no puede entrar, y no expone nada a nadie mas.
 */
public class EnviadorPorLog implements EnviadorDeMails {

    private static final Logger log = LoggerFactory.getLogger(EnviadorPorLog.class);

    @Override
    public void enviar(String para, String asunto, String texto) {
        log.warn("MAIL SIN ENVIAR (no hay proveedor configurado) para={} asunto=\"{}\"\n{}",
                para, asunto, texto);
    }
}
