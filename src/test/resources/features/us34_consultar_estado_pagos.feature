# language: es
Característica: US34 Consultar estado de pagos y garantía
  Como participante de un préstamo quiero consultar las transacciones económicas asociadas
  para saber si los pagos y garantías fueron procesados.

  Antecedentes:
    Dado un préstamo reservado con tarifa diaria de 20.00 PEN por 3 bloques de 24 horas y garantía de 50.00 PEN
    Y el prestatario constituye la garantía
    Y Mercado Pago notifica 1 vez que el pago fue "APROBADO"
    Y el prestatario paga la tarifa de 60.00 PEN con la clave "pago-1"

  Escenario: Ambos participantes ven cada transacción con su estado
    Cuando el prestamista consulta las transacciones del préstamo
    Entonces ve 2 transacciones
    Y ve una transacción "CONSTITUCION_GARANTIA" en estado "CONFIRMADA"
    Y ve una transacción "PAGO_TARIFA" en estado "PENDIENTE"

  Escenario: El estado refleja la actualización del proveedor
    Cuando Mercado Pago notifica 1 vez que el pago fue "APROBADO"
    Y el prestatario consulta las transacciones del préstamo
    Entonces ve una transacción "PAGO_TARIFA" en estado "CONFIRMADA"

  Escenario: Un estudiante ajeno no puede consultar
    Cuando un estudiante ajeno consulta las transacciones del préstamo
    Entonces la operación es rechazada con el mensaje "No participas en este préstamo"
