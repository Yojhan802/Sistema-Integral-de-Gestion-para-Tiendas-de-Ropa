package com.freestyleperu.aplicacion.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    /**
     * EventSource/WebSocket del navegador no pueden mandar headers propios en la conexión inicial —
     * estas dos rutas de streaming SSE aceptan el mismo access token por query param como única excepción.
     */
    private static final List<String> QUERY_PARAM_TOKEN_URIS =
            List.of("/api/notifications/stream", "/api/store/notifications/stream");

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        String token = null;
        if (header != null && header.startsWith(PREFIX)) {
            token = header.substring(PREFIX.length());
        } else if (QUERY_PARAM_TOKEN_URIS.contains(request.getRequestURI())) {
            token = request.getParameter("token");
        }
        if (token != null) {
            AuthenticatedUser authenticatedUser = jwtService.parse(token);
            Long tenantResuelto = TenantContext.get();
            boolean tokenPerteneceAlTenantDeLaPeticion = authenticatedUser != null
                    && (tenantResuelto == null || tenantResuelto.equals(authenticatedUser.tenantId()));
            if (tokenPerteneceAlTenantDeLaPeticion
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                List<GrantedAuthority> authorities = authenticatedUser.authorities().stream()
                        .map(SimpleGrantedAuthority::new)
                        .map(GrantedAuthority.class::cast)
                        .toList();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(authenticatedUser, null, authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }
}
