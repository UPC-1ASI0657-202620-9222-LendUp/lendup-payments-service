package com.lendup.pagos.config.security;
public record VerifiedToken(String uid,boolean emailVerified,boolean administrator){}
