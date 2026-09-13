package cl.duoc.backendiii.bff.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(ChannelAuthProperties.class)
public class BffCommonConfiguration {

    @Bean
    RestClient coreBankingRestClient(@Value("${core.banking.url}") String coreBankingUrl) {
        return RestClient.builder()
                .baseUrl(coreBankingUrl)
                .build();
    }

    @Bean
    HandlerInterceptor channelTokenInterceptor(ChannelAuthProperties channelAuthProperties) {
        return new ChannelTokenInterceptor(channelAuthProperties);
    }

    @Bean
    WebMvcConfigurer channelTokenWebMvcConfigurer(HandlerInterceptor channelTokenInterceptor,
                                                  ChannelAuthProperties channelAuthProperties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(channelTokenInterceptor)
                        .addPathPatterns(channelAuthProperties.pathPattern());
            }
        };
    }
}
