package com.olivamore.api.notification;

import com.olivamore.api.identity.CurrentUser;
import com.olivamore.api.notification.NotificationResponses.Notification;
import com.olivamore.api.notification.NotificationResponses.NudgeResult;
import com.olivamore.api.notification.NotificationResponses.NudgeTarget;
import com.olivamore.api.notification.NotificationResponses.Sender;
import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.time.Timestamps;
import com.olivamore.api.shared.validation.LaravelMessages;
import com.olivamore.api.shared.validation.ValidationErrors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Bildirimler ve "durtme". Bildirim simdilik yalnizca uygulama ici. */
@Service
class NotificationService {

	/** Ayni is icin ayni kisiye bu sure icinde ikinci kez durtme yok. */
	private static final int NUDGE_COOLDOWN_MINUTES = 3;

	private final UserNotificationRepository repository;
	private final NudgeDirectory directory;

	NotificationService(UserNotificationRepository repository, NudgeDirectory directory) {
		this.repository = repository;
		this.directory = directory;
	}

	/** En yeni once. */
	@Transactional(readOnly = true)
	Page<UserNotification> page(long userId, boolean unread, int page, int size) {
		PageRequest request = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
		return unread
			? repository.findByUserIdAndReadAtIsNull(userId, request)
			: repository.findByUserId(userId, request);
	}

	@Transactional(readOnly = true)
	List<Notification> withSenders(List<UserNotification> rows) {
		Map<Long, Sender> senders = directory.senders(rows.stream().map(UserNotification::getSentByUserId).toList());
		return rows.stream().map(row -> Notification.of(row, sender(senders, row))).toList();
	}

	@Transactional(readOnly = true)
	long unreadCount(long userId) {
		return repository.countByUserIdAndReadAtIsNull(userId);
	}

	/** Baskasinin bildirimi okunmus sayilamaz. */
	@Transactional
	Notification markRead(long id, long userId) {
		UserNotification notification = repository.findById(id)
			.orElseThrow(() -> new ModelNotFoundException("UserNotification", id));
		if (notification.getUserId() != userId) {
			throw new BusinessRuleException("Bu bildirim size ait değil.");
		}
		notification.markRead();
		repository.flush();
		return withSenders(List.of(notification)).getFirst();
	}

	@Transactional
	int markAllRead(long userId) {
		return repository.markAllRead(userId, Timestamps.now());
	}

	/**
	 * Varsayilan olarak isin sorumlu rolundeki herkes (durten haric), {@code userId} verilirse yalnizca o kisi.
	 */
	@Transactional
	NudgeResult nudge(CurrentUser actor, NudgeRequest request) {
		assertUserExists(request.userId());
		NudgeSubject subject = request.subjectType();
		long subjectId = request.subjectId();
		String label = directory.label(subject, subjectId)
			.orElseThrow(() -> new BusinessRuleException("Uyarı gönderilecek kayıt bulunamadı."));
		List<Long> recipients = recipients(actor, subject.role(), request.userId());
		assertNotJustNudged(subject, subjectId, recipients);
		String body = body(label, request.note());
		List<UserNotification> created = new ArrayList<>();
		for (long recipient : recipients) {
			created.add(repository.save(UserNotification.nudge(recipient, actor.id(), subject, subjectId, body)));
		}
		return new NudgeResult(created.size(), withSenders(created));
	}

	@Transactional(readOnly = true)
	List<NudgeTarget> targets(CurrentUser actor) {
		return directory.targets(actor.id());
	}

	private static Sender sender(Map<Long, Sender> senders, UserNotification row) {
		return row.getSentByUserId() == null ? null : senders.get(row.getSentByUserId());
	}

	private void assertUserExists(Long userId) {
		if (userId != null && !directory.userExists(userId)) {
			ValidationErrors errors = new ValidationErrors();
			errors.add("user_id", LaravelMessages.EXISTS);
			errors.throwIfAny();
		}
	}

	private List<Long> recipients(CurrentUser actor, String role, Long userId) {
		if (userId != null) {
			if (userId == actor.id()) {
				throw new BusinessRuleException("Kendinizi uyaramazsınız.");
			}
			return List.of(userId);
		}
		List<Long> users = directory.usersWithRole(role, actor.id());
		if (users.isEmpty()) {
			throw new BusinessRuleException("Bu iş '" + role + "' rolüne gidiyor ama o rolde uyarılacak kullanıcı yok.");
		}
		return users;
	}

	private void assertNotJustNudged(NudgeSubject subject, long subjectId, List<Long> recipients) {
		LocalDateTime since = Timestamps.now().minusMinutes(NUDGE_COOLDOWN_MINUTES);
		if (repository.existsBySubjectTypeAndSubjectIdAndUserIdInAndCreatedAtGreaterThanEqual(subject.value(), subjectId,
			recipients, since)) {
			throw new BusinessRuleException("Bu iş için az önce uyarı gönderildi. " + NUDGE_COOLDOWN_MINUTES
				+ " dakika sonra tekrar deneyebilirsiniz.");
		}
	}

	/** Hangi kayit oldugu, varsa not; aralarinda " • ". */
	private static String body(String label, String note) {
		return note == null || note.isBlank() ? label : label + " • " + note.strip();
	}

}
