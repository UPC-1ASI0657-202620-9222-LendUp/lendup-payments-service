package com.lendup.pagos.application.port.out;
import java.util.UUID;
/** Resuelve el UID verificado de Firebase al usuario_id interno de Identidad. */
public interface IdentidadPort { UUID resolverUsuarioId(String firebaseUid, String bearerToken); }
