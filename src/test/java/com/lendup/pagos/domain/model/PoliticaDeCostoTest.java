package com.lendup.pagos.domain.model;
import static org.junit.jupiter.api.Assertions.*; import java.math.BigDecimal; import java.time.LocalDateTime; import org.junit.jupiter.api.Test;
class PoliticaDeCostoTest { private final LocalDateTime base=LocalDateTime.of(2026,11,1,9,0); private final PoliticaDeCosto politica=PoliticaDeCosto.POR_BLOQUES_DE_24_HORAS;
  @Test void cobraBloquesIniciadosDe24Horas(){var p=new PeriodoPrestamo(base,base.plusHours(24).plusSeconds(1));assertEquals(2,p.bloquesIniciadosDe24Horas());assertEquals(new BigDecimal("25.00"),politica.importe(p,new BigDecimal("12.50")));}
  @Test void exactamente24HorasEsUnBloque(){assertEquals(1,new PeriodoPrestamo(base,base.plusDays(1)).bloquesIniciadosDe24Horas());}
  @Test void periodoMenorAUnDiaCobraUnBloque(){assertEquals(new BigDecimal("8.00"),politica.importe(new PeriodoPrestamo(base,base.plusMinutes(30)),new BigDecimal("8")));}
  @Test void redondeaADosDecimales(){assertEquals(new BigDecimal("10.01"),politica.importe(new PeriodoPrestamo(base,base.plusHours(2)),new BigDecimal("10.005")));}
  @Test void rechazaPeriodosYTarifasInvalidos(){assertThrows(IllegalArgumentException.class,()->new PeriodoPrestamo(base,base));var p=new PeriodoPrestamo(base,base.plusHours(1));assertThrows(IllegalArgumentException.class,()->politica.importe(p,BigDecimal.ZERO));}
}
