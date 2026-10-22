package cl.duoc.adminservice.service;

import cl.duoc.adminservice.config.AdminProperties;
import cl.duoc.adminservice.dto.AdminDtos.ResultadoInstancia;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Habla con el endpoint interno de control de cada instancia de pedidos-service. */
@Component
public class ConsumidorControlClient {

    private static final String HEADER_CLAVE = "X-Admin-Key";

    private final RestClient rest;
    private final AdminProperties props;

    public ConsumidorControlClient(AdminProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(2));
        fabrica.setReadTimeout(Duration.ofSeconds(30));
        this.rest = RestClient.builder().requestFactory(fabrica).build();
    }

    public List<ResultadoInstancia> listar() {
        List<ResultadoInstancia> resultados = new ArrayList<>();
        for (String base : props.consumidores().urls()) {
            resultados.add(llamar(base, () -> rest.get()
                    .uri(base + "/internal/consumidores")
                    .header(HEADER_CLAVE, clave())
                    .retrieve()
                    .body(Object.class)));
        }
        return resultados;
    }

    /** accion: "pausar" o "reanudar". Se aplica a todas las instancias configuradas. */
    public List<ResultadoInstancia> accion(String listenerId, String accion) {
        List<ResultadoInstancia> resultados = new ArrayList<>();
        for (String base : props.consumidores().urls()) {
            resultados.add(llamar(base, () -> rest.post()
                    .uri(base + "/internal/consumidores/{id}/{accion}", listenerId, accion)
                    .header(HEADER_CLAVE, clave())
                    .retrieve()
                    .body(Object.class)));
        }
        return resultados;
    }

    private ResultadoInstancia llamar(String base, java.util.function.Supplier<Object> llamada) {
        try {
            Object respuesta = llamada.get();
            return new ResultadoInstancia(base, "OK", Map.of("respuesta", respuesta == null ? "" : respuesta));
        } catch (Exception e) {
            return new ResultadoInstancia(base, "ERROR", Map.of("causa", String.valueOf(e.getMessage())));
        }
    }

    private String clave() {
        return props.apiKey() == null ? "" : props.apiKey();
    }
}

