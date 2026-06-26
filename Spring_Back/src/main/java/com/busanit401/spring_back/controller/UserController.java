package com.busanit401.spring_back.controller;

import com.busanit401.spring_back.domain.User;
import com.busanit401.spring_back.dto.user.*;
import com.busanit401.spring_back.enums.Role;
import com.busanit401.spring_back.security.auth.CustomUserDetails;
import com.busanit401.spring_back.security.auth.CustomUserDetailsService;
import com.busanit401.spring_back.domain.service.TokenBlacklistService;
import com.busanit401.spring_back.domain.service.UserService;
import com.busanit401.spring_back.util.JwtUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final CustomUserDetailsService customUserDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtUtil jwtUtil;

    /**
     * 로그아웃
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request,
                                    @CookieValue(value = "refresh_token", required = false) String refreshToken) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String accessToken = authorizationHeader.substring(7);
            long expirationTime = jwtUtil.extractAllClaims(accessToken).getExpiration().getTime() - System.currentTimeMillis();
            tokenBlacklistService.addToBlacklist(accessToken, expirationTime);
        }

        // [개선] 로그아웃 시 Refresh Token도 블랙리스트에 넣어 추가 사용 방지
        if (refreshToken != null && !jwtUtil.isTokenExpired(refreshToken)) {
            long refreshExpTime = jwtUtil.extractAllClaims(refreshToken).getExpiration().getTime() - System.currentTimeMillis();
            tokenBlacklistService.addToBlacklist(refreshToken, refreshExpTime);
        }

        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().body("로그아웃 되었습니다.");
    }

    /**
     * 회원가입
     */
    @PostMapping
    public ResponseEntity<?> registerUser(@RequestBody @Valid UserReq dto, // [개선] @Valid 추가
                                          BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return buildValidationErrorResponse(bindingResult);
        }
        UserResp userResponse = userService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(userResponse);
    }

    /**
     * 마이페이지
     */
    @GetMapping
    public ResponseEntity<?> getUserProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserResp userResponse = userService.getUser(userDetails.getId());
        return ResponseEntity.ok(userResponse);
    }

    /**
     * 회원 수정
     */
    @PatchMapping
    public ResponseEntity<?> updateUserProfile(@AuthenticationPrincipal CustomUserDetails userDetails,
                                               @RequestBody @Valid UserSimpleReq dto, // [개선] @Valid 추가
                                               BindingResult bindingResult) {
        log.info("PATCH 요청 도착");
        log.info("userId = {}", userDetails.getId());
        log.info("username = {}", dto.getUsername());
        log.info("nickname = {}", dto.getNickname());
        if (bindingResult.hasErrors()) {
            return buildValidationErrorResponse(bindingResult);
        }
        userService.updateUser(userDetails.getId(), dto);
        UserResp updatedUser = userService.getUser(userDetails.getId());
        return ResponseEntity.ok(updatedUser);
    }

    /**
     * 비밀번호 변경
     */
    @PatchMapping("/password")
    public ResponseEntity<?> updatePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid PasswordUpdateReq request,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            List<String> errors = bindingResult.getAllErrors()
                    .stream()
                    .map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .toList();

            return ResponseEntity.badRequest().body(errors);
        }

        userService.updatePassword(userDetails.getId(), request);
        return ResponseEntity.ok(
                "비밀번호가 변경되었습니다."
        );
    }
    /**
     * 회원 탈퇴
     */
    @DeleteMapping
    public ResponseEntity<?> deleteUser(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.deleteUser(userDetails.getId());
        return ResponseEntity.ok(Map.of("message", "회원 탈퇴가 완료되었습니다."));
    }

    /**
     * 소셜 로그인 (구글/카카오 통합 처리 가능)
     */
    @PostMapping("/google-login")
    public ResponseEntity<?> loginWithGoogle(@RequestBody UserInfo userInfo, HttpServletResponse response) {
        return handleSocialLogin(userInfo, response);
    }

    @PostMapping("/kakao-login")
    public ResponseEntity<?> loginWithKakao(@RequestBody UserInfo userInfo, HttpServletResponse response) {
        return handleSocialLogin(userInfo, response);
    }

    /**
     * 이메일로 가입 여부 확인 (소셜 로그인 신규/기존 분기용)
     */
    @GetMapping("/exists")
    public ResponseEntity<?> checkEmailExists(@RequestParam String email) {
        boolean exists = userService.existsByEmail(email);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    /**
     * refreshToken 갱신
     */
    @PostMapping("/refresh-token")
    public ResponseEntity<?> refreshAccessToken(
            @CookieValue(value = "refresh_token", required = false) String refreshToken) { // [개선] @CookieValue로 한 줄 축약

        if (refreshToken == null || jwtUtil.isTokenExpired(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid or expired refresh token");
        }

        if (tokenBlacklistService.isBlacklisted(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Refresh token is blacklisted.");
        }

        String username = jwtUtil.extractUsername(refreshToken);
        String newAccessToken = jwtUtil.generateToken(username);

        return ResponseEntity.ok("{\"accessToken\": \"" + newAccessToken + "\"}");
    }

    private ResponseEntity<?> buildValidationErrorResponse(BindingResult bindingResult) {
        List<String> errorMessages = bindingResult.getAllErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.toList());
        return ResponseEntity.badRequest().body(errorMessages);
    }

    private ResponseEntity<?> handleSocialLogin(UserInfo userInfo, HttpServletResponse response) {
        try {
            CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(userInfo.getUsername());
            User user = userDetails.getUser();

            if (user.getRole() == Role.SOCIAL) {
                return generateJwtResponse(user.getUsername(), response);
            } else {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 존재하는 이메일입니다. 일반 로그인을 사용하세요.");
            }
        } catch (UsernameNotFoundException e) {
            UserResp newUserResponse = userService.createUser(UserReq.from(userInfo));
            return generateJwtResponse(newUserResponse.getUsername(), response);
        }
    }

    // [개선] 소셜 로그인도 일반 로그인 필터와 일치하게 Refresh Token은 HttpOnly 쿠키로 설정
    private ResponseEntity<?> generateJwtResponse(String username, HttpServletResponse response) {
        String accessToken = jwtUtil.generateToken(username);
        String refreshToken = jwtUtil.generateRefreshToken(username);

        Cookie refreshTokenCookie = new Cookie("refresh_token", refreshToken);
        refreshTokenCookie.setHttpOnly(true);
        refreshTokenCookie.setSecure(true);
        refreshTokenCookie.setPath("/");
        refreshTokenCookie.setMaxAge(Math.toIntExact(jwtUtil.getRefreshExpiration() / 1000)); // 밀리초 단위를 초 단위로 변경 시 주의
        response.addCookie(refreshTokenCookie);

        return ResponseEntity.ok("{\"accessToken\": \"" + accessToken + "\"}");
    }
}