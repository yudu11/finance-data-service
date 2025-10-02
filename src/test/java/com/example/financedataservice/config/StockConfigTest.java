package com.example.financedataservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.AbstractResource;
import org.springframework.lang.NonNull;

class StockConfigTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void stockConfig_loadsValidResource() {
        StockConfig stockConfig = new StockConfig(objectMapper, new StringResource("""
            {
              \"symbols\": [\"AAPL\", \"MSFT\"],
              \"days\": 15
            }
        """));

        assertThat(stockConfig.getSymbols()).containsExactly("AAPL", "MSFT");
        assertThat(stockConfig.getDays()).isEqualTo(15);
    }

    @Test
    void stockConfig_throwsWhenMissingSymbols() {
        assertThatThrownBy(() -> new StockConfig(objectMapper, new StringResource("""
            {
              \"symbols\": [],
              \"days\": 15
            }
        """)))
            .isInstanceOf(IllegalStateException.class);
    }

    private static class StringResource extends AbstractResource {

        private final String payload;

        StringResource(String payload) {
            this.payload = payload;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        @NonNull
        public String getDescription() {
            return "String-backed resource";
        }

        @Override
        @NonNull
        public InputStream getInputStream() throws IOException {
            return new ByteArrayInputStream(payload.getBytes());
        }
    }
}
