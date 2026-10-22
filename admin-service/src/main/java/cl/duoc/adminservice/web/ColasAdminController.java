package cl.duoc.adminservice.web;

import cl.duoc.adminservice.dto.AdminDtos.ColaEstado;
import cl.duoc.adminservice.dto.AdminDtos.ColaInfo;
import cl.duoc.adminservice.dto.AdminDtos.CrearColaRequest;
import cl.duoc.adminservice.dto.AdminDtos.ReprocesoResultado;
import cl.duoc.adminservice.service.ColaAdminService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Colas y DLQ. Sin logica: delega todo en ColaAdminService. */
@RestController
@RequestMapping("/api/admin")
public class ColasAdminController {

    private final ColaAdminService servicio;

    public ColasAdminController(ColaAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/queues")
    public List<String> listar() {
        return servicio.listarColas();
    }

    @PostMapping("/queues")
    public ResponseEntity<ColaInfo> crear(@Valid @RequestBody CrearColaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crearCola(req));
    }

    @GetMapping("/queues/{nombre}/status")
    public ColaEstado estado(@PathVariable String nombre) {
        return servicio.estadoCola(nombre);
    }

    @DeleteMapping("/queues/{nombre}")
    public ResponseEntity<Void> eliminar(@PathVariable String nombre) {
        servicio.eliminarCola(nombre);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/queues/{nombre}/purge")
    public Map<String, String> purgar(@PathVariable String nombre) {
        servicio.purgarCola(nombre);
        return Map.of("cola", nombre, "resultado", "purgada");
    }

    @GetMapping("/dlq/{nombre}/status")
    public ColaEstado estadoDlq(@PathVariable String nombre) {
        return servicio.estadoCola(nombre);
    }

    @PostMapping("/dlq/{nombre}/redrive")
    public ReprocesoResultado reprocesar(@PathVariable String nombre,
                                         @RequestParam(required = false) Integer max) {
        return servicio.reprocesarDlq(nombre, max);
    }

    @PostMapping("/dlq/{nombre}/purge")
    public Map<String, String> purgarDlq(@PathVariable String nombre) {
        servicio.purgarCola(nombre);
        return Map.of("dlq", nombre, "resultado", "purgada");
    }
}
