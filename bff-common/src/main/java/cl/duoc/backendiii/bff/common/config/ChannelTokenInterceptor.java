package cl.duoc.backendiii.bff.common.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

public class ChannelTokenInterceptor implements HandlerInterceptor {

    private static final String CHANNEL_TOKEN_HEADER = "X-Channel-Token";

    private final ChannelAuthProperties channelAuthProperties;

    public ChannelTokenInterceptor(ChannelAuthProperties channelAuthProperties) {
        this.channelAuthProperties = channelAuthProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String receivedToken = request.getHeader(CHANNEL_TOKEN_HEADER);
        if (channelAuthProperties.authToken().equals(receivedToken)) {
            return true;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Token de canal " + channelAuthProperties.name() + " invalido\"}");
        return false;
    }
}
