package cl.duoc.backendiii.bff.atm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final ChannelTokenInterceptor channelTokenInterceptor;

    public WebMvcConfig(ChannelTokenInterceptor channelTokenInterceptor) {
        this.channelTokenInterceptor = channelTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(channelTokenInterceptor)
                .addPathPatterns("/atm/**");
    }
}

