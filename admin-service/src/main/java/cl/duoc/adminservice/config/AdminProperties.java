package cl.duoc.adminservice.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Unica fuente de configuracion del administrador (application.yml, prefijo "admin"). */
@ConfigurationProperties(prefix = "admin")
public record AdminProperties(String apiKey, Consumidores consumidores, ColaPorDefecto colaPorDefecto) {

    public record Consumidores(List<String> urls) {
        public Consumidores {
            urls = urls == null ? List.of() : List.copyOf(urls);
        }
    }

    public record ColaPorDefecto(
            int visibilityTimeoutSegundos,
            int retencionSegundos,
            int dlqRetencionSegundos,
            int maxReceiveCount) {
    }
}
