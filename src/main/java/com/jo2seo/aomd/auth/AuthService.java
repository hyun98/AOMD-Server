package com.jo2seo.aomd.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jo2seo.aomd.auth.dto.response.KakaoLoginResponse;
import com.jo2seo.aomd.security.TokenProvider;
import com.jo2seo.aomd.user.User;
import com.jo2seo.aomd.user.UserRepository;
import com.jo2seo.aomd.user.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {
    private final TokenProvider tokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /*
    TODO: 이미지 서버에 이미지 업로드
    */
    public KakaoLoginResponse kakaoLogin(final String authorizationCode, final String callbackUrl) throws JsonProcessingException {
        /* 카카오 토큰 발급 */
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", "5ea91fb99e9476b7c17cf33a8c3cbb66");
        params.add("redirect_uri", callbackUrl);
        params.add("code", authorizationCode);

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<MultiValueMap<String, String>> entity = new HttpEntity<>(params, headers);
        RestTemplate rt = new RestTemplate();

        ResponseEntity<String> response = rt.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                entity,
                String.class
        );
        ObjectMapper objectMapper = new ObjectMapper();
        Map json = objectMapper.readValue(response.getBody(), Map.class);
        String accessToken = (String)json.get("access_token");

        /* 카카오 토큰으로 정보 가져오기 */
        headers.clear();
        headers.add("Authorization", String.format("Bearer %s", accessToken));
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        response = rt.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.GET,
                entity,
                String.class
        );

        json = objectMapper.readValue(response.getBody(), Map.class);
        Map kakao_account = (Map) json.get("kakao_account");
        Map profile = (Map) kakao_account.get("profile");

        String email = (String) kakao_account.get("email");
        String password = UUID.randomUUID().toString();
        String imageUrl = (String) profile.get("thumbnail_image_url");
        String nickname = (String) profile.get("nickname");

        userRepository.signup(new User(email, passwordEncoder.encode(password), imageUrl, nickname, UserRole.USER));

        return new KakaoLoginResponse(email, password);
    }

    @Transactional(readOnly = true)
    public String genJwt(final String email, final String password) {
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                new UsernamePasswordAuthenticationToken(email, password);

        Authentication authentication = authenticationManagerBuilder
                .getObject()
                .authenticate(usernamePasswordAuthenticationToken);

        return tokenProvider.createToken(authentication);
    }
}