package com.eastoftown.pos;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;

@Component
public class LocalSecurity extends org.springframework.web.filter.OncePerRequestFilter {
    private final String token;
    private final BackupService backups;
    public LocalSecurity(@org.springframework.beans.factory.annotation.Value("${eot.api-token:${EOT_API_TOKEN:}}") String token,BackupService backups){this.token=token;this.backups=backups;}
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (token == null || token.length() < 32) { res.sendError(503,"Start the backend through the desktop launcher"); return; }
        String given = req.getHeader("X-EOT-Token");
        if (given == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8))) { res.sendError(401); return; }
        if(!req.getMethod().equals("GET") && !req.getRequestURI().startsWith("/api/auth/") && !req.getRequestURI().equals("/api/backups/cancel-restore") && backups.restorePending()){
            res.setStatus(409);res.setContentType("application/json");res.getWriter().write("{\"message\":\"Restore is prepared. Restart the application or cancel the pending restore before making changes.\"}");return;
        }
        chain.doFilter(req,res);
    }
}
