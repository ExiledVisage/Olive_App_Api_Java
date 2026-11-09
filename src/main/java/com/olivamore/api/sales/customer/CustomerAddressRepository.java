package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.time.Timestamps;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

	List<CustomerAddress> findByCustomerIdInOrderByIdAsc(Collection<Long> customerIds);

	List<CustomerAddress> findByCustomerIdOrderByCreatedAtDesc(long customerId);

	Optional<CustomerAddress> findFirstByCustomerIdOrderByIdDesc(long customerId);

	boolean existsByIdAndCustomerId(long id, long customerId);

	@Query(value = "select count(*) from orders where delivery_address_id = :id", nativeQuery = true)
	long countOrders(long id);

	@Modifying
	@Query("""
		update CustomerAddress a set a.defaultAddress = false, a.updatedAt = :now
		where a.customerId = :customerId and a.id <> :exceptId
		""")
	void clearDefault(long customerId, long exceptId, LocalDateTime now);

	default void clearDefault(long customerId, long exceptId) {
		clearDefault(customerId, exceptId, Timestamps.now());
	}

}
