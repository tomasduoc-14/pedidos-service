package cl.duoc.pedidosservice.mensajeria.publicador;

import cl.duoc.pedidosservice.mensajeria.evento.PedidoEvento;

/** Unico punto por el que la logica de negocio publica eventos. No expone el SDK. */
public interface PedidoEventoPublicador {

    void publicar(PedidoEvento evento);
}
