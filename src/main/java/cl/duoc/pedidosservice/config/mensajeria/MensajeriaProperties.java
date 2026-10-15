package cl.duoc.pedidosservice.config.mensajeria;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Unica fuente de nombres y parametros de la mensajeria (topico, colas, DLQ).
 * Los valores viven en application.yml; ninguna clase de negocio debe escribirlos a mano.
 */
@Validated
@ConfigurationProperties(prefix = "mensajeria")
public record MensajeriaProperties(
        @NotNull @Valid Topico topico,
        @NotEmpty Map<String, @Valid Cola> colas) {

    public record Topico(@NotBlank String nombre) {
    }

    public record Cola(
            @NotBlank String nombre,
            @NotBlank String dlq,
            @Min(1) int visibilityTimeoutSegundos,
            @Min(60) int retencionSegundos,
            @Min(60) int dlqRetencionSegundos,
            @Min(1) int maxReceiveCount,
            List<String> filtroTipoEvento) {

        public Cola {
            filtroTipoEvento = filtroTipoEvento == null ? List.of() : List.copyOf(filtroTipoEvento);
        }
    }
}
