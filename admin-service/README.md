# admin-service

Microservicio administrador de la mensajeria de Pedidos360 (SNS + SQS).
Puerto `8090`. Toda la logica esta en `ColaAdminService`; los controladores solo delegan.

## Levantar
```powershell
# credenciales del Learner Lab y la clave del administrador (NO se suben al repo)
$env:AWS_ACCESS_KEY_ID='...'; $env:AWS_SECRET_ACCESS_KEY='...'; $env:AWS_SESSION_TOKEN='...'
$env:AWS_REGION='us-east-1'
$env:ADMIN_API_KEY='clave-demo'
.\mvnw.cmd -f admin-service\pom.xml spring-boot:run
```
Todas las llamadas a `/api/admin/**` llevan el header `X-Admin-Key: <ADMIN_API_KEY>`.

## API REST

| Verbo | Ruta | Que hace |
|---|---|---|
| GET | `/api/admin/queues` | Lista las colas |
| POST | `/api/admin/queues` | Crea una cola (con DLQ y Redrive Policy por defecto) |
| GET | `/api/admin/queues/{nombre}/status` | Mensajes disponibles / en proceso / retrasados, retencion, visibility timeout, redrive |
| DELETE | `/api/admin/queues/{nombre}` | Elimina la cola |
| POST | `/api/admin/queues/{nombre}/purge` | Purga la cola |
| GET | `/api/admin/topics` | Lista los topicos |
| POST | `/api/admin/topics` | Crea un topico |
| DELETE | `/api/admin/topics/{nombre}` | Elimina un topico |
| GET | `/api/admin/topics/{nombre}/subscriptions` | Lista las suscripciones del topico |
| POST | `/api/admin/topics/{nombre}/subscriptions` | Suscribe una cola (filtro por `tipoEvento` y raw message delivery) |
| DELETE | `/api/admin/subscriptions?arn=...` | Elimina una suscripcion |
| GET | `/api/admin/consumers` | Estado de los consumidores en cada instancia |
| POST | `/api/admin/consumers/{id}/pause` | Pausa un consumidor (todas las instancias) |
| POST | `/api/admin/consumers/{id}/resume` | Reanuda un consumidor |
| GET | `/api/admin/dlq/{nombre}/status` | Estado de una DLQ |
| POST | `/api/admin/dlq/{nombre}/redrive?max=N` | Reprocesa: mueve los mensajes de la DLQ a su cola de origen |
| POST | `/api/admin/dlq/{nombre}/purge` | Purga la DLQ |

Ids de consumidor: `notificaciones-listener`, `tickets-listener`, `auditoria-listener`.

### Cuerpos
Crear cola (todos los campos menos `nombre` son opcionales; salen de `admin.cola-por-defecto`):
```json
{ "nombre": "pagos-queue", "conDlq": true, "visibilityTimeoutSegundos": 30,
  "retencionSegundos": 86400, "dlqRetencionSegundos": 1209600, "maxReceiveCount": 3 }
```
Crear topico: `{ "nombre": "eventos-prueba" }`

Suscribir una cola:
```json
{ "cola": "pagos-queue", "filtroTipoEvento": ["PEDIDO_CREADO"], "rawMessageDelivery": true }
```

## Errores
`400` datos invalidos, `401` falta X-Admin-Key, `404` cola/topico/consumidor inexistente,
`409` el recurso ya existe, `503` sin conexion o credenciales de AWS.

## Como se pausan los consumidores
Los listeners viven en `pedidos-service`, otro proceso. El administrador llama a
`/internal/consumidores/{id}/pausar|reanudar` de cada instancia listada en `admin.consumidores.urls`
(no se expone por el API Gateway y exige `X-Admin-Key`). Pausar detiene el listener: los mensajes
quedan en la cola y otra instancia activa los toma.

## Valores por defecto (admin.cola-por-defecto)
- `visibility-timeout-segundos: 30` > tiempo maximo de procesamiento de un mensaje.
- `retencion-segundos: 345600` (4 dias): margen para recuperarse de una caida de fin de semana.
- `dlq-retencion-segundos: 1209600` (14 dias, el maximo): la retencion corre desde que el mensaje entro a la cola original, por eso la DLQ debe retener mas.
- `max-receive-count: 3`: 3 intentos antes de enviar a la DLQ.
