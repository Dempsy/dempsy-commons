package net.dempsy.util.spring;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.PropertySource;

public class SpringContextLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpringContextLoader.class);

    public static ClassPathXmlApplicationContext load(final String[] propertyCtx, final String[] appCtx, final Object... dependencies) throws IOException {
        return load(propertyCtx, appCtx, false, dependencies);
    }

    public static ClassPathXmlApplicationContext load(final PropertiesReader reader, final String[] appCtx, final Object... dependencies) throws IOException {
        return load(reader, appCtx, false, dependencies);
    }

    public static ClassPathXmlApplicationContext load(final PropertiesReader reader, final String[] appCtx, final boolean skipRefresh,
        final Object... dependencies) throws IOException {
        final String[] toUse = appCtx == null ? new String[] {"spring/default-property-placeholder.xml"} : appCtx;

        // load the main application context without refreshing it.
        final ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext(toUse, false);

        if(dependencies != null)
            Arrays.stream(dependencies)
                .map(o -> new CustomBeanFactoryPostProcessor(o))
                .forEach(pp -> ctx.addBeanFactoryPostProcessor(pp));

        // Add a Spring PropertySource to the main application context's environment
        ctx.getEnvironment().getPropertySources().addFirst(
            new PropertySource<Properties>(reader.getClass().getName()) {
                Properties props = reader.read();

                @Override
                public Object getProperty(final String name) {
                    final String ret = props.getProperty(name);
                    if(LOGGER.isDebugEnabled())
                        LOGGER.debug("Property requested: {} has value of {}", name, ret);
                    return ret;
                }
            });

        // refresh the context
        if(skipRefresh)
            return ctx;

        ctx.refresh();

        // double check to make sure that there's a PropertySourcesPlaceholderConfigurer
        final Map<String, PropertySourcesPlaceholderConfigurer> pspcmap = ctx.getBeansOfType(PropertySourcesPlaceholderConfigurer.class);
        if(pspcmap == null || pspcmap.size() == 0) {
            throw new IllegalArgumentException("There was an attempt to use the property reader ["
                + reader.getClass().getName()
                + "] with the context defined by "
                + Arrays.toString(appCtx)
                + " but there is no PropertySourcesPlaceholderConfigurer in the context");
        }

        return ctx;
    }

    public static ClassPathXmlApplicationContext load(final String[] propertyCtx, final String[] appCtx, final boolean skipRefresh,
        final Object... dependencies) throws IOException {

        // Load the properties-source context.
        try(final ClassPathXmlApplicationContext propsCtx = new ClassPathXmlApplicationContext(propertyCtx);) {
            // Retrieve the reader.
            final PropertiesReader reader = propsCtx.getBean(PropertiesReader.class);
            return load(reader, appCtx, skipRefresh, dependencies);
        }
    }

    private static class CustomBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

        private final Object dependency;

        public CustomBeanFactoryPostProcessor(final Object dependency) {
            this.dependency = dependency;
        }

        @Override
        public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory) throws BeansException {
            beanFactory.registerResolvableDependency(dependency.getClass(), dependency);
        }
    }
}
