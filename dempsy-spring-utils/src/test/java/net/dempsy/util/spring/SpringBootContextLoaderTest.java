package net.dempsy.util.spring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import net.dempsy.util.SystemPropertyManager;
import net.dempsy.util.io.UriOpener;

public class SpringBootContextLoaderTest {

    @SpringBootApplication
    @EnableWebSocket
    public static class WebsocketServerDispatcher implements WebSocketConfigurer {
        public static final String SERVICE_PATH = "/tracker";

        public static class Handler extends TextWebSocketHandler {

            @Override
            public void handleTextMessage(final WebSocketSession session, final TextMessage message)
                throws Exception {}

            @Override
            public void afterConnectionClosed(final WebSocketSession session, final CloseStatus status) throws Exception {}
        }

        private UriOpener vfs;

        @Value("${my.test.uri}") public final String testUri = null;

        @Value("${app.properties.property1}") public String app1;

        @Value("${app.properties.property2}") public String app2;

        @Autowired
        public void setVfs(final UriOpener vfs) {
            this.vfs = vfs;
        }

        public UriOpener getVfs() {
            return vfs;
        }

        @Bean
        public Handler handler() {
            return new Handler();
        }

        @Override
        public void registerWebSocketHandlers(final WebSocketHandlerRegistry registry) {
            registry.addHandler(handler(), SERVICE_PATH).setAllowedOrigins("*");
        }
    }

    @Test
    public void testSubstitutions() throws Exception {
        try(@SuppressWarnings("resource")
        SystemPropertyManager man = new SystemPropertyManager().set("variables.file", "classpath:///context-loader-test.properties");) {
            final String[] args = new String[0];
            final String[] propertyCtx = new String[] {"spring/vfs.xml","spring/spring-context-loader-test-property-wiring.xml"};
            final String[] parentCtx = new String[] {"spring/default-property-placeholder.xml","spring/vfs.xml"};

            try(ConfigurableApplicationContext ctx = SpringBootContextLoader.load(propertyCtx, parentCtx,
                WebsocketServerDispatcher.class, args);) {
                final WebsocketServerDispatcher bean = ctx.getBean(WebsocketServerDispatcher.class);

                assertNotNull(bean.vfs);
                assertEquals("https://my.test.uri/", bean.testUri);
                assertEquals("simply here", bean.app1);
                assertEquals("hello again", bean.app2);
            }
        }
    }
}
