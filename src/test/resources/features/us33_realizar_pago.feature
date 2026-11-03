# language: es
Característica: US33 Realizar pago del préstamo
  Como estudiante prestatario quiero pagar la tarifa del préstamo mediante Mercado Pago
  para cumplir las condiciones económicas antes de recibir el objeto.

  Antecedentes:
    Dado un préstamo reservado con tarifa diaria de 20.00 PEN por 3 bloques de 24 horas y sin garantía

  Escenario: Ver los medios de pago habilitados
    Cuando consulto los medios de pago
    Entonces veo el medio "MERCADO_PAGO" en estado "ACTIVO"

  Escenario: Iniciar el pago registra el medio y devuelve el checkout
    Cuando el prestatario paga la tarifa de 60.00 PEN con la clave "pago-1"
    Entonces la última transacción queda en estado "PENDIENTE"
    Y la transacción tiene el medio "MERCADO_PAGO" y una URL de checkout de Mercado Pago

  Escenario: Mercado Pago aprueba el pago
    Dado el prestatario paga la tarifa de 60.00 PEN con la clave "pago-1"
    Cuando Mercado Pago notifica 3 veces que el pago fue "APROBADO"
    Entonces la última transacción queda en estado "CONFIRMADA"
    Y se publica 1 evento "PagoConfirmado"

  Escenario: Mercado Pago rechaza el pago y el pago sigue pendiente de cumplir
    Dado el prestatario paga la tarifa de 60.00 PEN con la clave "pago-1"
    Cuando Mercado Pago notifica 1 vez que el pago fue "RECHAZADO"
    Entonces la última transacción queda en estado "RECHAZADA"
    Y se publica 1 evento "PagoRechazado"
    Y el prestatario puede volver a pagar la tarifa con la clave "pago-2"

  Escenario: El importe debe coincidir con la tarifa acordada
    Cuando el prestatario paga la tarifa de 40.00 PEN con la clave "pago-3"
    Entonces la operación es rechazada con el mensaje "El importe no coincide con la tarifa acordada"

  Escenario: Solo el prestatario puede pagar
    Cuando el prestamista paga la tarifa de 60.00 PEN con la clave "pago-4"
    Entonces la operación es rechazada con el mensaje "Operación no permitida"
