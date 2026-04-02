package com.uniphore.platform.validation.aspect;

import com.google.protobuf.Descriptors.EnumValueDescriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Message;
import org.slf4j.Logger;

/**
 * Protobuf field-access helper for {@link FieldConstraintsAspect}.
 *
 * <p>Intentionally isolated in its own class so that the JVM loads it only when
 * {@code protobuf-java} is on the runtime classpath.  The aspect checks
 * {@code PROTOBUF_PRESENT} before referencing this class.
 *
 * <h3>Supported field types</h3>
 * <ul>
 *   <li><b>String</b> — returned as-is.</li>
 *   <li><b>Numeric scalars</b> (int32/64, float, double, bool, uint32/64, sint32/64, fixed32/64,
 *       sfixed32/64) — returned as their Java boxed type.</li>
 *   <li><b>Enum</b> — returned as the enum constant name string (e.g. {@code "ACTIVE"}).</li>
 *   <li><b>Message</b> — the nested {@link Message} object; combined with dot-notation, a
 *       nested field can be reached (e.g. {@code "address.city"}).</li>
 *   <li><b>Repeated</b> — returned as {@code java.util.List<?>}, compatible with the existing
 *       {@code measureLength} element-count check.</li>
 *   <li><b>Bytes</b> — returned as {@link com.google.protobuf.ByteString}; length check uses
 *       {@link com.google.protobuf.ByteString#size()}.</li>
 * </ul>
 *
 * <h3>Presence / null semantics</h3>
 * <p>For fields that support presence tracking (message-type fields, {@code oneof} members, and
 * proto3 {@code optional} scalar fields), an unset field is returned as {@code null} — matching
 * the behaviour for unset POJO fields, meaning validation rules are skipped.  For proto3 regular
 * scalar fields (no presence), the protobuf default value is returned (e.g. {@code ""} for
 * string, {@code 0} for int32).
 */
final class ProtoFieldReader {

    private ProtoFieldReader() {}

    /** Returns {@code true} when {@code target} is a protobuf {@link Message}. */
    static boolean isProtoMessage(Object target) {
        return target instanceof Message;
    }

    /**
     * Reads a field from a protobuf {@link Message} using the Descriptors API.
     *
     * @param target    the protobuf message (cast-safe via {@link #isProtoMessage})
     * @param fieldPath simple field name or dot-separated path (e.g. {@code "address.city"})
     * @param log       logger from the calling aspect (avoids a second Logger instance)
     * @return field value, or {@code null} if the field is absent/unset
     */
    static Object readField(Object target, String fieldPath, Logger log) {
        return readMessage((Message) target, fieldPath, log);
    }

    // -------------------------------------------------------------------------

    private static Object readMessage(Message message, String fieldPath, Logger log) {
        String[] parts = fieldPath.split("\\.", 2);
        String name = parts[0];

        FieldDescriptor fd = message.getDescriptorForType().findFieldByName(name);
        if (fd == null) {
            log.warn("Protobuf field '{}' not found in {}. Rule will be skipped.",
                    name, message.getDescriptorForType().getName());
            return null;
        }

        // Fields with presence (message-type, oneof, proto3 optional) — treat unset as null
        if (fd.hasPresence() && !message.hasField(fd)) {
            return null;
        }

        Object value = message.getField(fd);

        // Recurse into nested messages via dot-notation
        if (parts.length > 1) {
            if (value instanceof Message nestedMsg) {
                return readMessage(nestedMsg, parts[1], log);
            }
            log.warn("Field '{}' in {} is not a message type; cannot navigate to child '{}'.",
                    name, message.getDescriptorForType().getName(), parts[1]);
            return null;
        }

        // Normalize protobuf enum value descriptor → name string for allowed-values checks
        if (value instanceof EnumValueDescriptor evd) {
            return evd.getName();
        }

        // All other types (String, long, int, double, float, boolean, ByteString, List<?>)
        // are returned as-is and handled by the existing validators.
        return value;
    }
}
