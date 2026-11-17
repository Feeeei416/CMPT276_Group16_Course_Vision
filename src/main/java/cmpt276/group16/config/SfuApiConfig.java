package cmpt276.group16.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration 
public class SfuApiConfig {

    // The HTTP client for the SFU Course Outlines API. 
    // Without timeouts a request to a slow server would wait forever and the IT manager's page would never load
    @Bean 
    public RestClient sfuRestClient(@Value("${app.sfu-api.connect-timeout}") Duration connectTimeout, 
            @Value("${app.sfu-api.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().requestFactory(requestFactory).build();
    }
}
