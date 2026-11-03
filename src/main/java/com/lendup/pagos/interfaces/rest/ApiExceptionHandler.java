package com.lendup.pagos.interfaces.rest;
import com.lendup.pagos.application.exception.*; import jakarta.validation.ConstraintViolationException; import java.time.Instant; import org.springframework.dao.DataIntegrityViolationException; import org.springframework.http.*; import org.springframework.http.converter.HttpMessageNotReadableException; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*; import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
/** Mismo formato de error que el monolito y Reservas: {status, error, message}. */
@RestControllerAdvice public class ApiExceptionHandler {
  record ErrorResponse(int status,String error,String message,Instant timestamp){}
  @ExceptionHandler(NotFoundException.class) ResponseEntity<ErrorResponse> notFound(Exception e){return error(404,"NO_ENCONTRADO",e.getMessage());}
  @ExceptionHandler(ForbiddenException.class) ResponseEntity<ErrorResponse> forbidden(Exception e){return error(403,"NO_AUTORIZADO",e.getMessage());}
  @ExceptionHandler(InvalidWebhookSignatureException.class) ResponseEntity<ErrorResponse> signature(Exception e){return error(401,"FIRMA_INVALIDA",e.getMessage());}
  @ExceptionHandler(BusinessConflictException.class) ResponseEntity<ErrorResponse> conflict(Exception e){return error(409,"CONFLICTO_NEGOCIO",e.getMessage());}
  @ExceptionHandler(DependencyUnavailableException.class) ResponseEntity<ErrorResponse> unavailable(Exception e){return error(503,"DEPENDENCIA_NO_DISPONIBLE",e.getMessage());}
  @ExceptionHandler({MethodArgumentNotValidException.class,ConstraintViolationException.class,HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,IllegalArgumentException.class}) ResponseEntity<ErrorResponse> bad(Exception e){return error(400,"SOLICITUD_INVALIDA",safe(e.getMessage()));}
  @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ErrorResponse> database(Exception e){return error(409,"CONFLICTO_DATOS","Conflicto de unicidad o integridad");}
  private ResponseEntity<ErrorResponse> error(int status,String code,String msg){return ResponseEntity.status(status).body(new ErrorResponse(status,code,msg,Instant.now()));}
  private String safe(String s){return s==null?"Solicitud inválida":s.length()>300?s.substring(0,300):s;}
}
