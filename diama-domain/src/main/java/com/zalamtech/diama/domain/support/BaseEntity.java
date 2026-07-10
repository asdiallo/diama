package com.zalamtech.diama.domain.support;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Socle commun des entités à clé primaire UUID.
 *
 * <p>La PK est générée côté application en <b>UUID v7</b> (time-ordered) via
 * {@link GeneratedUuidV7}, cohérent avec le {@code DEFAULT uuid_generate_v7()}
 * de la base (utilisé par les triggers / insertions hors application).</p>
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity extends PanacheEntityBase {

    @Id
    @GeneratedUuidV7
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
}
