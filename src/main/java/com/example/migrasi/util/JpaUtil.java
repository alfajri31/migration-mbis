package com.example.migrasi.util;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

public class JpaUtil {

    /**
     * Method untuk mengambil satu field dari entity secara dinamis
     *
     * Contoh:
     * select e.id from Branch e where e.code = :value
     *
     * @param em           EntityManager
     * @param entityClass  Class entity (misal: Branch.class)
     * @param selectField  Field yang ingin diambil (misal: "id")
     * @param whereField   Field untuk kondisi where (misal: "code")
     * @param value        Nilai yang dicari
     * @param resultClass  Tipe hasil (misal: String.class)
     * @return             Nilai field yang ditemukan, atau null jika tidak ada
     */
    public static <T, R> R findField(
            EntityManager em,
            Class<T> entityClass,
            String selectField,
            String whereField,
            Object value,
            Class<R> resultClass
    ) {
        String entityName = entityClass.getSimpleName();

        String jpql = String.format(
                "select e.%s from %s e where e.%s = :value",
                selectField,
                entityName,
                whereField
        );

        TypedQuery<R> query = em.createQuery(jpql, resultClass);

        return query
                .setParameter("value", value)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }
}