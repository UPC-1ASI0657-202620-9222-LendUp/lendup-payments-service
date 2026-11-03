package com.lendup.pagos.infrastructure.client;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.lendup.pagos.application.exception.DependencyUnavailableException;
import com.lendup.pagos.application.exception.ForbiddenException;
import com.lendup.pagos.config.IntegrationProperties;
import java.util.UUID;
import org.junit.jupiter.api.*;

class IdentidadRestAdapterTest {
  static WireMockServer server;
  @BeforeAll static void start() { server = new WireMockServer(WireMockConfiguration.options().dynamicPort()); server.start(); }
  @AfterAll static void stop() { server.stop(); }
  @BeforeEach void reset() { server.resetAll(); }

  private IdentidadRestAdapter adapter(String token, String template) {
    return new IdentidadRestAdapter(new IntegrationProperties(new IntegrationProperties.Identity(server.baseUrl(), token, template, 500, 800), null));
  }

  @Test void contratoInternoConTokenDeServicio() {
    UUID id = UUID.randomUUID();
    server.stubFor(get(urlEqualTo("/internal/v1/usuarios/firebase/uid-1")).withHeader("Authorization", equalTo("Bearer service-token"))
        .willReturn(okJson("{\"usuario_id\":\"" + id + "\"}")));
    assertEquals(id, adapter("service-token", "/internal/v1/usuarios/firebase/{uid}").resolverUsuarioId("uid-1", "user-token"));
  }

  @Test void desarrolloLocalContraElMonolitoReenviaElTokenDelUsuario() {
    UUID id = UUID.randomUUID();
    server.stubFor(get(urlEqualTo("/api/v1/estudiantes/me")).withHeader("Authorization", equalTo("Bearer user-token"))
        .willReturn(okJson("{\"usuario\":{\"id\":\"" + id + "\"},\"perfil\":{}}")));
    assertEquals(id, adapter("", "/api/v1/estudiantes/me").resolverUsuarioId("uid-1", "user-token"));
  }

  @Test void usuarioSinPerfilEs403YSinConfiguracion503() {
    server.stubFor(get(urlEqualTo("/api/v1/estudiantes/me")).willReturn(aResponse().withStatus(403)));
    assertThrows(ForbiddenException.class, () -> adapter("", "/api/v1/estudiantes/me").resolverUsuarioId("uid", "t"));
    assertThrows(DependencyUnavailableException.class, () -> new IdentidadRestAdapter(new IntegrationProperties(null, null)).resolverUsuarioId("uid", "t"));
  }
}
