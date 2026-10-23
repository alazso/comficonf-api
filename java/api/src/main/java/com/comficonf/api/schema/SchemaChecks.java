package com.comficonf.api.schema;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Consistency checks shared by containers of fields. */
final class SchemaChecks {

    private SchemaChecks() {
    }

    /** Checks key uniqueness and visibility conditions of fields that share a parent. */
    static void checkSiblings(List<ConfigField> fields, String container) {
        Map<String, ConfigField> byKey = new HashMap<>();
        for (ConfigField field : fields) {
            if (byKey.put(field.key(), field) != null) {
                throw new SchemaException("Duplicate field key '" + field.key() + "' in " + container);
            }
        }
        for (ConfigField field : fields) {
            VisibilityCondition condition = field.visibleWhen();
            if (condition == null) {
                continue;
            }
            ConfigField sibling = byKey.get(condition.field());
            String prefix = "Field '" + field.key() + "' in " + container + ": visibility condition ";
            if (sibling == null) {
                throw new SchemaException(prefix + "refers to unknown sibling '" + condition.field() + "'");
            }
            if (!sibling.type().isScalar() || sibling.sensitive()) {
                throw new SchemaException(prefix + "must refer to a non-sensitive scalar field");
            }
            for (Object value : condition.values()) {
                if (!matchesType(sibling, value)) {
                    throw new SchemaException(prefix + "compares '" + sibling.key() + "' ("
                            + sibling.type().protocolName() + ") with incompatible value " + value);
                }
            }
        }
    }

    /** Rejects sensitive fields inside object lists, where values cannot be matched to stored secrets. */
    static void checkNoSensitive(ConfigField field, String listKey) {
        if (field.sensitive()) {
            throw new SchemaException("Field '" + field.key() + "' inside object list '" + listKey
                    + "' cannot be sensitive");
        }
        for (ConfigField child : field.fields()) {
            checkNoSensitive(child, listKey);
        }
    }

    private static boolean matchesType(ConfigField field, Object value) {
        return switch (field.type()) {
            case BOOLEAN -> value instanceof Boolean;
            case INTEGER -> value instanceof Long number && IntegerFieldBuilder.isSafe(number);
            case DECIMAL -> value instanceof Double;
            case ENUM -> value instanceof String s && field.choices().stream().anyMatch(c -> c.value().equals(s));
            case STRING, TEXT, MATERIAL -> value instanceof String;
            default -> false;
        };
    }
}
