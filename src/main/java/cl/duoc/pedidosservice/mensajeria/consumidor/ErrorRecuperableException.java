package cl.duoc.pedidosservice.mensajeria.consumidor;

/** Falla transitoria: no se confirma el mensaje y SQS lo reentrega. */
public class ErrorRecuperableException extends RuntimeException {
    public ErrorRecuperableException(String mensaje) {
        super(mensaje);
    }
}
