package cl.duoc.adminservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Protege /api/admin/** con el header X-Admin-Key cuando admin.api-key esta definida. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    public static final String HEADER = "X-Admin-Key";

    private final String clave;

    public ApiKeyFilter(AdminProperties props) {
        this.clave = props.apiKey() == null ? "" : props.apiKey();
        if (clave.isBlank()) {
            log.warn("admin.api-key vacia: la API de administracion queda SIN proteccion (solo desarrollo)");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return clave.isBlank() || !request.getRequestURI().startsWith("/api/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!clave.equals(request.getHeader(HEADER))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Header X-Admin-Key invalido o ausente\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
