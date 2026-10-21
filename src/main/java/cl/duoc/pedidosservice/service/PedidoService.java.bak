package cl.duoc.pedidosservice.service;

import cl.duoc.pedidosservice.model.EstadoPedido;
import cl.duoc.pedidosservice.model.Pedido;
import cl.duoc.pedidosservice.repository.PedidoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
    }

    public List<Pedido> listarTodos() {
        return pedidoRepository.findAll();
    }

    public Pedido buscarPorId(Long id) {
        return pedidoRepository.findById(id)
                .orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    public Pedido crear(Pedido nuevo) {
        return pedidoRepository.save(nuevo);
    }

    public Pedido actualizarEstado(Long id, EstadoPedido nuevoEstado) {
        Pedido pedido = buscarPorId(id);
        pedido.setEstado(nuevoEstado);
        return pedidoRepository.save(pedido);
    }

    public void eliminar(Long id) {
        if (!pedidoRepository.existsById(id)) {
            throw new PedidoNoEncontradoException(id);
        }
        pedidoRepository.deleteById(id);
    }
}