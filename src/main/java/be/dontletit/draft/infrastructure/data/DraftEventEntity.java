package be.dontletit.draft.infrastructure.data;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** One event of a draft's history, at its index. */
@Entity
@Table(name = "draft_events")
@IdClass(DraftEventEntity.Key.class)
public class DraftEventEntity {

    @Id
    @Column(name = "draft_id")
    public String draftId;

    @Id
    @Column(name = "event_index")
    public int index;

    @Column(nullable = false)
    public String type;

    @Column(nullable = false)
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    public String payload;

    @Column(name = "recorded_at", nullable = false)
    public Instant recordedAt;

    public static class Key implements Serializable {
        public String draftId;
        public int index;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key key && index == key.index && Objects.equals(draftId, key.draftId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(draftId, index);
        }
    }
}
