# language: es
Característica: US41 Recibir reembolso por cancelación
  Como estudiante prestatario quiero recibir el reembolso cuando una reserva confirmada se cancela
  antes de la entrega, para recuperar los pagos y garantías procesados.

  Antecedentes:
    Dado un préstamo reservado con tarifa diaria de 20.00 PEN por 3 bloques de 24 horas y garantía de 50.00 PEN
    Y el prestatario constituye la garantía
    Y Mercado Pago notifica 1 vez que el pago fue "APROBADO"
    Y el prestatario paga la tarifa de 60.00 PEN con la clave "pago-1"
    Y Mercado Pago notifica 1 vez que el pago fue "APROBADO"

  Escenario: La cancelación reembolsa la tarifa y devuelve la garantía
    Cuando la reserva se cancela antes de la entrega
    Entonces Mercado Pago reembolsa 60.00 PEN de la tarifa
    Y Mercado Pago reembolsa 50.00 PEN de la garantía
    Y se publica 1 evento "ReembolsoProcesado"
    Y se publica 1 evento "GarantiaDevuelta"

  Escenario: Si el proveedor no responde el reembolso queda pendiente y se reintenta
    Dado que Mercado Pago no responde
    Cuando la reserva se cancela antes de la entrega
    Entonces hay 2 reembolsos en estado "PENDIENTE"
    Cuando Mercado Pago vuelve a responder y corre la conciliación
    Entonces hay 2 reembolsos en estado "CONFIRMADA"
    Y Mercado Pago reembolsa 60.00 PEN de la tarifa

  Escenario: Si el proveedor rechaza el reembolso queda registrado como no completado
    Dado que Mercado Pago rechaza los reembolsos
    Cuando la reserva se cancela antes de la entrega
    Entonces hay 2 reembolsos en estado "FALLIDA"
    Y se publica 0 eventos "ReembolsoProcesado"

  Escenario: El mismo evento de cancelación no genera reembolsos duplicados
    Cuando la reserva se cancela antes de la entrega
    Y la reserva se cancela antes de la entrega
    Entonces Mercado Pago reembolsa 60.00 PEN de la tarifa
    Y hay 2 reembolsos en estado "CONFIRMADA"
