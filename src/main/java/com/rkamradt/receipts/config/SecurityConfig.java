package com.rkamradt.receipts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Spring Security 6: set attribute name to null so the token is resolved
        // from the cookie without requiring a separate deferred token endpoint.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .authorizeHttpRequests(auth -> auth
                // Health probe must be open for K8s probes
                .requestMatchers("/actuator/health").permitAll()
                // All /api/** calls require a logged-in session
                .requestMatchers("/api/**").authenticated()
                // Static frontend files and OAuth2 endpoints are public
                .anyRequest().permitAll()
            )
            .csrf(csrf -> csrf
                // Browser-readable cookie so React can send X-XSRF-TOKEN header
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(csrfHandler)
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(ui -> ui.oidcUserService(oidcUserService()))
                .defaultSuccessUrl("/", true)
                .failureUrl("/?error=auth_failed")
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .invalidateHttpSession(true)
            )
            .exceptionHandling(ex -> ex
                // API calls get a 401 JSON-friendly response; browser navigations
                // get redirected into the OAuth2 flow.
                .authenticationEntryPoint((req, res, e) -> {
                    if (req.getRequestURI().startsWith("/api/")) {
                        res.sendError(401, "Unauthorized");
                    } else {
                        res.sendRedirect("/oauth2/authorization/google");
                    }
                })
                .accessDeniedHandler((req, res, e) ->
                    res.sendError(403, "Forbidden — must sign in with a @kamradtfamily.net account"))
            );

        return http.build();
    }

    /**
     * Enforce @kamradtfamily.net domain: belt-and-suspenders on top of the
     * "Internal" consent screen. Rejects if hd != kamradtfamily.net OR
     * email_verified != true.
     */
    @Bean
    public OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return request -> {
            OidcUser user = delegate.loadUser(request);
            String hd = user.getAttribute("hd");
            Boolean emailVerified = user.getAttribute("email_verified");
            if (!"kamradtfamily.net".equals(hd) || !Boolean.TRUE.equals(emailVerified)) {
                throw new OAuth2AuthenticationException("access_denied");
            }
            return user;
        };
    }
}
