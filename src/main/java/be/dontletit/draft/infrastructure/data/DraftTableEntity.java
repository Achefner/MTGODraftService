package be.dontletit.draft.infrastructure.data;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "draft_tables")
public class DraftTableEntity {

    @Id
    public String id;

    @Column(nullable = false)
    public String format;

    @Column(nullable = false)
    public String source;

    @Column(nullable = false)
    public String status;

    @Column(nullable = false)
    public boolean timed;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "draft_players", joinColumns = @JoinColumn(name = "draft_id"))
    @OrderBy("seat")
    public List<Player> players = new ArrayList<>();

    @Embeddable
    public static class Player {
        @Column(nullable = false)
        public int seat;

        @Column(nullable = false)
        public String name;

        @Column(name = "token_hash", nullable = false)
        public String tokenHash;
    }
}
