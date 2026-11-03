package com.lendup.pagos.domain.model;
public enum TipoTransaccion { PAGO_TARIFA, PAGO_EXTENSION, CONSTITUCION_GARANTIA, LIBERACION_TARIFA, DEVOLUCION_GARANTIA, RETENCION_GARANTIA, REEMBOLSO_TARIFA;
  /** Operaciones que el prestatario paga mediante Checkout Pro. */
  public boolean esCobroAlPrestatario(){return this==PAGO_TARIFA||this==PAGO_EXTENSION||this==CONSTITUCION_GARANTIA;}
  /** Operaciones que se ejecutan como reembolso en Mercado Pago. */
  public boolean esReembolso(){return this==DEVOLUCION_GARANTIA||this==REEMBOLSO_TARIFA;}
}
