package cl.duoc.adminservice.web;

import cl.duoc.adminservice.dto.AdminDtos.CrearTopicoRequest;
import cl.duoc.adminservice.dto.AdminDtos.SuscripcionInfo;
import cl.duoc.adminservice.dto.AdminDtos.SuscripcionRequest;
import cl.duoc.adminservice.dto.AdminDtos.TopicoInfo;
import cl.duoc.adminservice.service.ColaAdminService;
import jakarta.validation.Valid;
import java.util.List;
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

/** Topicos y suscripciones. Sin logica: delega todo en ColaAdminService. */
@RestController
@RequestMapping("/api/admin")
public class TopicosAdminController {

    private final ColaAdminService servicio;

    public TopicosAdminController(ColaAdminService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/topics")
    public List<TopicoInfo> listar() {
        return servicio.listarTopicos();
    }

    @PostMapping("/topics")
    public ResponseEntity<TopicoInfo> crear(@Valid @RequestBody CrearTopicoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.crearTopico(req.nombre()));
    }

    @DeleteMapping("/topics/{nombre}")
    public ResponseEntity<Void> eliminar(@PathVariable String nombre) {
        servicio.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/topics/{nombre}/subscriptions")
    public List<SuscripcionInfo> listarSuscripciones(@PathVariable String nombre) {
        return servicio.listarSuscripciones(nombre);
    }

    @PostMapping("/topics/{nombre}/subscriptions")
    public ResponseEntity<SuscripcionInfo> suscribir(@PathVariable String nombre,
                                                     @Valid @RequestBody SuscripcionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.suscribir(nombre, req));
    }

    /** El ARN de una suscripcion contiene ':' y '/', por eso va como parametro y no en la ruta. */
    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> eliminarSuscripcion(@RequestParam String arn) {
        servicio.eliminarSuscripcion(arn);
        return ResponseEntity.noContent().build();
    }
}
