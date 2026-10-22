package cl.duoc.pedidosservice.mensajeria.control;

import io.awspring.cloud.sqs.listener.MessageListenerContainer;
import io.awspring.cloud.sqs.listener.MessageListenerContainerRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint INTERNO para que el microservicio administrador pause y reanude consumidores.
 * No se expone por el API Gateway (solo /api/pedidos) y exige el header X-Admin-Key.
 */
@RestController
@RequestMapping("/internal/consumidores")
@ConditionalOnProperty(name = "mensajeria.topologia.habilitada", havingValue = "true")
public class ConsumidorControlController {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorControlController.class);

    private final MessageListenerContainerRegistry registro;
    private final String clave;

    public ConsumidorControlController(MessageListenerContainerRegistry registro,
                                       @Value("${admin.api-key:}") String clave) {
        this.registro = registro;
        this.clave = clave;
    }

    @GetMapping
    public ResponseEntity<?> listar(@RequestHeader(value = "X-Admin-Key", required = false) String header) {
        if (!autorizado(header)) {
            return prohibido();
        }
        List<Map<String, Object>> lista = registro.getListenerContainers().stream()
                .map(c -> estado(c, c.isRunning()))
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PostMapping("/{id}/pausar")
    public ResponseEntity<?> pausar(@PathVariable String id,
                                    @RequestHeader(value = "X-Admin-Key", required = false) String header) {
        if (!autorizado(header)) {
            return prohibido();
        }
        MessageListenerContainer<?> contenedor = registro.getContainerById(id);
        if (contenedor == null) {
            return noEncontrado(id);
        }
        contenedor.stop();
        log.warn("Consumidor '{}' PAUSADO por el administrador", id);
        return ResponseEntity.ok(estado(contenedor, contenedor.isRunning()));
    }

    @PostMapping("/{id}/reanudar")
    public ResponseEntity<?> reanudar(@PathVariable String id,
                                      @RequestHeader(value = "X-Admin-Key", required = false) String header) {
        if (!autorizado(header)) {
            return prohibido();
        }
        MessageListenerContainer<?> contenedor = registro.getContainerById(id);
        if (contenedor == null) {
            return noEncontrado(id);
        }
        contenedor.start();
        log.info("Consumidor '{}' REANUDADO por el administrador", id);
        return ResponseEntity.ok(estado(contenedor, contenedor.isRunning()));
    }

    private boolean autorizado(String header) {
        return !clave.isBlank() && clave.equals(header);
    }

    private static Map<String, Object> estado(MessageListenerContainer<?> contenedor, boolean activo) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", contenedor.getId());
        m.put("activo", activo);
        return m;
    }

    private static ResponseEntity<Map<String, Object>> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "X-Admin-Key invalida o admin.api-key sin definir"));
    }

    private static ResponseEntity<Map<String, Object>> noEncontrado(String id) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "No existe el consumidor '" + id + "'"));
    }
}
