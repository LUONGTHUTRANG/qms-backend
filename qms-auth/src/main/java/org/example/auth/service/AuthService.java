package org.example.auth.service;

import lombok.RequiredArgsConstructor;
import org.example.auth.dto.AuthResponse;
import org.example.auth.dto.LoginRequest;
import org.example.auth.dto.RefreshRequest;
import org.example.auth.dto.UserInfoDto;
import org.example.auth.entity.AppUser;
import org.example.auth.entity.RefreshToken;
import org.example.auth.repository.AppUserRepository;
import org.example.auth.repository.RefreshTokenRepository;
import org.example.auth.security.JwtUtil;
import org.example.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        AppUser user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));

        if (!user.getIsActive()) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "User account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String accessToken = jwtUtil.generateToken(user);
        RefreshToken refreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getTokenHash())
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .branchId(user.getBranchId())
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(RefreshRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(request.getRefreshToken())
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Invalid Refresh Token"));

        if (refreshToken.getRevokedAt() != null || refreshToken.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Refresh Token expired or revoked");
        }

        AppUser user = refreshToken.getUser();
        String newAccessToken = jwtUtil.generateToken(user);

        // Optionally rotate refresh token
        refreshToken.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(refreshToken);
        RefreshToken newRefreshToken = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getTokenHash())
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .branchId(user.getBranchId())
                .build();
    }

    private RefreshToken createRefreshToken(AppUser user) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(UUID.randomUUID().toString())
                .issuedAt(LocalDateTime.now())
                .expiredAt(LocalDateTime.now().plusDays(7)) // 7 days valid
                .build();
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserInfoDto getUserInfo(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "User not found"));

        return UserInfoDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .branchId(user.getBranchId())
                .build();
    }
}
