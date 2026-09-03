package com.freestyleperu.aplicacion.shared.security;

import com.freestyleperu.aplicacion.ia.OpenRouterProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@EnableConfigurationProperties({ JwtProperties.class, CorsProperties.class, AccountLockProperties.class, OpenRouterProperties.class })
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        configuration.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Idempotency-Key", "X-Tenant-Slug"));
        configuration.setExposedHeaders(List.of("Location"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        // El navegador manda "Origin" tambien cuando la peticion es del mismo origen (lo
        // hace en todo POST/PUT/DELETE), y Spring trata como CORS cualquier peticion con
        // esa cabecera. Como los frontends se sirven detras del mismo nginx que expone
        // /api, eso convertia en 403 al login legitimo desde cualquier host no enumerado
        // -- la IP de LAN al probar desde un movil, o un dominio nuevo en produccion.
        //
        // Aqui se acepta el Origin solo cuando coincide con el host por el que llego la
        // peticion. Una pagina de otro dominio no puede falsear su Origin, asi que sigue
        // bloqueada; y un cliente que no sea navegador nunca estuvo sujeto a CORS.
        return request -> {
            CorsConfiguration resolved = source.getCorsConfiguration(request);
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            if (origin == null || !origin.equalsIgnoreCase(origenDeLaPeticion(request))) {
                return resolved;
            }
            CorsConfiguration mismoOrigen = resolved == null
                    ? new CorsConfiguration()
                    : new CorsConfiguration(resolved);
            mismoOrigen.addAllowedOrigin(origin);
            return mismoOrigen;
        };
    }

    /**
     * Reconstruye el origen por el que el navegador alcanzo la aplicacion. Detras del
     * proxy hay que mirar las cabeceras reenviadas: el socket local siempre dice
     * http://backend:8080, que no es lo que el navegador uso.
     */
    private static String origenDeLaPeticion(HttpServletRequest request) {
        String host = primerValor(request.getHeader("X-Forwarded-Host"));
        if (host == null) {
            host = request.getHeader(HttpHeaders.HOST);
        }
        if (host == null) {
            return null;
        }
        String scheme = primerValor(request.getHeader("X-Forwarded-Proto"));
        if (scheme == null) {
            scheme = request.getScheme();
        }
        return scheme + "://" + host;
    }

    /** Las cabeceras reenviadas pueden encadenar varios saltos separados por coma. */
    private static String primerValor(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        int coma = header.indexOf(',');
        return (coma < 0 ? header : header.substring(0, coma)).trim();
    }

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            SubscriptionStatusFilter subscriptionStatusFilter,
            OpsApiKeyAuthenticationFilter opsApiKeyAuthenticationFilter,
            TenantResolutionFilter tenantResolutionFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/store/auth/register", "/api/store/auth/login", "/api/store/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/store/catalog/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/store/complaints").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/store/complaints/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/store/assistant/chat").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/store/assistant/enabled").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/system/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/system/branding").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/system/subscription").permitAll()
                        // Los comprobantes de suscripción llevan nombres y montos: nunca por la
                        // ruta estática. Se sirven por un endpoint que exige ser operador de
                        // plataforma. El resto de /uploads (productos, logos, banners) sí es
                        // público porque la tienda lo necesita sin sesión.
                        .requestMatchers(HttpMethod.GET, "/uploads/suscripciones/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(opsApiKeyAuthenticationFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(subscriptionStatusFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(tenantResolutionFilter, OpsApiKeyAuthenticationFilter.class);
        return http.build();
    }
}
