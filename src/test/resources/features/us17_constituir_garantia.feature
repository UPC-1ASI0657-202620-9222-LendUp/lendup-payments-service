# language: es
Característica: US17 Constituir garantía monetaria
  Como estudiante prestatario quiero constituir la garantía requerida mediante Mercado Pago
  para cumplir la condición de protección antes de recibir el objeto.

  Escenario: Constituir la garantía muestra el monto y el checkout
    Dado un préstamo reservado con tarifa diaria de 20.00 PEN por 3 bloques de 24 horas y garantía de 50.00 PEN
    Cuando el prestatario constituye la garantía
    Entonces la última transacción queda en estado "PENDIENTE"
    Y la garantía queda en estado "PENDIENTE" por 50.00 PEN
    Y la transacción tiene el medio "MERCADO_PAGO" y una URL de checkout de Mercado Pago

  Escenario: La confirmación del proveedor constituye la garantía
    Dado un préstamo reservado con tarifa diaria de 20.00 PEN por 3 bloques de 24 horas y garantía de 50.00 PEN
    Y el prestatario constituye la garantía
    Cuando Mercado Pago notifica 2 veces que el pago fue "APROBADO"
    Entonces la garantía queda en estado "CONSTITUIDA" por 50.00 PEN
    Y se publica 1 evento "GarantiaConstituida"

  Escenario: Un rechazo deja la garantía sin confirmar
    Dado un préstamo reservado con tarifa diaria de 20.00 PEN por 3 bloques de 24 horas y garantía de 50.00 PEN
    Y el prestatario constituye la garantía
    Cuando Mercado Pago notifica 1 vez que el pago fue "RECHAZADO"
    Entonces la garantía queda en estado "PENDIENTE" por 50.00 PEN
    Y se publica 1 evento "PagoRechazado"

  Escenario: Un préstamo sin garantía acordada no admite constituirla
    Dado un préstamo reservado con tarifa diaria de 20.00 PEN por 3 bloques de 24 horas y sin garantía
    Cuando el prestatario constituye la garantía
    Entonces la operación es rechazada con el mensaje "El préstamo no requiere garantía"
