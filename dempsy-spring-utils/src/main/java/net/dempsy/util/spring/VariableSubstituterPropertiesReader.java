package net.dempsy.util.spring;

import static net.dempsy.util.Functional.chain;
import static net.dempsy.util.Functional.chainThrows;
import static net.dempsy.util.Functional.uncheck;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.text.StringSubstitutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.dempsy.util.Functional;
import net.dempsy.util.MutableRef;
import net.dempsy.util.io.UriOpener;

public class VariableSubstituterPropertiesReader implements PropertiesReader {
    private final static Logger LOGGER = LoggerFactory.getLogger(VariableSubstituterPropertiesReader.class);

    public static final String ENV_SUB_PREFIX = "$ENV{";
    public static final String ENV_SUB_SUFFIX = "}";

    public static final char VALUE_DELIMITER = ':';

    private final UriOpener vfs;
    private final String[] variablePropertiesSources;
    private boolean allowEnvVariableReferencing = true;

    public VariableSubstituterPropertiesReader() {
        this(f -> {
            throw new IllegalStateException();
        });
    }

    public VariableSubstituterPropertiesReader(final UriOpener vfs) {
        this(vfs, new String[0]);
    }

    /**
     * The variablePropertiesSources is a list of properties files but each entry in the
     * array can also be a comma separated list.
     */
    public VariableSubstituterPropertiesReader(final UriOpener vfs, final String... variablePropertiesSources) {
        this.vfs = vfs;
        this.variablePropertiesSources = Arrays.stream(variablePropertiesSources)
            .map(e -> e.split(",", -1))
            .flatMap(Arrays::stream)
            .toArray(String[]::new);
    }

    @Override
    public Properties read() throws IOException {

        // read all of the properties files into one big prop object
        final Properties variables = new Properties();

        // check to make sure there are no variables that should have been substituted
        final Properties sysProps = new Properties();
        System.getProperties().forEach((k, v) -> sysProps.put(k, v));

        // These are just for tracking for the purposes of logging dups
        final Map<String, URI> sources = new HashMap<>();
        final MutableRef<URI> currentSource = new MutableRef<>();

        Arrays.stream(variablePropertiesSources)
            .map(pfile -> uncheck(() -> new URI(pfile))) // to uri
            .map(u -> chain(u, u1 -> currentSource.ref = u1)) // set the current source
            .map(u -> uncheck(() -> vfs.open(u))) // to input stream
            .forEach(is -> Functional.<IOException>uncheck(() -> {
                final Properties lprop = chainThrows(new Properties(), p -> p.load(is));
                lprop.load(is);

                lprop.forEach((k, v) -> {
                    LOGGER.trace("Constructing properties: " + k + ":" + v);

                    if(variables.containsKey(k)) {
                        if(sysProps.containsKey(k))
                            LOGGER.info("System property \"{}\" with a value of \"{}\" is taking prescendence over that set in a file with a value of \"{}\"",
                                k, sysProps.get(k), v);
                        else {
                            if(v.equals(variables.get(k)))
                                LOGGER.info(
                                    "The variable \"{}\" is being set to \"{}\" from two different properties files listed. The first is \"{}\" and the second is \"{}\".",
                                    k, v, sources.get(k), currentSource.ref);
                            else
                                LOGGER.warn(
                                    "The variable \"{}\" is being set twice. Once from \"{}\" and once from \"{}\". The value will be set to is \"{}\" while the other is \"{}\"",
                                    k, sources.get(k), currentSource.ref, variables.get(k), v);
                        }
                    } else {
                        sources.put((String)k, currentSource.ref);
                        variables.put(k, v);
                    }
                });
            }));

        variables.putAll(sysProps);

        // substitute the substitutes
        Properties prev;
        Properties substituted = variables;
        do {
            prev = substituted;
            substituted = substitutePass(prev);
        } while(!prev.equals(substituted));

        // handle ENV substitutions.
        if(allowEnvVariableReferencing)
            substituted = substituteEnvVariables(substituted);

        variables.clear();
        variables.putAll(substituted);

        for(final Map.Entry<Object, Object> e: variables.entrySet()) {
            final String k = (String)e.getKey();
            final String v = (String)e.getValue();
            checkForUnsubstituted(k);
            checkForUnsubstituted(v);
        }
        return variables;
    }

    public static void checkForUnsubstituted(final String toCheck) {
        final int openIndex = toCheck.indexOf("${");
        if(openIndex >= 0 && toCheck.indexOf("}") > openIndex)
            throw new IllegalStateException("Cannot resolve placeholder for " + toCheck);
    }

    public boolean isAllowEnvVariableReferencing() {
        return allowEnvVariableReferencing;
    }

    public void setAllowEnvVariableReferencing(final boolean allowEnvVariableReferencing) {
        this.allowEnvVariableReferencing = allowEnvVariableReferencing;
    }

    private static Properties substituteEnvVariables(final Properties properties) {

        final StringSubstitutor sub = new StringSubstitutor(System.getenv());
        sub.setVariablePrefix(ENV_SUB_PREFIX);
        sub.setVariableSuffix(ENV_SUB_SUFFIX);
        sub.setValueDelimiter(VALUE_DELIMITER);

        final Properties substituted = new Properties();
        properties.forEach((k, v) -> {
            // Purposely chose not to substitute in the keys. It's too confusing to use correctly.
            substituted.put(k, sub.replace(v));
        });

        return substituted;
    }

    private static Properties substitutePass(final Properties variables) {
        final Map<String, String> varProps = new HashMap<>();

        variables.forEach((k, v) -> varProps.put((String)k, (String)v));
        final StringSubstitutor sub = new StringSubstitutor(varProps);
        sub.setValueDelimiter(VALUE_DELIMITER);

        final Properties substituted = new Properties();
        variables.forEach((k, v) -> {
            // Purposely chose not to substitute in the keys. It's too confusing to use correctly.
            substituted.put(k, sub.replace(v));
        });

        varProps.forEach((k, v) -> {
            if(!substituted.containsKey(k))
                substituted.put(k, v);
        });
        return substituted;
    }

}
