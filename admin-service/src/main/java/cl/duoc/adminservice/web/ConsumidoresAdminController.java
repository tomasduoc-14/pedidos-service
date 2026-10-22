package cl.duoc.adminservice.web;

import cl.duoc.adminservice.dto.AdminDtos.ResultadoInstancia;
import cl.duoc.adminservice.service.ColaAdminService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Pausar y reanudar consumidores (listeners) en las instancias de pedidos-service. */
@RestController
@RequestMapping("/api/admin/consumers")
public class ConsumidoresAdminController {

    private final ColaAdminService servicio;

    public ConsumidoresAdminController(ColaAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<ResultadoInstancia> listar() {
        return servicio.listarConsumidores();
    }

    @PostMapping("/{id}/pause")
    public List<ResultadoInstancia> pausar(@PathVariable String id) {
        return servicio.pausarConsumidor(id);
    }

    @PostMapping("/{id}/resume")
    public List<ResultadoInstancia> reanudar(@PathVariable String id) {
        return servicio.reanudarConsumidor(id);
    }
}
