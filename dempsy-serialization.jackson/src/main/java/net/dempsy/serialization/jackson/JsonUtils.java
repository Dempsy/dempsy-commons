package net.dempsy.serialization.jackson;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.lang3.tuple.Pair;

public class JsonUtils {
    private JsonUtils() {}

    /**
     * Convert the given object to a json string.
     */
    public static String pojoToJsonString(final Object pojo) throws JsonProcessingException {
        return pojoToJsonString(pojo, false);
    }

    /**
     * Convert the given object to a json string optionally with human readable formatting
     */
    public static String pojoToJsonString(final Object pojo, final boolean prettyPrint) throws JsonProcessingException {
        final ObjectMapper mapper = makeStandardObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        if(prettyPrint) {
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
        }
        return mapper.writeValueAsString(pojo);
    }

    /**
     * Convert a string with json to the given object type.
     */
    public static <T> T jsonStringToPojo(final String json, final Class<T> pojoType) throws IOException {
        final ObjectMapper mapper = makeStandardObjectMapper();
        return mapper.readValue(json, pojoType);
    }

    /**
     * Convert object type using a standard json mapper.
     */
    public static <T> T objectToClassThroughJson(final Object serializableObject, final Class<T> clazz) throws IOException {
        return makeStandardObjectMapper().convertValue(serializableObject, clazz);
    }

    /**
     * Kognition standard object mapper.
     */
    public static ObjectMapper makeStandardObjectMapper() {
        ObjectMapper objectMapper;

        objectMapper = new ObjectMapper();
        // This should satisfy the requirement to include all null objects and empty collections/arrays
        // and replaces previously deprecated SerializationFeatures
        objectMapper.setDefaultPropertyInclusion(
            JsonInclude.Value.construct(Include.ALWAYS, Include.ALWAYS));
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        objectMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);

        return objectMapper;
    }

    /**
     * Convert an object to a map. This object cannot be a collection. If it is a collection
     * use listify.
     */
    public static Map<String, Object> mappify(final ObjectMapper mapper, final Object obj) {
        return mapper.convertValue(obj, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * Convert a generic collection to a list of object converting the objects using
     * a json ObjectMapper
     */
    public static List<Object> listify(final ObjectMapper mapper, final Collection<?> obj) {
        return mapper.convertValue(obj, new TypeReference<List<Object>>() {});
    }

    /**
     * Filter a mappified object's field recursively.
     */
    public static Map<String, Object> filter(final Map<String, Object> src, final BiFunction<String, Object, Boolean> filter) {
        // the stupid Collector.toMap can't be used if values are null.
        final Map<String, Object> ret = new HashMap<>();
        src.entrySet().stream()
            .filter(e -> filter.apply(e.getKey(), e.getValue()))
            .map(e -> new Object[] {e.getKey(),filterObj(e.getValue(), filter)})
            .forEach(e -> ret.put((String)e[0], e[1]));
        return ret;
    }

    /**
     * Map a mappified object's field recursively.
     */
    public static Map<String, Object> map(final Map<String, Object> src, final BiFunction<String, Object, Object> filter) {
        // the stupid Collector.toMap can't be used if values are null.
        return mapEntry(src, (k, v) -> Pair.of(k, filter.apply(k, v)));
    }

    /**
     * Map a mappified object's field recursively.
     */
    public static Map<String, Object> mapEntry(final Map<String, Object> src, final BiFunction<String, Object, Pair<String, Object>> filter) {
        // the stupid Collector.toMap can't be used if values are null.
        final Map<String, Object> ret = new HashMap<>();
        src.entrySet().stream()
            // apply at current level
            .map(e -> filter.apply(e.getKey(), e.getValue()))
            .map(oa -> Pair.of(oa.getLeft(), mapObjEntry(oa.getRight(), filter)))
            .forEach(e -> ret.put(e.getLeft(), e.getRight()));
        return ret;
    }

    /**
     * Flatten a mappified object using an xpath notation for the new keys in the map.
     */
    public static Map<String, String> flatten(final Map<String, Object> decoded) {
        final Map<String, String> ret = new HashMap<>();
        flatten("/", decoded, ret);
        return ret;
    }

    @SuppressWarnings("unchecked")
    private static Object filterObj(final Object src, final BiFunction<String, Object, Boolean> filter) {
        if(src instanceof List)
            return ((List<Object>)src).stream()
                .map(o -> filterObj(o, filter))
                .collect(Collectors.toList());
        else if(src instanceof Map)
            return filter((Map<String, Object>)src, filter);
        else
            return src;
    }

    @SuppressWarnings("unchecked")
    private static Object mapObjEntry(final Object src, final BiFunction<String, Object, Pair<String, Object>> filter) {
        if(src instanceof List)
            return ((List<Object>)src).stream()
                .map(o -> mapObjEntry(o, filter))
                .collect(Collectors.toList());
        else if(src instanceof Map)
            return mapEntry((Map<String, Object>)src, filter);
        else
            return src;
    }

    @SuppressWarnings("unchecked")
    private static void flatten(final String prefix, final Map<String, Object> decoded, final Map<String, String> results) {
        for(final Map.Entry<String, Object> entry: decoded.entrySet()) {
            final String key = entry.getKey();
            final String nextPrefix = prefix + key + "/";
            final Object value = entry.getValue();

            if(value == null)
                results.put(prefix + key, null);
            else if(value instanceof Map)
                flatten(nextPrefix, (Map<String, Object>)value, results);
            else if(!(value instanceof List))
                results.put(prefix + key, value.toString());
        }
    }

}
