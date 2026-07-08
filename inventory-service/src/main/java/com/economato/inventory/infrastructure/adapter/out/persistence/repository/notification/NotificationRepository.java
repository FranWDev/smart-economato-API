package com.economato.inventory.infrastructure.adapter.out.persistence.repository.notification;
import com.economato.inventory.domain.model.notification.NotificationType;

import com.economato.inventory.domain.model.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    long deleteByIsReadTrue();

    @Override
    @EntityGraph(attributePaths = {"recipient", "sender"})
    Page<Notification> findAll(Specification<Notification> spec, Pageable pageable);

    @EntityGraph(attributePaths = {"recipient", "sender"})
    Optional<Notification> findByIdAndRecipientId(Long id, Integer recipientId);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipient.id = :recipientId AND n.isRead = false AND n.isDeletedByRecipient = false")
    long countByRecipientIdAndIsReadFalseAndIsDeletedByRecipientFalse(@Param("recipientId") Integer recipientId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :recipientId AND n.isRead = false AND n.isDeletedByRecipient = false")
    int markAllAsReadByRecipientId(@Param("recipientId") Integer recipientId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Notification n SET n.isDeletedBySender = true WHERE n.groupId = :groupId AND n.sender.id = :senderId AND n.type = NotificationType.MANUAL")
    int softDeleteManualGroupByGroupIdAndSenderId(@Param("groupId") String groupId, @Param("senderId") Integer senderId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.createdAt < :threshold AND n.isRead = true")
    int deleteReadByCreatedAtBefore(@Param("threshold") LocalDateTime threshold);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.createdAt BETWEEN :from AND :to AND n.isRead = true")
    int deleteReadByCreatedAtBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.createdAt < :threshold AND n.isRead = true AND n.isDeletedByRecipient = true")
    int deleteOldReadAndDeletedBefore(@Param("threshold") LocalDateTime threshold);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Notification n WHERE n.type IN :types")
    void deleteByTypes(@Param("types") Collection<NotificationType> types);
}