package com.olivamore.api.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;

interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {

	Page<UserNotification> findByUserId(long userId, Pageable pageable);

	Page<UserNotification> findByUserIdAndReadAtIsNull(long userId, Pageable pageable);

	long countByUserIdAndReadAtIsNull(long userId);

	boolean existsBySubjectTypeAndSubjectIdAndUserIdInAndCreatedAtGreaterThanEqual(String subjectType, long subjectId,
		Collection<Long> userIds, LocalDateTime since);

	@Modifying
	@Query("""
		update UserNotification n set n.readAt = :now, n.updatedAt = :now
		where n.userId = :userId and n.readAt is null
		""")
	int markAllRead(long userId, LocalDateTime now);

}
