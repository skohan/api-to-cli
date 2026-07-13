package com.petstore.cli.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * How {@link com.petstore.cli.CliContext#render} prints a response. Selected via the
 * {@code --format} flag (or {@code PETSTORE_OUTPUT}); see {@code PetstoreCli} for the option
 * and {@code CliContext} for the resolution precedence.
 */
public enum OutputFormat {

    JSON {
        @Override
        public String format(JsonNode node, ObjectMapper mapper) {
            try {
                return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (Exception e) {
                return String.valueOf(node);
            }
        }
    },

    TABLE {
        @Override
        public String format(JsonNode node, ObjectMapper mapper) {
            return TableRenderer.render(node);
        }
    };

    public abstract String format(JsonNode node, ObjectMapper mapper);
}
