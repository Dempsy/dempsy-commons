package net.dempsy.serialization.jackson;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.nokia.dempsy.util.SafeString;

import net.dempsy.serialization.SerializationException;
import net.dempsy.serialization.Serializer;

public class JsonSerializer implements Serializer {
    ObjectMapper objectMapper;

    public JsonSerializer() {
        objectMapper = new ObjectMapper();
        objectMapper.enableDefaultTyping();
        objectMapper.configure(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS, false);
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        objectMapper.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
        objectMapper.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        objectMapper.setSerializationInclusion(Include.NON_NULL);
        objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
        objectMapper.setVisibility(PropertyAccessor.GETTER, Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.SETTER, Visibility.NONE);
        objectMapper.setVisibility(PropertyAccessor.CREATOR, Visibility.NONE);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T deserialize(final byte[] data, final Class<T> clazz) throws SerializationException {
        ArrayList<T> info = null;
        if (data != null) {
            final String jsonData = new String(data);
            try {
                info = objectMapper.readValue(jsonData, ArrayList.class);
            } catch (final Exception e) {
                throw new SerializationException("Error occured while deserializing data " + jsonData, e);
            }
        }
        return (info != null && info.size() > 0) ? info.get(0) : null;
    }

    @Override
    public byte[] serialize(final Object data) throws SerializationException {
        String jsonData = null;
        if (data != null) {
            final ArrayList<Object> arr = new ArrayList<Object>();
            arr.add(data);
            try {
                jsonData = objectMapper.writeValueAsString(arr);
            } catch (final Exception e) {
                throw new SerializationException("Error occured during serializing class " +
                        SafeString.valueOfClass(data) + " with information " + SafeString.valueOf(data), e);
            }
        }
        return (jsonData != null) ? jsonData.getBytes() : null;
    }

}
