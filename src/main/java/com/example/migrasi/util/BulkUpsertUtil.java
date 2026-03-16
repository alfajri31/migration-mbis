package com.example.migrasi.util;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.util.List;
import java.util.function.Function;

@Slf4j
public class BulkUpsertUtil {

    public static <T, ID> int bulkUpsert(
            List<T> entities,
            Class<T> entityClass,
            Class<ID> idClass,
            Function<T, Object> uniqueKeyGetter,
            String uniqueField,
            EntityManager entityManager,
            PlatformTransactionManager txManager
    ) {

        if (entities == null || entities.isEmpty()) return 0;

        int processed = 0;

        for (T entity : entities) {

            DefaultTransactionDefinition def = new DefaultTransactionDefinition();
            def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

            TransactionStatus status = txManager.getTransaction(def);

            try {

                Object key = uniqueKeyGetter.apply(entity);

                if (key == null || key.toString().isBlank()) {
                    log.warn("Skip: unique key kosong");
                    txManager.commit(status);
                    continue;
                }

                String jpql = String.format(
                        "select e.id from %s e where e.%s = :key",
                        entityClass.getSimpleName(),
                        uniqueField
                );

                ID existingId = entityManager.createQuery(jpql, idClass)
                        .setParameter("key", key)
                        .setMaxResults(1)
                        .getResultStream()
                        .findFirst()
                        .orElse(null);

                if (existingId != null) {

                    entityClass.getMethod("setId", idClass)
                            .invoke(entity, existingId);

                    entityManager.merge(entity);

                } else {

                    entityManager.persist(entity);

                }

                entityManager.flush();
                entityManager.clear();

                txManager.commit(status);
                processed++;

            } catch (Exception ex) {

                txManager.rollback(status);
                entityManager.clear();

                log.warn("Skip error key {} : {}", uniqueKeyGetter.apply(entity), ex.getMessage());
            }
        }

        log.info("BulkUpsert processed: {}", processed);

        return processed;
    }
}