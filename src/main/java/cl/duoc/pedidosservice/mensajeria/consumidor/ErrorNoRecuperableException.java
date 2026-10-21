package cl.duoc.pedidosservice.mensajeria.consumidor;

/** Falla definitiva: reintentar no sirve, el mensaje va directo a la DLQ. */
public class ErrorNoRecuperableException extends RuntimeException {
    public ErrorNoRecuperableException(String mensaje) {
        super(mensaje);
    }
}
