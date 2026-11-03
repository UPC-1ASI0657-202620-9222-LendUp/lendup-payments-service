package com.lendup.pagos.config.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

@Configuration
public class SecurityConfiguration {
  private static final String[] SWAGGER_PATHS = {"/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**"};
  public static final String WEBHOOK_PATH = "/api/v1/webhooks/mercado-pago";

  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      TokenVerifier verifier,
      @Value("${lendup.security.cors-origins:}") String origins,
      @Value("${lendup.security.swagger-public:false}") boolean swaggerPublic) throws Exception {
    return http
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(request -> corsConfiguration(origins)))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(errors -> errors
            .authenticationEntryPoint((request, response, exception) -> writeError(response, 401,
                "AUTENTICACION_REQUERIDA", "Autenticación requerida"))
            .accessDeniedHandler((request, response, exception) -> writeError(response, 403,
                "NO_AUTORIZADO", "No autorizado")))
        .authorizeHttpRequests(authorize -> {
          authorize.requestMatchers(
              "/actuator/health/liveness",
              "/actuator/health/readiness").permitAll();
          // Mercado Pago no envía tokens de Firebase: la autenticidad se valida con la firma x-signature.
          authorize.requestMatchers(HttpMethod.POST, WEBHOOK_PATH).permitAll();
          if (swaggerPublic) authorize.requestMatchers(SWAGGER_PATHS).permitAll();
          else authorize.requestMatchers(SWAGGER_PATHS).authenticated();
          authorize.requestMatchers("/actuator/**").denyAll();
          authorize.anyRequest().authenticated();
        })
        .addFilterBefore(new FirebaseAuthenticationFilter(verifier), UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  private CorsConfiguration corsConfiguration(String configuredOrigins) {
    List<String> origins = Arrays.stream(configuredOrigins.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(origins);
    configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-ID"));
    configuration.setExposedHeaders(List.of("X-Correlation-ID"));
    configuration.setAllowCredentials(false);
    configuration.setMaxAge(3600L);
    return configuration;
  }

  private void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String code, String message)
      throws java.io.IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + code
        + "\",\"message\":\"" + message + "\"}");
  }
}
