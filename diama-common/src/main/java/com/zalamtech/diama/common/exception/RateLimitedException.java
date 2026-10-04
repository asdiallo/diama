package com.zalamtech.diama.common.exception;

/** Trop de requêtes sur un endpoint public depuis la même origine (HTTP 429). */
public class RateLimitedException extends BusinessException {
    public RateLimitedException() {
        super("RATE_LIMITED", 429, "Trop de tentatives. Réessayez dans quelques minutes.");
    }
}
