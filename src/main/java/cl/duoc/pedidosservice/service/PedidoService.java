package cl.duoc.pedidosservice.service;

import cl.duoc.pedidosservice.model.EstadoPedido;
import cl.duoc.pedidosservice.model.Pedido;
import cl.duoc.pedidosservice.repository.PedidoRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import cl.duoc.pedidosservice.mensajeria.evento.PedidoEvento;
import cl.duoc.pedidosservice.mensajeria.evento.TipoEvento;
import cl.duoc.pedidosservice.mensajeria.publicador.PedidoEventoPublicador;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    private final PedidoEventoPublicador publicador;

    public PedidoService(PedidoRepository pedidoRepository, PedidoEventoPublicador publicador) {
        this.pedidoRepository = pedidoRepository;
        this.publicador = publicador;
    }
    private void publicar(TipoEvento tipo, Pedido p) {
        publicador.publicar(PedidoEvento.de(tipo, p.getId(), p.getProducto(), p.getCantidad(),
                p.getClienteEmail(), p.getEstado() == null ? null : p.getEstado().name()));
    }

    public List<Pedido> listarTodos() {
        return pedidoRepository.findAll();
    }

    public Pedido buscarPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    public Pedido crear(Pedido nuevo) {
        Pedido guardado = pedidoRepository.save(nuevo);
        publicar(TipoEvento.PEDIDO_CREADO, guardado);
        return guardado;
    }

    public Pedido actualizarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = buscarPorId(id);
        pedido.setEstado(nuevoEstado);
        Pedido actualizado = pedidoRepository.save(pedido);
        if (nuevoEstado == EstadoPedido.ENVIADO) {
            publicar(TipoEvento.PEDIDO_ENVIADO, actualizado);
        } else if (nuevoEstado == EstadoPedido.ENTREGADO) {
            publicar(TipoEvento.PEDIDO_ENTREGADO, actualizado);
        }
        return actualizado;
    }

    public void eliminar(Long id) {
        if (!pedidoRepository.existsById(id)) {
            throw new PedidoNoEncontradoException(id);
        }
        pedidoRepository.deleteById(id);
    }
}