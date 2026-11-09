package com.olivamore.api.sales.customer;

import com.olivamore.api.shared.error.BusinessRuleException;
import com.olivamore.api.shared.error.HttpStatusException;
import com.olivamore.api.shared.error.LaravelValidationException;
import com.olivamore.api.shared.error.ModelNotFoundException;
import com.olivamore.api.shared.persistence.DeletionGuard;
import com.olivamore.api.shared.validation.LaravelMessages;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Musteriler, adresler ve formul tercihleri. */
@Service
class CustomerService {

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final CustomerRepository customers;
	private final CustomerAddressRepository addresses;
	private final CustomerPreferredRecipeRepository preferred;
	private final JdbcClient jdbc;

	CustomerService(CustomerRepository customers, CustomerAddressRepository addresses,
		CustomerPreferredRecipeRepository preferred, JdbcClient jdbc) {
		this.customers = customers;
		this.addresses = addresses;
		this.preferred = preferred;
		this.jdbc = jdbc;
	}

	@Transactional(readOnly = true)
	List<CustomerResponse> all(Boolean isActive) {
		return withAddresses(isActive == null ? customers.findAll(NEWEST_FIRST)
			: customers.findByActive(isActive, NEWEST_FIRST));
	}

	@Transactional(readOnly = true)
	Page<CustomerResponse> page(Boolean isActive, int page, int size) {
		PageRequest request = PageRequest.of(page - 1, size, NEWEST_FIRST);
		Page<Customer> rows = isActive == null ? customers.findAll(request) : customers.findByActive(isActive, request);
		Map<Long, CustomerResponse> responses = withAddresses(rows.getContent()).stream()
			.collect(Collectors.toMap(CustomerResponse::id, response -> response));
		return rows.map(customer -> responses.get(customer.getId()));
	}

	@Transactional(readOnly = true)
	CustomerResponse get(long id) {
		return withAddresses(List.of(find(id))).getFirst();
	}

	@Transactional
	long create(CreateCustomerRequest request) {
		Customer customer = customers.save(new Customer(request));
		if (request.addresses() != null) {
			request.addresses().forEach(address -> saveAddress(customer.getId(), address));
		}
		return customer.getId();
	}

	@Transactional
	void update(long id, UpdateCustomerRequest request) {
		find(id).apply(request);
	}

	@Transactional
	void delete(long id) {
		Customer customer = find(id);
		if (customers.countOrders(id) > 0) {
			throw new BusinessRuleException("Bu müşteriye ait siparişler bulunduğu için silinemez.");
		}
		customers.delete(customer);
	}

	/** En yeni once. */
	@Transactional(readOnly = true)
	List<CustomerAddressResponse> addresses(long customerId) {
		find(customerId);
		return addresses.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
			.map(CustomerAddressResponse::of).toList();
	}

	@Transactional
	CustomerAddressResponse addAddress(long customerId, CreateCustomerAddressRequest request) {
		find(customerId);
		return CustomerAddressResponse.of(saveAddress(customerId, request));
	}

	@Transactional
	CustomerAddressResponse updateAddress(long customerId, long addressId, UpdateCustomerAddressRequest request) {
		CustomerAddress address = findAddress(customerId, addressId);
		if (Boolean.TRUE.equals(request.isDefault())) {
			addresses.clearDefault(customerId, addressId);
		}
		address.apply(request);
		addresses.flush();
		return CustomerAddressResponse.of(address);
	}

	/** Siparise bagli adres silinmez; varsayilan adres silinirse en son eklenen varsayilan olur. */
	@Transactional
	void deleteAddress(long customerId, long addressId) {
		CustomerAddress address = findAddress(customerId, addressId);
		DeletionGuard.of("'" + address.getLabel() + "' adresi")
			.usedBy("sipariş", addresses.countOrders(addressId))
			.check();
		boolean wasDefault = address.isDefaultAddress();
		addresses.delete(address);
		addresses.flush();
		if (wasDefault) {
			addresses.findFirstByCustomerIdOrderByIdDesc(customerId).ifPresent(CustomerAddress::markDefault);
		}
	}

	/** Ayni formul tekrar eklenirse yalnizca notu guncellenir. */
	@Transactional
	void addPreferredRecipe(long customerId, PreferredRecipeRequest request) {
		find(customerId);
		long recipeId = request.finishedProductRecipeId();
		if (recipeName(recipeId) == null) {
			throw LaravelValidationException.of("finished_product_recipe_id",
				LaravelMessages.format(LaravelMessages.EXISTS, "finished_product_recipe_id"));
		}
		preferred.findByCustomerIdAndFinishedProductRecipeId(customerId, recipeId).ifPresentOrElse(
			existing -> existing.changeNote(request.note()),
			() -> preferred.save(new CustomerPreferredRecipe(customerId, recipeId, request.note())));
	}

	@Transactional
	void removePreferredRecipe(long customerId, long recipeId) {
		find(customerId);
		String name = recipeName(recipeId);
		if (name == null) {
			throw new ModelNotFoundException("FinishedProductRecipe", recipeId);
		}
		CustomerPreferredRecipe row = preferred.findByCustomerIdAndFinishedProductRecipeId(customerId, recipeId)
			.orElseThrow(() -> new BusinessRuleException("'" + name + "' bu müşterinin tercihlerinde değil."));
		preferred.delete(row);
	}

	@Transactional(readOnly = true)
	void ensureExists(long id) {
		find(id);
	}

	private CustomerAddress saveAddress(long customerId, CreateCustomerAddressRequest request) {
		if (Boolean.TRUE.equals(request.isDefault())) {
			addresses.clearDefault(customerId, 0);
		}
		return addresses.save(new CustomerAddress(customerId, request));
	}

	private List<CustomerResponse> withAddresses(List<Customer> rows) {
		Map<Long, List<CustomerAddressResponse>> byCustomer = addresses
			.findByCustomerIdInOrderByIdAsc(rows.stream().map(Customer::getId).toList()).stream()
			.map(CustomerAddressResponse::of)
			.collect(Collectors.groupingBy(CustomerAddressResponse::customerId));
		return rows.stream()
			.map(customer -> CustomerResponse.of(customer, byCustomer.getOrDefault(customer.getId(), List.of())))
			.toList();
	}

	private Customer find(long id) {
		return customers.findById(id).orElseThrow(() -> new ModelNotFoundException("Customer", id));
	}

	private CustomerAddress findAddress(long customerId, long addressId) {
		find(customerId);
		CustomerAddress address = addresses.findById(addressId)
			.orElseThrow(() -> new ModelNotFoundException("CustomerAddress", addressId));
		if (address.getCustomerId() != customerId) {
			throw new HttpStatusException(404, "Bu adres bu müşteriye ait değil.");
		}
		return address;
	}

	private String recipeName(long recipeId) {
		return jdbc.sql("select name from finished_product_recipes where id = ?")
			.param(recipeId)
			.query(String.class)
			.optional()
			.orElse(null);
	}

}
