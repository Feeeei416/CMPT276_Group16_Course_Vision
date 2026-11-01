package cmpt276.group16.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;

// Every @WebMvcTest loads every @ControllerAdvice, but only creates controllers and web beans.
// If UploadErrorHandler needed one of our @Components, every other controller's @WebMvcTest would fail to start
@WebMvcTest(controllers = UploadErrorHandler.class)
class UploadErrorHandlerSliceTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void loadsInAWebMvcTestWithoutOurOtherBeans() {
        assertThat(context.getBean(UploadErrorHandler.class)).isNotNull();
        assertThat(context.getBeansOfType(UploadLimit.class)).isEmpty();
    }
}
