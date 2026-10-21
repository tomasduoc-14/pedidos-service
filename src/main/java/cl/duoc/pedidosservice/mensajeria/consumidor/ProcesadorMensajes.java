package cl.duoc.pedidosservice.mensajeria.consumidor;

import cl.duoc.pedidosservice.config.mensajeria.MensajeriaProperties;
import cl.duoc.pedidosservice.mensajeria.evento.PedidoEvento;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Flujo comun de todos los consumidores:
 *  - exito: se ejecuta la logica y se confirma (ACK) el mensaje
 *  - duplicado: se confirma sin volver a procesar
 *  - error recuperable: NO se confirma; SQS lo reentrega tras el visibility timeout
 *    y, al agotar maxReceiveCount, lo mueve solo a la DLQ (Redrive Policy)
 *  - error no recuperable: se envia directo a la DLQ y se confirma el original
 * Todo queda registrado con id, intento y causa.
 */
@Component
@ConditionalOnProperty(name = "mensajeria.topologia.habilitada", havingValue = "true")
public class ProcesadorMensajes {

    private static final Logger log = LoggerFactory.getLogger(ProcesadorMensajes.class);

    private final MensajeriaProperties props;
    private final SqsTemplate sqsTemplate;
    private final JsonMapper json;
    private final Set<String> procesados = ConcurrentHashMap.newKeySet();

    public ProcesadorMensajes(MensajeriaProperties props, SqsTemplate sqsTemplate, JsonMapper json) {
        this.props = props;
        this.sqsTemplate = sqsTemplate;
        this.json = json;
    }

    public void procesar(String claveCola, Message<String> mensaje, Acknowledgement ack,
                         Consumer<PedidoEvento> logica) {
        MensajeriaProperties.Cola cola = props.colas().get(claveCola);
        int intento = intentoDe(mensaje);
        String sqsId = valorHeader(mensaje, "messageid");

        PedidoEvento evento;
        try {
            evento = json.readValue(mensaje.getPayload(), PedidoEvento.class);
        } catch (RuntimeException e) {
            enviarADlq(cola, mensaje, sqsId, intento, "JSON invalido: " + e.getMessage());
            ack.acknowledge();
            return;
        }

        String claveDuplicado = claveCola + ":" + evento.eventoId();
        if (procesados.contains(claveDuplicado)) {
            log.info("[{}] DUPLICADO ignorado eventoId={} intento={}", cola.nombre(), evento.eventoId(), intento);
            ack.acknowledge();
            return;
        }

        try {
            logica.accept(evento);
            procesados.add(claveDuplicado);
            ack.acknowledge();
            log.info("[{}] OK eventoId={} tipo={} intento={} (ACK)",
                    cola.nombre(), evento.eventoId(), evento.tipoEvento(), intento);
        } catch (ErrorNoRecuperableException e) {
            enviarADlq(cola, mensaje, evento.eventoId(), intento, e.getMessage());
            ack.acknowledge();
        } catch (RuntimeException e) {
            // ErrorRecuperableException y cualquier error inesperado se tratan como recuperables
            boolean ultimo = intento >= cola.maxReceiveCount();
            log.warn("[{}] REINTENTO eventoId={} sqsId={} intento={}/{} causa={}{}",
                    cola.nombre(), evento.eventoId(), sqsId, intento, cola.maxReceiveCount(),
                    e.getMessage(),
                    ultimo ? " -> ULTIMO INTENTO, SQS lo movera a " + cola.dlq() : "");
            // sin ACK: vuelve a la cola tras el visibility timeout
        }
    }

    private void enviarADlq(MensajeriaProperties.Cola cola, Message<String> mensaje,
                            String id, int intento, String causa) {
        sqsTemplate.send(to -> to.queue(cola.dlq())
                .payload(mensaje.getPayload())
                .header("causa", causa)
                .header("intentoOriginal", String.valueOf(intento))
                .header("colaOrigen", cola.nombre()));
        log.error("[{}] NO RECUPERABLE id={} intento={} causa={} -> enviado a {}",
                cola.nombre(), id, intento, causa, cola.dlq());
    }

    private static int intentoDe(Message<?> mensaje) {
        String valor = valorHeader(mensaje, "approximatereceivecount");
        try {
            return valor == null ? 1 : Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private static String valorHeader(Message<?> mensaje, String fragmento) {
        for (Map.Entry<String, Object> h : mensaje.getHeaders().entrySet()) {
            if (h.getKey().toLowerCase().contains(fragmento)) {
                return String.valueOf(h.getValue());
            }
        }
        return null;
    }
}
