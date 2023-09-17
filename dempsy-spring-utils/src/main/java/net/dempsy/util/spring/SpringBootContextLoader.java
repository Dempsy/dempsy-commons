package net.dempsy.util.spring;

import java.io.IOException;
import java.lang.reflect.Method;

import org.springframework.boot.SpringApplication;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import net.dempsy.util.QuietCloseable;

public class SpringBootContextLoader {

//    public static ConfigurableApplicationContext load(final Class[] propertyContexts, )

    public static ConfigurableApplicationContext load(final String[] propertyCtx, final Class<?> appCtx2, final String[] args) throws IOException {
        return load(propertyCtx, null, appCtx2, args);
    }

    public static ConfigurableApplicationContext load(final String[] propertyCtx, final String[] parentCtx, final Class<?> springBootApp, final String[] args)
        throws IOException {

        final ClassPathXmlApplicationContext pCtx = SpringContextLoader.load(propertyCtx, parentCtx);

        try(final ClassPathXmlApplicationContext propsCtx = new ClassPathXmlApplicationContext(propertyCtx);) {
            final SpringApplication app = new SpringApplication(springBootApp);

            app.addInitializers(appCtx -> {
                final ApplicationContext curParent = appCtx.getParent();
                if(curParent != null) throw new IllegalStateException(
                    "Cannot use the " + SpringBootContextLoader.class.getSimpleName() + " to augment a context that already has a parent. Sorry.");
                appCtx.setParent(pCtx);
            });
            final var ret = app.run(args);
            return wrapConfigurableApplicationContext(ret, () -> pCtx.close());
        }
    }

    private static final ConfigurableApplicationContext wrapConfigurableApplicationContext(final ConfigurableApplicationContext ret,
        final QuietCloseable additional) {

        return (ConfigurableApplicationContext)Enhancer.create(ConfigurableApplicationContext.class,
            (MethodInterceptor)(final Object obj, final Method method, final Object[] args, final MethodProxy proxy) -> {
                final Object result = proxy.invoke(ret, args);
                if("close".equals(method.getName()) && (args == null || args.length == 0)) {
                    additional.close();
                }
                return result;
            });
    }

}
