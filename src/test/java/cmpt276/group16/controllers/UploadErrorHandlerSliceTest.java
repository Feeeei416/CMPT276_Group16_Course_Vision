package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import cmpt276.group16.models.UserRepository;

// Every @WebMvcTest loads every @ControllerAdvice, but only creates controllers and web beans.
// If UploadErrorHandler needed one of our @Components, every other controller's @WebMvcTest would fail to start
@WebMvcTest(controllers = UploadErrorHandler.class)
class UploadErrorHandlerSliceTest {

    @Autowired
    private ApplicationContext context;

    // WebConfig's AuthInterceptor needs it, and @WebMvcTest doesn't create repositories
    @MockitoBean
    private UserRepository userRepository;
    
    @Test
    void loadsInAWebMvcTestWithoutOurOtherBeans() {
        assertThat(context.getBean(UploadErrorHandler.class)).isNotNull();
        assertThat(context.getBeansOfType(UploadLimit.class)).isEmpty();
    }
}
