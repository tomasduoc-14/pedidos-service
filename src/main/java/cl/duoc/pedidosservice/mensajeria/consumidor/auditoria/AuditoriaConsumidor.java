package cl.duoc.pedidosservice.mensajeria.consumidor.auditoria;

import cl.duoc.pedidosservice.mensajeria.consumidor.ProcesadorMensajes;
import cl.duoc.pedidosservice.mensajeria.evento.PedidoEvento;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/** Dominio: registro de auditoria de todos los eventos. */
@Component
@ConditionalOnProperty(name = "mensajeria.topologia.habilitada", havingValue = "true")
public class AuditoriaConsumidor {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaConsumidor.class);

    private final ProcesadorMensajes procesador;

    public AuditoriaConsumidor(ProcesadorMensajes procesador) {
        this.procesador = procesador;
    }

    @SqsListener(queueNames = "${mensajeria.colas.auditoria.nombre}",
            id = "auditoria-listener", acknowledgementMode = "MANUAL")
    public void escuchar(Message<String> mensaje, Acknowledgement ack) {
        procesador.procesar("auditoria", mensaje, ack, this::auditar);
    }

    private void auditar(PedidoEvento evento) {
        log.info("AUDITORIA evento={} tipo={} pedido={} estado={} fecha={}",
                evento.eventoId(), evento.tipoEvento(), evento.pedidoId(), evento.estado(), evento.fecha());
    }
}
