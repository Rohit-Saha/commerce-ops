package com.commerceops.customer.service;

import com.commerceops.customer.domain.CustomerAddress;
import com.commerceops.customer.repository.CustomerAddressRepository;
import com.commerceops.customer.service.exception.AddressNotFoundException;
import com.commerceops.customer.web.dto.AddressRequest;
import com.commerceops.customer.web.dto.AddressResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AddressBookService {

    private final CustomerAddressRepository addressRepository;
    private final CustomerAuthService customerAuthService;

    public AddressBookService(CustomerAddressRepository addressRepository,
                              CustomerAuthService customerAuthService) {
        this.addressRepository = addressRepository;
        this.customerAuthService = customerAuthService;
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> list(String customerId) {
        customerAuthService.requireCustomer(customerId);
        return addressRepository.findByCustomerIdOrderByIsDefaultDescCreatedAtAsc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AddressResponse get(String customerId, String addressId) {
        return toResponse(requireOwned(customerId, addressId));
    }

    @Transactional
    public AddressResponse create(String customerId, AddressRequest request) {
        customerAuthService.requireCustomer(customerId);
        Instant now = Instant.now();
        long existing = addressRepository.countByCustomerId(customerId);
        boolean makeDefault = existing == 0 || Boolean.TRUE.equals(request.isDefault());

        if (makeDefault) {
            addressRepository.clearDefaultForCustomer(customerId);
        }

        CustomerAddress address = new CustomerAddress();
        address.setId(UUID.randomUUID().toString());
        address.setCustomerId(customerId);
        applyFields(address, request);
        address.setDefault(makeDefault);
        address.setCreatedAt(now);
        address.setUpdatedAt(now);
        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public AddressResponse update(String customerId, String addressId, AddressRequest request) {
        CustomerAddress address = requireOwned(customerId, addressId);
        applyFields(address, request);
        if (Boolean.TRUE.equals(request.isDefault()) && !address.isDefault()) {
            addressRepository.clearDefaultForCustomer(customerId);
            address.setDefault(true);
        }
        address.setUpdatedAt(Instant.now());
        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public void delete(String customerId, String addressId) {
        CustomerAddress address = requireOwned(customerId, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);
        if (wasDefault) {
            addressRepository.findFirstByCustomerIdAndIdNotOrderByCreatedAtAsc(customerId, addressId)
                    .ifPresent(next -> {
                        next.setDefault(true);
                        next.setUpdatedAt(Instant.now());
                        addressRepository.save(next);
                    });
        }
    }

    @Transactional
    public AddressResponse setDefault(String customerId, String addressId) {
        CustomerAddress address = requireOwned(customerId, addressId);
        if (!address.isDefault()) {
            addressRepository.clearDefaultForCustomer(customerId);
            address.setDefault(true);
            address.setUpdatedAt(Instant.now());
            addressRepository.save(address);
        }
        return toResponse(address);
    }

    private CustomerAddress requireOwned(String customerId, String addressId) {
        customerAuthService.requireCustomer(customerId);
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }

    private void applyFields(CustomerAddress address, AddressRequest request) {
        address.setRecipientName(request.recipientName().trim());
        address.setLine1(request.line1().trim());
        address.setLine2(blankToNull(request.line2()));
        address.setCity(request.city().trim());
        address.setState(request.state().trim());
        address.setPostalCode(request.postalCode().trim());
        String country = request.country() == null || request.country().isBlank()
                ? "US"
                : request.country().trim();
        address.setCountry(country);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private AddressResponse toResponse(CustomerAddress address) {
        return new AddressResponse(
                address.getId(),
                address.getRecipientName(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry(),
                address.isDefault(),
                address.getCreatedAt(),
                address.getUpdatedAt());
    }
}
