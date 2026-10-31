package cmpt276.group16.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Web MVC settings for the app。
@Configuration 
public class WebConfig implements WebMvcConfigurer{
    /**
     * Registers AuthInterceptor so it runs before every request to a role's pages.
     * Paths outside these prefixes (/login, /logout, /css/**) are not checked.
     * New pages added under these prefixes are protected automatically.
     */
    @Override 
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/student/**", "/advisor/**", "/it/**");
    }
}
