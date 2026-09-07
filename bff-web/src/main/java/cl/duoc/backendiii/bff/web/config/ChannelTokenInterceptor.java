package cl.duoc.backendiii.bff.web.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class ChannelTokenInterceptor implements HandlerInterceptor {

    private static final String CHANNEL_TOKEN_HEADER = "X-Channel-Token";

    private final String expectedToken;

    public ChannelTokenInterceptor(@Value("${channel.auth-token}") String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String receivedToken = request.getHeader(CHANNEL_TOKEN_HEADER);
        if (expectedToken.equals(receivedToken)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Token de canal Web invalido\"}");
        return false;
    }
}

