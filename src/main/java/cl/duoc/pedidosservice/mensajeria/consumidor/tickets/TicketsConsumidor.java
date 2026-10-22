package cl.duoc.pedidosservice.mensajeria.consumidor.tickets;

import cl.duoc.pedidosservice.mensajeria.consumidor.ErrorNoRecuperableException;
import cl.duoc.pedidosservice.mensajeria.consumidor.ErrorRecuperableException;
import cl.duoc.pedidosservice.mensajeria.consumidor.ProcesadorMensajes;
import cl.duoc.pedidosservice.mensajeria.evento.PedidoEvento;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/** Dominio: generacion de tickets/documentos. */
@Component
@ConditionalOnProperty(name = "mensajeria.topologia.habilitada", havingValue = "true")
public class TicketsConsumidor {

    private static final Logger log = LoggerFactory.getLogger(TicketsConsumidor.class);

    private final ProcesadorMensajes procesador;
    private final boolean simularFallos;

    public TicketsConsumidor(ProcesadorMensajes procesador,
                             @Value("${mensajeria.simular-fallos:true}") boolean simularFallos) {
        this.procesador = procesador;
        this.simularFallos = simularFallos;
    }

    @SqsListener(queueNames = "${mensajeria.colas.tickets.nombre}",
            id = "tickets-listener", acknowledgementMode = "MANUAL")
    public void escuchar(Message<String> mensaje, Acknowledgement ack) {
        procesador.procesar("tickets", mensaje, ack, this::generarTicket);
    }

    private void generarTicket(PedidoEvento evento) {
        // Gancho de demostracion: con MENSAJERIA_SIMULAR_FALLOS=false ya no falla (sirve para reprocesar la DLQ).
        if (simularFallos && "FALLO_RECUPERABLE".equals(evento.producto())) {
            throw new ErrorRecuperableException("servicio de documentos no disponible (simulado)");
        }
        if (simularFallos && "FALLO_NO_RECUPERABLE".equals(evento.producto())) {
            throw new ErrorNoRecuperableException("datos del pedido invalidos (simulado)");
        }
        log.info("TICKET TCK-{} generado para {} x{} ({})",
                evento.pedidoId(), evento.producto(), evento.cantidad(), evento.clienteEmail());
    }
}
