package com.commerceops.customer.repository;

import com.commerceops.customer.domain.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, String> {

    List<CustomerAddress> findByCustomerIdOrderByIsDefaultDescCreatedAtAsc(String customerId);

    Optional<CustomerAddress> findByIdAndCustomerId(String id, String customerId);

    long countByCustomerId(String customerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE CustomerAddress a SET a.isDefault = false WHERE a.customerId = :customerId AND a.isDefault = true")
    void clearDefaultForCustomer(@Param("customerId") String customerId);

    Optional<CustomerAddress> findFirstByCustomerIdAndIdNotOrderByCreatedAtAsc(String customerId, String excludeId);
}
