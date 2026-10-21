package cl.duoc.pedidosservice.mensajeria.consumidor.notificaciones;

import cl.duoc.pedidosservice.mensajeria.consumidor.ProcesadorMensajes;
import cl.duoc.pedidosservice.mensajeria.evento.PedidoEvento;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/** Dominio: avisos al cliente. */
@Component
@ConditionalOnProperty(name = "mensajeria.topologia.habilitada", havingValue = "true")
public class NotificacionesConsumidor {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesConsumidor.class);

    private final ProcesadorMensajes procesador;

    public NotificacionesConsumidor(ProcesadorMensajes procesador) {
        this.procesador = procesador;
    }

    @SqsListener(queueNames = "${mensajeria.colas.notificaciones.nombre}",
            id = "notificaciones-listener", acknowledgementMode = "MANUAL")
    public void escuchar(Message<String> mensaje, Acknowledgement ack) {
        procesador.procesar("notificaciones", mensaje, ack, this::notificar);
    }

    private void notificar(PedidoEvento evento) {
        log.info("NOTIFICACION a {}: tu pedido #{} ({}) cambio a {}",
                evento.clienteEmail(), evento.pedidoId(), evento.producto(), evento.estado());
    }
}
