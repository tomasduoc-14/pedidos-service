package cl.duoc.pedidosservice.mensajeria.publicador;

import cl.duoc.pedidosservice.mensajeria.evento.PedidoEvento;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Activo cuando la mensajeria esta apagada (por ejemplo, desarrollo local con H2). */
@Component
@ConditionalOnProperty(name = "mensajeria.topologia.habilitada", havingValue = "false", matchIfMissing = true)
public class NoopPedidoEventoPublicador implements PedidoEventoPublicador {

    private static final Logger log = LoggerFactory.getLogger(NoopPedidoEventoPublicador.class);

    @Override
    public void publicar(PedidoEvento evento) {
        log.debug("Mensajeria deshabilitada, evento descartado: {}", evento.eventoId());
    }
}
