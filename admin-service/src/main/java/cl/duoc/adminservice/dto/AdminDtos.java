package cl.duoc.adminservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Map;

/** Cuerpos de entrada y salida de la API de administracion. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record CrearColaRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,70}", message = "solo letras, numeros, - y _ (max 70)")
            String nombre,
            Boolean conDlq,
            @Min(0) @Max(43200) Integer visibilityTimeoutSegundos,
            @Min(60) @Max(1209600) Integer retencionSegundos,
            @Min(60) @Max(1209600) Integer dlqRetencionSegundos,
            @Min(1) @Max(1000) Integer maxReceiveCount) {
    }

    public record CrearTopicoRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,256}", message = "solo letras, numeros, - y _")
            String nombre) {
    }

    public record SuscripcionRequest(
            @NotBlank String cola,
            List<String> filtroTipoEvento,
            Boolean rawMessageDelivery) {
    }

    public record ColaInfo(String nombre, String url, String arn, String dlq) {
    }

    public record ColaEstado(
            String nombre,
            String arn,
            int mensajesDisponibles,
            int mensajesEnProceso,
            int mensajesRetrasados,
            int visibilityTimeoutSegundos,
            int retencionSegundos,
            String redrivePolicy) {
    }

    public record TopicoInfo(String nombre, String arn) {
    }

    public record SuscripcionInfo(String arn, String protocolo, String destino) {
    }

    public record ReprocesoResultado(String dlq, String colaDestino, int mensajesMovidos) {
    }

    public record ResultadoInstancia(String instancia, String resultado, Map<String, Object> detalle) {
    }
}
