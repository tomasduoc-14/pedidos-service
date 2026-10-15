package cl.duoc.pedidosservice.config.mensajeria;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

/**
 * Declara la topologia de mensajeria: topico, colas, DLQ (Redrive Policy) y
 * suscripciones con filtro. No contiene logica de negocio.
 */
@Component
@ConditionalOnProperty(name = "mensajeria.topologia.habilitada", havingValue = "true")
public class MensajeriaTopologia {

    private static final Logger log = LoggerFactory.getLogger(MensajeriaTopologia.class);

    private final MensajeriaProperties props;
    private final SqsAsyncClient sqs;
    private final SnsClient sns;

    public MensajeriaTopologia(MensajeriaProperties props, SqsAsyncClient sqs, SnsClient sns) {
        this.props = props;
        this.sqs = sqs;
        this.sns = sns;
    }

    @PostConstruct
    public void declarar() {
        String topicArn = sns.createTopic(
                CreateTopicRequest.builder().name(props.topico().nombre()).build()).topicArn();
        log.info("Topico SNS listo: {}", topicArn);
        props.colas().forEach((clave, cola) -> declararCola(clave, cola, topicArn));
    }

    private void declararCola(String clave, MensajeriaProperties.Cola cola, String topicArn) {
        String dlqUrl = crearCola(cola.dlq(), Map.of(
                QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(cola.dlqRetencionSegundos())));
        String dlqArn = arnDe(dlqUrl);

        String redrive = "{\"deadLetterTargetArn\":\"" + dlqArn
                + "\",\"maxReceiveCount\":\"" + cola.maxReceiveCount() + "\"}";
        String colaUrl = crearCola(cola.nombre(), Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(cola.visibilityTimeoutSegundos()),
                QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(cola.retencionSegundos()),
                QueueAttributeName.REDRIVE_POLICY, redrive));
        String colaArn = arnDe(colaUrl);

        sqs.setQueueAttributes(SetQueueAttributesRequest.builder()
                .queueUrl(colaUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, politicaParaSns(colaArn, topicArn)))
                .build()).join();

        Map<String, String> atributos = new HashMap<>();
        atributos.put("RawMessageDelivery", "true");
        if (!cola.filtroTipoEvento().isEmpty()) {
            atributos.put("FilterPolicy", filtro(cola));
        }
        sns.subscribe(SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(colaArn)
                .attributes(atributos)
                .returnSubscriptionArn(true)
                .build());

        log.info("Cola '{}' lista ({}), DLQ '{}', filtro={}",
                cola.nombre(), clave, cola.dlq(), cola.filtroTipoEvento());
    }

    private String crearCola(String nombre, Map<QueueAttributeName, String> atributos) {
        return sqs.createQueue(CreateQueueRequest.builder()
                .queueName(nombre)
                .attributes(atributos)
                .build()).join().queueUrl();
    }

    private String arnDe(String queueUrl) {
        return sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(queueUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build()).join().attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    private String filtro(MensajeriaProperties.Cola cola) {
        String valores = cola.filtroTipoEvento().stream()
                .map(v -> "\"" + v + "\"")
                .collect(Collectors.joining(","));
        return "{\"" + MensajeriaConstantes.ATRIBUTO_TIPO_EVENTO + "\":[" + valores + "]}";
    }

    private String politicaParaSns(String colaArn, String topicArn) {
        return """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow",
                "Principal":{"Service":"sns.amazonaws.com"},
                "Action":"sqs:SendMessage","Resource":"%s",
                "Condition":{"ArnEquals":{"aws:SourceArn":"%s"}}}]}
                """.formatted(colaArn, topicArn);
    }
}
