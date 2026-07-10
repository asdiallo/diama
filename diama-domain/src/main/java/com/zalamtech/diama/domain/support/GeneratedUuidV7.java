package com.zalamtech.diama.domain.support;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marque une clé primaire UUID générée en v7 par {@link UuidV7Generator}
 * (côté application, avant l'INSERT).
 */
@IdGeneratorType(UuidV7Generator.class)
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface GeneratedUuidV7 {
}
