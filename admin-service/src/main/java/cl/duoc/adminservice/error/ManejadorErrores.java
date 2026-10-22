package cl.duoc.adminservice.error;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;

@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, Object>> noEncontrado(RecursoNoEncontradoException e) {
        return cuerpo(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> peticionInvalida(IllegalArgumentException e) {
        return cuerpo(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return cuerpo(HttpStatus.BAD_REQUEST, detalle);
    }

    @ExceptionHandler(AwsServiceException.class)
    public ResponseEntity<Map<String, Object>> errorAws(AwsServiceException e) {
        HttpStatus estado = HttpStatus.resolve(e.statusCode());
        if (estado == null || estado.is2xxSuccessful()) {
            estado = HttpStatus.BAD_GATEWAY;
        }
        String mensaje = e.awsErrorDetails() != null ? e.awsErrorDetails().errorMessage() : e.getMessage();
        log.warn("Error de AWS ({}): {}", estado.value(), mensaje);
        return cuerpo(estado, mensaje);
    }

    @ExceptionHandler(SdkClientException.class)
    public ResponseEntity<Map<String, Object>> sinConexionAws(SdkClientException e) {
        log.warn("No se pudo llamar a AWS: {}", e.getMessage());
        return cuerpo(HttpStatus.SERVICE_UNAVAILABLE,
                "No se pudo conectar con AWS (credenciales o red): " + e.getMessage());
    }

    private static ResponseEntity<Map<String, Object>> cuerpo(HttpStatus estado, String mensaje) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("status", estado.value());
        cuerpo.put("error", mensaje);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}
