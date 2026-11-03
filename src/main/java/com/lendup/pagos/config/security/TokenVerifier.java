package com.lendup.pagos.config.security;
public interface TokenVerifier { VerifiedToken verify(String idToken) throws Exception; }
