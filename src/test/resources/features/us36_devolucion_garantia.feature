# language: es
Característica: US36 Recibir devolución de garantía monetaria
  Como estudiante prestatario quiero recibir la devolución total o parcial de mi garantía
  para recuperar el monto disponible al finalizar el préstamo y resolver las incidencias.

  Antecedentes:
    Dado un préstamo reservado con tarifa diaria de 20.00 PEN por 3 bloques de 24 horas y garantía de 50.00 PEN
    Y el prestatario constituye la garantía
    Y Mercado Pago notifica 1 vez que el pago fue "APROBADO"

  Escenario: Préstamo finalizado sin incidencias devuelve toda la garantía
    Cuando el préstamo finaliza
    Entonces Mercado Pago reembolsa 50.00 PEN de la garantía
    Y la garantía queda en estado "DEVUELTA" por 50.00 PEN
    Y se publica 1 evento "GarantiaDevuelta"

  Escenario: Incidencia con afectación parcial retiene y devuelve el saldo
    Cuando se resuelve una incidencia con decisión "AFECTACION_PARCIAL" por 20.00 PEN
    Entonces Mercado Pago reembolsa 30.00 PEN de la garantía
    Y la garantía queda en estado "AFECTADA_PARCIALMENTE" por 50.00 PEN
    Y se publica 1 evento "GarantiaRetenida"
    Y se publica 1 evento "GarantiaDevuelta"

  Escenario: Incidencia con afectación total no devuelve nada
    Cuando se resuelve una incidencia con decisión "AFECTACION_TOTAL" por 50.00 PEN
    Entonces Mercado Pago reembolsa 0.00 PEN de la garantía
    Y la garantía queda en estado "AFECTADA_TOTALMENTE" por 50.00 PEN

  Escenario: Un evento repetido no genera una segunda devolución
    Cuando el préstamo finaliza
    Y el préstamo finaliza
    Entonces Mercado Pago reembolsa 50.00 PEN de la garantía
