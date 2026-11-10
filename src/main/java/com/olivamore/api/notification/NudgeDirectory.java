package com.olivamore.api.notification;

import com.olivamore.api.finishedproduct.FinishedProductDirectory;
import com.olivamore.api.identity.UserDirectory;
import com.olivamore.api.notification.NotificationResponses.NudgeTarget;
import com.olivamore.api.notification.NotificationResponses.Sender;
import com.olivamore.api.production.ProductionDirectory;
import com.olivamore.api.sales.SalesDirectory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Durtulen isler ve kullanicilar: diger modullerin arayuzlerinden salt okunur. */
@Component
class NudgeDirectory {

	private final UserDirectory users;
	private final FinishedProductDirectory finishedProducts;
	private final SalesDirectory sales;
	private final ProductionDirectory production;

	NudgeDirectory(UserDirectory users, FinishedProductDirectory finishedProducts, SalesDirectory sales,
		ProductionDirectory production) {
		this.users = users;
		this.finishedProducts = finishedProducts;
		this.sales = sales;
		this.production = production;
	}

	/** Isin bildirimde gorunen adi; kayit yoksa bos. */
	Optional<String> label(NudgeSubject subject, long id) {
		return switch (subject) {
			case FINISHED_PRODUCT_RUN -> finishedProducts.runLabel(id);
			case ORDER -> sales.orderLabel(id);
			case FINISHED_PRODUCT -> finishedProducts.productName(id);
			case PRODUCTION_BATCH -> production.batchCode(id);
			case BATCH_PROCESS -> production.processLabel(id);
		};
	}

	boolean userExists(long id) {
		return users.exists(id);
	}

	/** Roldeki kullanicilar, {@code exceptUserId} haric. */
	List<Long> usersWithRole(String role, long exceptUserId) {
		return users.idsWithRole(role, exceptUserId);
	}

	Map<Long, Sender> senders(Collection<Long> userIds) {
		return users.users(userIds).values().stream().collect(Collectors.toMap(UserDirectory.User::id,
			user -> new Sender(user.id(), user.name())));
	}

	/** Sorumlu rollerdeki kullanicilar (durten haric), ada gore, rolleriyle. */
	List<NudgeTarget> targets(long actorId) {
		return users.withAnyRole(NudgeSubject.roles(), actorId).stream()
			.map(user -> new NudgeTarget(user.id(), user.name(), user.email(), user.roles()))
			.toList();
	}

}
