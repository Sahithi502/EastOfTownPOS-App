package com.eastoftown.pos;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class LocalSecurity extends org.springframework.web.filter.OncePerRequestFilter {
    private final String token = System.getenv("EOT_API_TOKEN");
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (token == null || token.length() < 32) { res.sendError(503,"Start the backend through the desktop launcher"); return; }
        String given = req.getHeader("X-EOT-Token");
        if (given == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) { res.sendError(401); return; }
        chain.doFilter(req,res);
    }
}
