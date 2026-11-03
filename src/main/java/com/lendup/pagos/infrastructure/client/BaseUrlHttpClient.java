package com.lendup.pagos.infrastructure.client;

import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPDefaultHttpClient;
import com.mercadopago.net.MPHttpClient;
import com.mercadopago.net.MPRequest;
import com.mercadopago.net.MPResponse;
import org.apache.http.impl.client.HttpClients;

/**
 * Cliente HTTP del SDK oficial que permite cambiar la URL base (WireMock en pruebas) y desactiva los reintentos
 * internos del SDK: los reintentos los controla LendUp (misma clave de idempotencia y conciliación programada).
 * El cliente por defecto del SDK solo admite HTTPS; una URL http:// solo se acepta para simuladores locales.
 */
final class BaseUrlHttpClient implements MPHttpClient {
  private static final MPRequestOptions NO_RETRIES = MPRequestOptions.builder().maxRetries(0).build();
  private final MPDefaultHttpClient delegate;
  private final String baseUrl;

  BaseUrlHttpClient(String baseUrl) {
    this.baseUrl = baseUrl == null || baseUrl.isBlank() ? MercadoPagoConfig.BASE_URL : baseUrl.replaceAll("/+$", "");
    this.delegate = this.baseUrl.startsWith("http://")
        ? new MPDefaultHttpClient(HttpClients.custom().disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build()) {}
        : new MPDefaultHttpClient();
  }

  @Override
  public MPResponse send(MPRequest request) throws MPException, MPApiException {
    String uri = request.getUri();
    if (uri != null && uri.startsWith(MercadoPagoConfig.BASE_URL)) uri = baseUrl + uri.substring(MercadoPagoConfig.BASE_URL.length());
    MPRequest rewritten = MPRequest.builder()
        .uri(uri).method(request.getMethod()).headers(request.getHeaders()).payload(request.getPayload())
        .queryParams(request.getQueryParams()).accessToken(request.getAccessToken())
        .connectionTimeout(request.getConnectionTimeout()).connectionRequestTimeout(request.getConnectionRequestTimeout())
        .socketTimeout(request.getSocketTimeout()).build();
    return delegate.sendWithRetry(rewritten, NO_RETRIES);
  }
}
