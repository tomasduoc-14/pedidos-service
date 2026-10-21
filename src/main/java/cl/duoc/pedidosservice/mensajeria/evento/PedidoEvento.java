package cl.duoc.pedidosservice.mensajeria.evento;

import java.time.Instant;
import java.util.UUID;

/** Mensaje que viaja por el topico. eventoId permite detectar duplicados. */
public record PedidoEvento(
        String eventoId,
        String tipoEvento,
        Long pedidoId,
        String producto,
        Integer cantidad,
        String clienteEmail,
        String estado,
        String fecha) {

    public static PedidoEvento de(TipoEvento tipo, Long pedidoId, String producto,
                                  Integer cantidad, String clienteEmail, String estado) {
        return new PedidoEvento(UUID.randomUUID().toString(), tipo.name(), pedidoId,
                producto, cantidad, clienteEmail, estado, Instant.now().toString());
    }
}
