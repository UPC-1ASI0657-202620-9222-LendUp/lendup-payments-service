package com.lendup.pagos.support;
import com.lendup.pagos.application.port.out.IdentidadPort; import com.lendup.pagos.config.security.*; import java.nio.charset.StandardCharsets; import java.util.UUID; import org.springframework.boot.test.context.TestConfiguration; import org.springframework.context.annotation.Bean; import org.springframework.context.annotation.Primary;
/** El token de prueba es el propio UID ("Bearer prestatario") y Identidad lo resuelve a un UUID determinista. */
@TestConfiguration public class TestAuthConfiguration {
  @Bean TokenVerifier testTokenVerifier(){return t->new VerifiedToken(t,true,false);}
  @Bean @Primary IdentidadPort testIdentidad(){return (uid,token)->usuario(uid);}
  public static UUID usuario(String uid){return UUID.nameUUIDFromBytes(uid.getBytes(StandardCharsets.UTF_8));}
}
