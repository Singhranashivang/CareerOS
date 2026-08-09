package com.careeros.backend.achievement.llm;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads a list of strings from whatever shape a local model actually produced.
 *
 * The schema asks for a flat array; a 7B model honours that most of the time,
 * not every time. Shapes seen in production:
 *
 *   ["Controller", "Service"]                              the schema
 *   "Layered Architecture"                                 a bare string
 *   {"type":"Layered","layers":["Controller","Service"]}   labelled object
 *   {"REST API": true, "Auth": true}                       flag object
 *
 * Objects need both halves of the entry considered. In the labelled case the
 * information is in the values; in the flag case the values are just `true` and
 * the information is entirely in the keys. So: a boolean value means the key is
 * the value (and false means absent); anything else recurses into the value.
 */
public class FlexibleStringListDeserializer extends JsonDeserializer<List<String>> {

    @Override
    public List<String> deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {

        List<String> values = new ArrayList<>();
        collect(parser.readValueAsTree(), values);
        return values;
    }

    /**
     * Jackson routes an explicit JSON null here instead of deserialize(), so
     * without this the field stays null and every downstream reader has to
     * null-check a list the schema promised.
     */
    @Override
    public List<String> getNullValue(DeserializationContext context) {
        return new ArrayList<>();
    }

    private static void collect(JsonNode node, List<String> values) {

        if (node == null || node.isNull()) {
            return;
        }

        if (node.isArray()) {
            node.forEach(child -> collect(child, values));
            return;
        }

        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                JsonNode value = entry.getValue();
                if (value.isBoolean()) {
                    // {"REST API": true} — the key is the content.
                    if (value.asBoolean()) {
                        add(values, entry.getKey());
                    }
                } else {
                    collect(value, values);
                }
            }
            return;
        }

        add(values, node.asText());
    }

    private static void add(List<String> values, String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (!trimmed.isEmpty() && !values.contains(trimmed)) {
            values.add(trimmed);
        }
    }
}
