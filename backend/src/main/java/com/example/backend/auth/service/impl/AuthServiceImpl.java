package com.example.backend.auth.service.impl;

import com.example.backend.auth.dto.AuthResponse;
import com.example.backend.auth.dto.LoginRequest;
import com.example.backend.auth.dto.SignupRequest;
import com.example.backend.auth.service.AuthService;
import com.example.backend.exception.UnauthorizedException;
import com.example.backend.exception.UserAlreadyExistsException;
import com.example.backend.security.JwtUtils;
import com.example.backend.user.dto.UserDTO;
import com.example.backend.user.entity.User;
import com.example.backend.user.mapper.UserMapper;
import com.example.backend.user.repository.UserRepository;
import com.example.backend.utils.PhoneNumberNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        String username = request.getUsername().trim();
        String phoneNumber = PhoneNumberNormalizer.normalize(request.getPhoneNumber());

        if (userRepository.existsByUsername(username)
                || userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new UserAlreadyExistsException("Username or phone number is already registered");
        }

        User user = new User();
        user.setUsername(username);
        user.setPhoneNumber(phoneNumber);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new UserAlreadyExistsException("Username or phone number is already registered");
        }

        return authenticate(user, request.getPassword());
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String phoneNumber = PhoneNumberNormalizer.normalize(request.getPhoneNumber());

        // Phone-number lookup first because AuthenticationManager authenticates by username
        User user = userRepository.findByPhoneNumber(phoneNumber)
                .orElseThrow(() -> new BadCredentialsException("Invalid phone number or password"));

        return authenticate(user, request.getPassword());
    }

    @Override
    public UserDTO getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetails userDetails)) {
            throw new UnauthorizedException("Invalid authentication");
        }

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        return userMapper.toDto(user);
    }

    private AuthResponse authenticate(User user, String rawPassword) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getUsername(), rawPassword));

            String jwt = jwtUtils.generateJwtToken(authentication);

            AuthResponse response = new AuthResponse();
            response.setToken(jwt);
            response.setUser(userMapper.toDto(user));

            return response;
        } catch (AuthenticationException ex) {
            throw new BadCredentialsException("Invalid phone number or password");
        }
    }
}