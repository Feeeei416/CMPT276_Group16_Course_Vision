package cmpt276.group16.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import cmpt276.group16.models.UserRepository;

// Web MVC settings for the app.
@Configuration 
public class WebConfig implements WebMvcConfigurer{

    // Handed to AuthInterceptor, which loads the signed-in user by id on each request
    private final UserRepository userRepository;

    public WebConfig(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Registers AuthInterceptor so it runs before every request to a role's pages.
     * Paths outside these prefixes (/login, /logout, /css/**) are not checked.
     * New pages added under these prefixes are protected automatically.
     */
    @Override 
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor(userRepository))
                .addPathPatterns("/student/**", "/advisor/**", "/it/**", "/profile", "/profile/**");
    }
}
