package cl.duoc.pedidosservice.mensajeria.publicador;

import cl.duoc.pedidosservice.config.mensajeria.MensajeriaConstantes;
import cl.duoc.pedidosservice.config.mensajeria.MensajeriaProperties;
import cl.duoc.pedidosservice.mensajeria.evento.PedidoEvento;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "mensajeria.topologia.habilitada", havingValue = "true")
public class SnsPedidoEventoPublicador implements PedidoEventoPublicador {

    private static final Logger log = LoggerFactory.getLogger(SnsPedidoEventoPublicador.class);

    private final SnsClient sns;
    private final JsonMapper json;
    private final MensajeriaProperties props;
    private volatile String topicArn;

    public SnsPedidoEventoPublicador(SnsClient sns, JsonMapper json, MensajeriaProperties props) {
        this.sns = sns;
        this.json = json;
        this.props = props;
    }

    @Override
    public void publicar(PedidoEvento evento) {
        try {
            String cuerpo = json.writeValueAsString(evento);
            sns.publish(PublishRequest.builder()
                    .topicArn(topicArn())
                    .message(cuerpo)
                    .messageAttributes(Map.of(
                            MensajeriaConstantes.ATRIBUTO_TIPO_EVENTO,
                            MessageAttributeValue.builder()
                                    .dataType("String")
                                    .stringValue(evento.tipoEvento())
                                    .build()))
                    .build());
            log.info("Evento publicado: tipo={} eventoId={} pedidoId={}",
                    evento.tipoEvento(), evento.eventoId(), evento.pedidoId());
        } catch (RuntimeException e) {
            // La mensajeria no debe romper la operacion de negocio del pedido.
            log.error("No se pudo publicar eventoId={} tipo={} causa={}",
                    evento.eventoId(), evento.tipoEvento(), e.getMessage());
        }
    }

    private String topicArn() {
        if (topicArn == null) {
            topicArn = sns.createTopic(
                    CreateTopicRequest.builder().name(props.topico().nombre()).build()).topicArn();
        }
        return topicArn;
    }
}
