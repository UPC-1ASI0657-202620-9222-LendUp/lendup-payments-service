package com.lendup.pagos.config.security;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class FirebaseConfiguration {
  @Bean
  FirebaseAuth firebaseAuth(@Value("${firebase.project-id}") String projectId) throws IOException {
    if (projectId == null || projectId.isBlank()) {
      throw new IllegalStateException("FIREBASE_PROJECT_ID es obligatorio");
    }
    FirebaseApp app;
    if (FirebaseApp.getApps().isEmpty()) {
      GoogleCredentials credentials;
      try {
        credentials = GoogleCredentials.getApplicationDefault();
      } catch (IOException exception) {
        throw new IllegalStateException(
            "Firebase requiere Application Default Credentials; no existe bypass de producción", exception);
      }
      app = FirebaseApp.initializeApp(FirebaseOptions.builder()
          .setCredentials(credentials)
          .setProjectId(projectId)
          .build());
    } else {
      app = FirebaseApp.getInstance();
      if (!projectId.equals(app.getOptions().getProjectId())) {
        throw new IllegalStateException("El Project ID de Firebase no coincide con la aplicación inicializada");
      }
    }
    return FirebaseAuth.getInstance(app);
  }

  @Bean
  TokenVerifier firebaseTokenVerifier(FirebaseAuth auth) {
    return rawToken -> {
      FirebaseToken token = auth.verifyIdToken(rawToken, true);
      Object role = token.getClaims().get("role");
      boolean administrator = "ADMINISTRADOR".equals(role)
          || Boolean.TRUE.equals(token.getClaims().get("admin"));
      return new VerifiedToken(token.getUid(), token.isEmailVerified(), administrator);
    };
  }
}
