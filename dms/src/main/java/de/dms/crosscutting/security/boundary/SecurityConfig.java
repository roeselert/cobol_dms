package de.dms.crosscutting.security.boundary;

import de.dms.crosscutting.platform.control.DmsProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless security filter chain: every protected route requires a valid
 * OIDC bearer token (401 before any handler runs, S-2). The RSS feed
 * authenticates via its own opaque token inside the feeds BC; static assets
 * and health probes are public.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DmsProperties properties) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                        "/", "/index.html", "/css/**", "/js/**", "/favicon.ico",
                        "/actuator/health/**",
                        "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
                        "/api/v1/feeds/inbox.rss").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(handling -> handling.authenticationEntryPoint(
                    (request, response, ex) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED)));

        if (properties.security().devMode()) {
            LOGGER.warn("SECURITY MODE 'dev' ACTIVE — the X-Dev-User header is trusted as the caller's "
                    + "identity without any credential check. Never expose this instance publicly; "
                    + "production deployments must run with DMS_SECURITY_MODE=oidc (the default).");
            http.addFilterBefore(new DevAuthFilter(), UsernamePasswordAuthenticationFilter.class);
        } else {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
        }
        return http.build();
    }
}
