package com.zalamtech.diama.domain.support;

import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

import java.util.EnumSet;

/**
 * Générateur d'identifiant Hibernate (avant exécution) produisant des UUID v7.
 * Une valeur déjà assignée à l'entité est respectée (import de données, tests).
 */
public class UuidV7Generator implements BeforeExecutionGenerator {

    public UuidV7Generator(GeneratedUuidV7 config) {
        // La configuration ne porte pas d'attribut : rien à lire.
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }

    @Override
    public Object generate(SharedSessionContractImplementor session,
                           Object owner,
                           Object currentValue,
                           EventType eventType) {
        return currentValue != null ? currentValue : UuidV7.generate();
    }
}
