package com.commerceops.customer.service;

import com.commerceops.customer.domain.Customer;
import com.commerceops.customer.repository.CustomerRepository;
import com.commerceops.customer.security.JwtService;
import com.commerceops.customer.service.exception.CustomerNotFoundException;
import com.commerceops.customer.service.exception.EmailAlreadyRegisteredException;
import com.commerceops.customer.service.exception.InvalidCredentialsException;
import com.commerceops.customer.web.dto.AuthResponse;
import com.commerceops.customer.web.dto.CustomerProfileResponse;
import com.commerceops.customer.web.dto.LoginRequest;
import com.commerceops.customer.web.dto.RegisterRequest;
import com.commerceops.customer.web.dto.UpdateProfileRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CustomerAuthService {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public CustomerAuthService(CustomerRepository customerRepository,
                               PasswordEncoder passwordEncoder,
                               JwtService jwtService) {
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim();
        if (customerRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        Instant now = Instant.now();
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID().toString());
        customer.setEmail(email);
        customer.setPasswordHash(passwordEncoder.encode(request.password()));
        customer.setDisplayName(request.displayName().trim());
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        customerRepository.save(customer);

        return toAuth(customer);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Customer customer = customerRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return toAuth(customer);
    }

    @Transactional(readOnly = true)
    public CustomerProfileResponse me(String customerId) {
        return toProfile(requireCustomer(customerId));
    }

    /**
     * Idempotent provision for OIDC: create a local profile keyed by Keycloak {@code sub}
     * / {@code customer_id} claim when missing.
     */
    @Transactional
    public CustomerProfileResponse ensureOidcProfile(String customerId, String email, String displayName) {
        return customerRepository.findById(customerId)
                .map(CustomerAuthService::toProfile)
                .orElseGet(() -> {
                    Instant now = Instant.now();
                    Customer customer = new Customer();
                    customer.setId(customerId);
                    customer.setEmail(email != null && !email.isBlank()
                            ? email.trim()
                            : customerId + "@oidc.local");
                    // Unusable local password — credentials live in Keycloak.
                    customer.setPasswordHash(passwordEncoder.encode("oidc-disabled-" + UUID.randomUUID()));
                    customer.setDisplayName(displayName != null && !displayName.isBlank()
                            ? displayName.trim()
                            : customerId);
                    customer.setCreatedAt(now);
                    customer.setUpdatedAt(now);
                    customerRepository.save(customer);
                    return toProfile(customer);
                });
    }

    @Transactional
    public CustomerProfileResponse updateProfile(String customerId, UpdateProfileRequest request) {
        Customer customer = requireCustomer(customerId);
        boolean changed = false;

        if (request.displayName() != null && !request.displayName().isBlank()) {
            String name = request.displayName().trim();
            if (!name.equals(customer.getDisplayName())) {
                customer.setDisplayName(name);
                changed = true;
            }
        }

        boolean wantsPasswordChange = request.newPassword() != null && !request.newPassword().isBlank();
        if (wantsPasswordChange) {
            if (request.currentPassword() == null || request.currentPassword().isBlank()) {
                throw new IllegalArgumentException("Current password is required to set a new password");
            }
            if (!passwordEncoder.matches(request.currentPassword(), customer.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            customer.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            changed = true;
        }

        if (changed) {
            customer.setUpdatedAt(Instant.now());
            customerRepository.save(customer);
        }
        return toProfile(customer);
    }

    public Customer requireCustomer(String customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new CustomerNotFoundException(customerId));
    }

    private AuthResponse toAuth(Customer customer) {
        String token = jwtService.issueToken(customer.getId(), customer.getEmail());
        return new AuthResponse(token, toProfile(customer));
    }

    private static CustomerProfileResponse toProfile(Customer customer) {
        return new CustomerProfileResponse(
                customer.getId(),
                customer.getEmail(),
                customer.getDisplayName(),
                customer.getCreatedAt());
    }
}
