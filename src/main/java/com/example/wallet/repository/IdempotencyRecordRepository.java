package com.example.wallet.repository;

import com.example.wallet.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

    Optional<IdempotencyRecord> findByIdempotencyKey(String idempotencyKey);

    /**
     * Atomically claims an idempotency key.
     *
     * <p>PostgreSQL's ON CONFLICT is essential here. If another transaction has
     * inserted the same key but has not committed yet, this statement waits for
     * that transaction. It then returns 0 if the other transaction committed, or
     * inserts and returns 1 if the other transaction rolled back. This removes
     * the need for an exception-based retry loop.</p>
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            insert into idempotency_records
                (id, idempotency_key, operation_type, request_hash, status, created_at)
            values
                (:id, :idempotencyKey, :operationType, :requestHash, 'IN_PROGRESS', now())
            on conflict (idempotency_key) do nothing
            """, nativeQuery = true)
    int tryInsert(@Param("id") UUID id,
                  @Param("idempotencyKey") String idempotencyKey,
                  @Param("operationType") String operationType,
                  @Param("requestHash") String requestHash);

    /**
     * Marks a claimed key as completed in the same transaction that moved money.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = false)
    @Query(value = """
            update idempotency_records
               set status = 'COMPLETED',
                   response_status = :responseStatus,
                   response_body = :responseBody,
                   completed_at = now()
             where idempotency_key = :idempotencyKey
               and status = 'IN_PROGRESS'
            """, nativeQuery = true)
    int complete(@Param("idempotencyKey") String idempotencyKey,
                 @Param("responseStatus") int responseStatus,
                 @Param("responseBody") String responseBody);
}
