package lt.vsh1ft.groundstation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ApiExceptionHandlerTest.Endpoints.class)
@Import(ApiExceptionHandlerTest.Endpoints.class)
class ApiExceptionHandlerTest {

    @RestController
    static class Endpoints {

        @GetMapping("/bug")
        String bug() {
            throw new NoSuchElementException("No value present");
        }
    }

    @Autowired
    private MockMvcTester mvc;

    @Test
    void unrelatedNoSuchElementExceptionIsNotTurnedIntoNotFound() {
        assertThat(mvc.get().uri("/bug")).hasFailed()
                .failure().rootCause().isInstanceOf(NoSuchElementException.class);
    }
}
