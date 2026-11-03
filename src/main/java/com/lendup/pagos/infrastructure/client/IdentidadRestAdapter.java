package com.lendup.pagos.infrastructure.client;

import com.lendup.pagos.application.exception.DependencyUnavailableException;
import com.lendup.pagos.application.exception.ForbiddenException;
import com.lendup.pagos.application.port.out.IdentidadPort;
import com.lendup.pagos.config.IntegrationProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Resuelve UID de Firebase → usuario_id interno. Con IDENTITY_SERVICE_TOKEN usa el contrato interno acordado
 * ({"usuario_id": "..."}); sin token reenvía el Firebase ID token del usuario, lo que permite apuntar en local a
 * GET /api/v1/estudiantes/me del monolito ({"usuario": {"id": "..."}}).
 */
@Component
public class IdentidadRestAdapter implements IdentidadPort {
  private final IntegrationProperties.Identity config; private final RestClient client;
  public IdentidadRestAdapter(IntegrationProperties p){config=p.identity();client=config.baseUrl().isBlank()?null:RestClientFactory.create(config.baseUrl(),config.connectTimeoutMs(),config.readTimeoutMs());}

  @Override
  public UUID resolverUsuarioId(String firebaseUid, String bearerToken) {
    if(client==null||config.firebaseUserUriTemplate().isBlank())throw new DependencyUnavailableException("Integración de resolución de identidad con Identidad no configurada");
    String token=config.serviceToken().isBlank()?bearerToken:config.serviceToken();
    if(token==null||token.isBlank())throw new DependencyUnavailableException("No hay credencial para consultar Identidad");
    try{
      @SuppressWarnings("unchecked") Map<String,Object> row=client.get().uri(config.firebaseUserUriTemplate(),firebaseUid).header("Authorization","Bearer "+token).retrieve().body(Map.class);
      Object id=row==null?null:row.get("usuario_id");
      if(id==null&&row!=null&&row.get("usuario") instanceof Map<?,?> usuario)id=usuario.get("id");
      if(id==null)throw new DependencyUnavailableException("Identidad no vinculó el token a un usuario interno");
      return UUID.fromString(id.toString());
    }catch(HttpClientErrorException.NotFound|HttpClientErrorException.Forbidden e){throw new ForbiddenException("Registra tu perfil en LendUp");}
    catch(DependencyUnavailableException e){throw e;}catch(Exception e){throw new DependencyUnavailableException("Identidad no disponible",e);}
  }
}
