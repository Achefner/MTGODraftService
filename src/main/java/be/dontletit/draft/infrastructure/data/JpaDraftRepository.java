package be.dontletit.draft.infrastructure.data;

import be.dontletit.draft.domain.DraftFormat;
import be.dontletit.draft.domain.event.DraftEvent;
import be.dontletit.draft.spi.DraftRepositorySpiPort;
import be.dontletit.draft.spi.PlayerRecord;
import be.dontletit.draft.spi.TableRecord;
import be.dontletit.draft.spi.TableStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Tables and histories in the service's own postgres database (H2 in tests). */
@ApplicationScoped
public class JpaDraftRepository implements DraftRepositorySpiPort {

    @Inject
    EntityManager em;

    @Inject
    EventCodec codec;

    @Override
    @Transactional
    public void saveTable(TableRecord table) {
        DraftTableEntity entity = em.find(DraftTableEntity.class, table.id());
        boolean isNew = entity == null;
        if (isNew) {
            entity = new DraftTableEntity();
            entity.id = table.id();
        }
        entity.format = table.format().name();
        entity.source = table.source();
        entity.status = table.status().name();
        entity.timed = table.timed();
        entity.createdAt = table.createdAt();
        entity.players.clear();
        for (PlayerRecord player : table.players()) {
            DraftTableEntity.Player row = new DraftTableEntity.Player();
            row.seat = player.seat();
            row.name = player.name();
            row.tokenHash = player.tokenHash();
            entity.players.add(row);
        }
        if (isNew) em.persist(entity);
    }

    @Override
    @Transactional
    public Optional<TableRecord> findTable(String draftId) {
        return Optional.ofNullable(draftId == null ? null : em.find(DraftTableEntity.class, draftId)).map(JpaDraftRepository::toRecord);
    }

    @Override
    @Transactional
    public List<TableRecord> findTables(TableStatus status) {
        return em.createQuery("select t from DraftTableEntity t where t.status = :status", DraftTableEntity.class)
                .setParameter("status", status.name())
                .getResultList().stream()
                .map(JpaDraftRepository::toRecord)
                .toList();
    }

    @Override
    @Transactional
    public void appendEvents(String draftId, int firstPosition, List<DraftEvent> events) {
        long stored = em.createQuery("select count(e) from DraftEventEntity e where e.draftId = :id", Long.class)
                .setParameter("id", draftId).getSingleResult();
        if (stored != firstPosition) {
            throw new IllegalStateException("Draft " + draftId + " has " + stored + " stored events, cannot append at " + firstPosition);
        }
        Instant now = Instant.now();
        int index = firstPosition;
        try {
            for (DraftEvent event : events) {
                EventCodec.Encoded encoded = codec.encode(event);
                DraftEventEntity row = new DraftEventEntity();
                row.draftId = draftId;
                row.index = index++;
                row.type = encoded.type();
                row.payload = encoded.payload();
                row.recordedAt = now;
                em.persist(row);
            }
            em.flush();
        } catch (PersistenceException e) {
            throw new IllegalStateException("Another write reached draft " + draftId + " first", e);
        }
    }

    @Override
    @Transactional
    public List<DraftEvent> loadEvents(String draftId) {
        return em.createQuery("select e from DraftEventEntity e where e.draftId = :id order by e.index", DraftEventEntity.class)
                .setParameter("id", draftId)
                .getResultList().stream()
                .map(row -> codec.decode(row.type, row.payload))
                .toList();
    }

    private static TableRecord toRecord(DraftTableEntity entity) {
        List<PlayerRecord> players = entity.players.stream()
                .map(p -> new PlayerRecord(p.seat, p.name, p.tokenHash))
                .sorted(java.util.Comparator.comparingInt(PlayerRecord::seat))
                .toList();
        return new TableRecord(entity.id, DraftFormat.valueOf(entity.format), entity.source, TableStatus.valueOf(entity.status),
                entity.timed, entity.createdAt, players);
    }
}
