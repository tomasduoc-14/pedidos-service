package cl.duoc.adminservice.service;

import cl.duoc.adminservice.config.AdminProperties;
import cl.duoc.adminservice.dto.AdminDtos.ColaEstado;
import cl.duoc.adminservice.dto.AdminDtos.ColaInfo;
import cl.duoc.adminservice.dto.AdminDtos.CrearColaRequest;
import cl.duoc.adminservice.dto.AdminDtos.ReprocesoResultado;
import cl.duoc.adminservice.dto.AdminDtos.ResultadoInstancia;
import cl.duoc.adminservice.dto.AdminDtos.SuscripcionInfo;
import cl.duoc.adminservice.dto.AdminDtos.SuscripcionRequest;
import cl.duoc.adminservice.dto.AdminDtos.TopicoInfo;
import cl.duoc.adminservice.error.RecursoNoEncontradoException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.Subscription;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;

/**
 * Toda la logica de administracion de colas, topicos, suscripciones, DLQ y consumidores.
 * Los controladores solo llaman a estos metodos de alto nivel.
 */
@Service
public class ColaAdminService {

    private static final Logger log = LoggerFactory.getLogger(ColaAdminService.class);

    /** Atributo por el que filtran las suscripciones (el mismo que publica pedidos-service). */
    private static final String ATRIBUTO_FILTRO = "tipoEvento";
    private static final int MAX_REPROCESO_POR_DEFECTO = 100;
    private static final int MAX_REPROCESO_TOPE = 1000;

    private final SqsAsyncClient sqs;
    private final SnsClient sns;
    private final AdminProperties props;
    private final ConsumidorControlClient consumidores;

    public ColaAdminService(SqsAsyncClient sqs, SnsClient sns, AdminProperties props,
                            ConsumidorControlClient consumidores) {
        this.sqs = sqs;
        this.sns = sns;
        this.props = props;
        this.consumidores = consumidores;
    }

    // ------------------------------------------------------------------ colas

    public ColaInfo crearCola(CrearColaRequest req) {
        AdminProperties.ColaPorDefecto d = props.colaPorDefecto();
        int visibilidad = req.visibilityTimeoutSegundos() != null ? req.visibilityTimeoutSegundos() : d.visibilityTimeoutSegundos();
        int retencion = req.retencionSegundos() != null ? req.retencionSegundos() : d.retencionSegundos();
        int retencionDlq = req.dlqRetencionSegundos() != null ? req.dlqRetencionSegundos() : d.dlqRetencionSegundos();
        int maxReceive = req.maxReceiveCount() != null ? req.maxReceiveCount() : d.maxReceiveCount();
        boolean conDlq = req.conDlq() == null || req.conDlq();

        if (conDlq && retencionDlq < retencion) {
            throw new IllegalArgumentException(
                    "La retencion de la DLQ (" + retencionDlq + "s) debe ser mayor o igual a la de la cola (" + retencion + "s)");
        }

        Map<QueueAttributeName, String> atributos = new HashMap<>();
        atributos.put(QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(visibilidad));
        atributos.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(retencion));

        String nombreDlq = null;
        if (conDlq) {
            nombreDlq = req.nombre() + "-dlq";
            String dlqUrl = crear(nombreDlq, Map.of(
                    QueueAttributeName.MESSAGE_RETENTION_PERIOD, String.valueOf(retencionDlq)));
            atributos.put(QueueAttributeName.REDRIVE_POLICY,
                    "{\"deadLetterTargetArn\":\"" + arnDe(dlqUrl) + "\",\"maxReceiveCount\":\"" + maxReceive + "\"}");
        }
        String url = crear(req.nombre(), atributos);
        log.info("Cola creada: {} (DLQ: {})", req.nombre(), nombreDlq);
        return new ColaInfo(req.nombre(), url, arnDe(url), nombreDlq);
    }

    public void eliminarCola(String nombre) {
        String url = urlDe(nombre);
        esperar(sqs.deleteQueue(r -> r.queueUrl(url)));
        log.info("Cola eliminada: {}", nombre);
    }

    public List<String> listarColas() {
        return esperar(sqs.listQueues()).queueUrls().stream()
                .map(ColaAdminService::nombreDe)
                .sorted()
                .collect(Collectors.toList());
    }

    public ColaEstado estadoCola(String nombre) {
        String url = urlDe(nombre);
        Map<QueueAttributeName, String> a = esperar(sqs.getQueueAttributes(
                r -> r.queueUrl(url).attributeNames(QueueAttributeName.ALL))).attributes();
        return new ColaEstado(
                nombre,
                a.get(QueueAttributeName.QUEUE_ARN),
                entero(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)),
                entero(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)),
                entero(a.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_DELAYED)),
                entero(a.get(QueueAttributeName.VISIBILITY_TIMEOUT)),
                entero(a.get(QueueAttributeName.MESSAGE_RETENTION_PERIOD)),
                a.get(QueueAttributeName.REDRIVE_POLICY));
    }

    /** Vacia la cola (sirve tambien para purgar una DLQ). AWS permite una purga por minuto y cola. */
    public void purgarCola(String nombre) {
        String url = urlDe(nombre);
        esperar(sqs.purgeQueue(r -> r.queueUrl(url)));
        log.info("Cola purgada: {}", nombre);
    }

    // -------------------------------------------------------------------- DLQ

    /** Mueve mensajes de la DLQ a la cola de origen para que se procesen de nuevo. */
    public ReprocesoResultado reprocesarDlq(String nombreDlq, Integer max) {
        String dlqUrl = urlDe(nombreDlq);
        List<String> origenes = esperar(sqs.listDeadLetterSourceQueues(r -> r.queueUrl(dlqUrl))).queueUrls();
        if (origenes.isEmpty()) {
            throw new IllegalArgumentException("La cola '" + nombreDlq + "' no es DLQ de ninguna cola");
        }
        String destino = origenes.get(0);
        int limite = Math.min(max == null ? MAX_REPROCESO_POR_DEFECTO : max, MAX_REPROCESO_TOPE);

        int movidos = 0;
        while (movidos < limite) {
            List<Message> lote = esperar(sqs.receiveMessage(
                    r -> r.queueUrl(dlqUrl).maxNumberOfMessages(10).waitTimeSeconds(1))).messages();
            if (lote.isEmpty()) {
                break;
            }
            for (Message m : lote) {
                esperar(sqs.sendMessage(r -> r.queueUrl(destino).messageBody(m.body())));
                esperar(sqs.deleteMessage(r -> r.queueUrl(dlqUrl).receiptHandle(m.receiptHandle())));
                movidos++;
                log.info("Reprocesado messageId={} de {} -> {}", m.messageId(), nombreDlq, nombreDe(destino));
            }
        }
        return new ReprocesoResultado(nombreDlq, nombreDe(destino), movidos);
    }

    // ---------------------------------------------------------------- topicos

    public TopicoInfo crearTopico(String nombre) {
        String arn = sns.createTopic(r -> r.name(nombre)).topicArn();
        log.info("Topico creado: {}", arn);
        return new TopicoInfo(nombre, arn);
    }

    public void eliminarTopico(String nombre) {
        String arn = arnTopico(nombre);
        sns.deleteTopic(r -> r.topicArn(arn));
        log.info("Topico eliminado: {}", arn);
    }

    public List<TopicoInfo> listarTopicos() {
        List<TopicoInfo> lista = new ArrayList<>();
        for (Topic t : sns.listTopicsPaginator().topics()) {
            String arn = t.topicArn();
            lista.add(new TopicoInfo(arn.substring(arn.lastIndexOf(':') + 1), arn));
        }
        return lista;
    }

    // ---------------------------------------------------------- suscripciones

    /** Suscribe una cola al topico, con filtro por tipoEvento (opcional) y raw message delivery. */
    public SuscripcionInfo suscribir(String topico, SuscripcionRequest req) {
        String topicArn = arnTopico(topico);
        String colaUrl = urlDe(req.cola());
        String colaArn = arnDe(colaUrl);

        esperar(sqs.setQueueAttributes(r -> r.queueUrl(colaUrl)
                .attributes(Map.of(QueueAttributeName.POLICY, politicaParaSns(colaArn, topicArn)))));

        Map<String, String> atributos = new HashMap<>();
        atributos.put("RawMessageDelivery", String.valueOf(req.rawMessageDelivery() == null || req.rawMessageDelivery()));
        if (req.filtroTipoEvento() != null && !req.filtroTipoEvento().isEmpty()) {
            String valores = req.filtroTipoEvento().stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(","));
            atributos.put("FilterPolicy", "{\"" + ATRIBUTO_FILTRO + "\":[" + valores + "]}");
        }
        String arn = sns.subscribe(r -> r.topicArn(topicArn).protocol("sqs").endpoint(colaArn)
                .attributes(atributos).returnSubscriptionArn(true)).subscriptionArn();
        log.info("Suscripcion creada: {} -> {} (filtro={})", topico, req.cola(), req.filtroTipoEvento());
        return new SuscripcionInfo(arn, "sqs", colaArn);
    }

    public List<SuscripcionInfo> listarSuscripciones(String topico) {
        String topicArn = arnTopico(topico);
        List<SuscripcionInfo> lista = new ArrayList<>();
        for (Subscription s : sns.listSubscriptionsByTopicPaginator(r -> r.topicArn(topicArn)).subscriptions()) {
            lista.add(new SuscripcionInfo(s.subscriptionArn(), s.protocol(), s.endpoint()));
        }
        return lista;
    }

    public void eliminarSuscripcion(String arn) {
        sns.unsubscribe(r -> r.subscriptionArn(arn));
        log.info("Suscripcion eliminada: {}", arn);
    }

    // ------------------------------------------------------------ consumidores

    public List<ResultadoInstancia> listarConsumidores() {
        return consumidores.listar();
    }

    public List<ResultadoInstancia> pausarConsumidor(String listenerId) {
        log.info("Pausando consumidor {}", listenerId);
        return consumidores.accion(listenerId, "pausar");
    }

    public List<ResultadoInstancia> reanudarConsumidor(String listenerId) {
        log.info("Reanudando consumidor {}", listenerId);
        return consumidores.accion(listenerId, "reanudar");
    }

    // ---------------------------------------------------------------- privados

    private String crear(String nombre, Map<QueueAttributeName, String> atributos) {
        return esperar(sqs.createQueue(r -> r.queueName(nombre).attributes(atributos))).queueUrl();
    }

    private String urlDe(String nombre) {
        try {
            return esperar(sqs.getQueueUrl(r -> r.queueName(nombre))).queueUrl();
        } catch (QueueDoesNotExistException e) {
            throw new RecursoNoEncontradoException("La cola '" + nombre + "' no existe");
        }
    }

    private String arnDe(String queueUrl) {
        return esperar(sqs.getQueueAttributes(r -> r.queueUrl(queueUrl).attributeNames(QueueAttributeName.QUEUE_ARN)))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
    }

    private String arnTopico(String nombre) {
        for (Topic t : sns.listTopicsPaginator().topics()) {
            if (t.topicArn().endsWith(":" + nombre)) {
                return t.topicArn();
            }
        }
        throw new RecursoNoEncontradoException("El topico '" + nombre + "' no existe");
    }

    private static String nombreDe(String queueUrl) {
        return queueUrl.substring(queueUrl.lastIndexOf('/') + 1);
    }

    private static int entero(String valor) {
        try {
            return valor == null ? 0 : Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String politicaParaSns(String colaArn, String topicArn) {
        return """
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow",
                "Principal":{"Service":"sns.amazonaws.com"},
                "Action":"sqs:SendMessage","Resource":"%s",
                "Condition":{"ArnEquals":{"aws:SourceArn":"%s"}}}]}
                """.formatted(colaArn, topicArn);
    }

    /** Espera el resultado asincrono y relanza la excepcion original de AWS (no la envoltura). */
    private static <T> T esperar(CompletableFuture<T> futuro) {
        try {
            return futuro.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException causa) {
                throw causa;
            }
            throw e;
        }
    }
}
