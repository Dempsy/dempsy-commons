package net.dempsy.distconfig;

import static net.dempsy.util.Functional.chainThrows;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.support.GenericXmlApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import net.dempsy.distconfig.PropertiesReader.VersionedProperties;

public class Config {

    private final static Set<String> validCommands = new HashSet<>(Arrays.asList("push", "set", "merge", "read", "clear"));

    private static final String SYSTEM_PROP_NAME_APP_CTX = "appCtx";
    private static final String DEFAULT_APP_CTX = "classpath:zk.xml";

    private static void usage() {
        System.err.println("usage: java [-DappCtx=ctx1[,ctx2[,...]]] -jar Config.jar prop-source-uri command [...]");
        System.err.println("      command                  args");
        System.err.println("      ---------------------------------------");
        System.err.println("      read           [path to location to write props]");
        System.err.println("      push           path-to-properties-file");
        System.err.println("      merge          path-to-properties-file");
        System.err.println("      set            propertyName=value[,propertyName=value[,...]]");
        System.err.println("      clear          propertyName[,propertyName[,...]]");
        System.err.println();
        System.err.println("Note: The default application context expects the variable ZK_CONNECT to be set. You can");
        System.err.println("      set it as an environment variable or using -DZK_CONNECT=connectstring. The connect");
        System.err.println("      string should be of the form: host1:port[,host2:port[,...]]");
        System.err.println();
        System.err.println("Note: The default path within zookeeper is /envconf. To change this set an environment");
        System.err.println("      variable \"CONFIG_PATH\" to a different path. You can also provide the value on");
        System.err.println("      the command line using -DCONFIG_PATH=/path/to/config");
        System.exit(1);
    }

    private static PropertiesReader reader;
    private static PropertiesStore store;

    private static <T> T getBean(final GenericXmlApplicationContext ctx, final Class<T> clazz) {
        try {
            return ctx.getBean(clazz);
        } catch (final NoSuchBeanDefinitionException nsb) {
            return null;
        }
    }

    public static void main(final String[] args) throws Exception {
        if (args.length == 0)
            usage();

        final String command = args[0];

        if (!validCommands.contains(command)) {
            System.err.println("\"" + command + "\" is an invalid command. Valid commands are " + validCommands);
            usage();
        }

        final String ctxString = System.getProperties().containsKey(SYSTEM_PROP_NAME_APP_CTX) ? System.getProperty(SYSTEM_PROP_NAME_APP_CTX)
                : DEFAULT_APP_CTX;

        final DefaultResourceLoader resLoader = new DefaultResourceLoader();
        final Resource[] resources = Arrays.stream(ctxString.split(",")).map(resLoader::getResource).toArray(Resource[]::new);
        try (final GenericXmlApplicationContext ctx = new GenericXmlApplicationContext(resources);) {

            store = getBean(ctx, PropertiesStore.class);
            reader = getBean(ctx, PropertiesReader.class);

            if ("push".equals(command))
                push(args);
            else if ("set".equals(command))
                set(args);
            else if ("merge".equals(command))
                merge(args);
            else if ("read".equals(command))
                read(args);
            else if ("clear".equals(command))
                clear(args);
            else
                usage();
        }
    }

    private static void checkStore() {
        if (store == null) {
            System.err.println("No " + PropertiesStore.class.getSimpleName() + " was defined in the context.");
            System.exit(1);
        }
    }

    private static void checkReader() {
        if (reader == null) {
            System.err.println("No " + PropertiesReader.class.getSimpleName() + " was defined in the context.");
            System.exit(1);
        }
    }

    private static void push(final String[] args) throws Exception {
        if (args.length != 2)
            usage();

        checkStore();

        store.push(chainThrows(new Properties(), p -> p.load(new FileInputStream(args[1]))));
    }

    private static void merge(final String[] args) throws Exception {
        if (args.length != 2)
            usage();

        checkStore();

        store.merge(chainThrows(new Properties(), p -> p.load(new FileInputStream(args[1]))));
    }

    private static void set(final String[] args) throws IOException {
        if (args.length != 2)
            usage();

        checkStore();

        final String[] keyValues = args[1].split(",");
        final Properties props = new Properties();
        Arrays.stream(keyValues).forEach(kv -> {
            final String[] keyAndValue = kv.split("=");
            if (keyAndValue.length != 2)
                throw new RuntimeException("Invalud key=value in " + args[1]);
            props.setProperty(keyAndValue[0], keyAndValue[1]);
        });
        store.merge(props);
    }

    private static void clear(final String[] args) throws IOException {
        if (args.length != 2)
            usage();

        checkStore();

        store.clear(args[1].split(","));
    }

    private static void read(final String[] args) throws Exception {
        if (args.length > 2)
            usage();

        checkReader();

        final VersionedProperties props = reader.read(null);
        final OutputStream os = args.length == 2 ? new FileOutputStream(new File(args[1])) : System.out;

        props.store(os, "System environment properties version " + props.version);
    }
}
