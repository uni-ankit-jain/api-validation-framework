package com.uniphore.platform.validation.aspect;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Label;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto.Type;
import com.uniphore.platform.validation.annotation.FieldConstraints;
import com.uniphore.platform.validation.annotation.FieldRule;
import com.uniphore.platform.validation.exception.BodyValidationException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.bind.annotation.RequestBody;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

/**
 * Unit tests for protobuf request-body validation in {@link FieldConstraintsAspect}.
 *
 * <p>Uses {@link DynamicMessage} with programmatically-built file descriptors so no
 * protobuf compiler plugin is required in the build.
 */
@ExtendWith(MockitoExtension.class)
class FieldConstraintsAspectProtoTest {

    // -------------------------------------------------------------------------
    // Proto schema shared across all tests
    // -------------------------------------------------------------------------

    /** Descriptor for the Status enum: UNKNOWN=0, ACTIVE=1, INACTIVE=2 */
    private static Descriptors.EnumDescriptor statusEnumDescriptor;

    /**
     * Descriptor for OrderRequest:
     *   string  status_str  = 1;   (string field)
     *   string  name        = 2;
     *   int32   quantity    = 3;
     *   Status  priority    = 4;   (enum field)
     *   Address address     = 5;   (nested message field)
     *   repeated string tags = 6;  (repeated field)
     *   bytes   payload     = 7;   (bytes field)
     */
    private static Descriptors.Descriptor orderRequestDescriptor;

    /**
     * Descriptor for Address (nested message):
     *   string city = 1;
     */
    private static Descriptors.Descriptor addressDescriptor;

    @BeforeAll
    static void buildDescriptors() throws Exception {
        // --- Status enum ---
        DescriptorProtos.EnumDescriptorProto statusEnum = DescriptorProtos.EnumDescriptorProto.newBuilder()
                .setName("Status")
                .addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName("UNKNOWN").setNumber(0))
                .addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName("ACTIVE").setNumber(1))
                .addValue(DescriptorProtos.EnumValueDescriptorProto.newBuilder().setName("INACTIVE").setNumber(2))
                .build();

        // --- Address nested message ---
        DescriptorProtos.DescriptorProto addressMsg = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Address")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("city").setNumber(1)
                        .setType(Type.TYPE_STRING).setLabel(Label.LABEL_OPTIONAL))
                .build();

        // --- OrderRequest message ---
        DescriptorProtos.DescriptorProto orderMsg = DescriptorProtos.DescriptorProto.newBuilder()
                .setName("OrderRequest")
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("status_str").setNumber(1)
                        .setType(Type.TYPE_STRING).setLabel(Label.LABEL_OPTIONAL))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("name").setNumber(2)
                        .setType(Type.TYPE_STRING).setLabel(Label.LABEL_OPTIONAL))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("quantity").setNumber(3)
                        .setType(Type.TYPE_INT32).setLabel(Label.LABEL_OPTIONAL))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("priority").setNumber(4)
                        .setType(Type.TYPE_ENUM).setLabel(Label.LABEL_OPTIONAL)
                        .setTypeName(".test.Status"))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("address").setNumber(5)
                        .setType(Type.TYPE_MESSAGE).setLabel(Label.LABEL_OPTIONAL)
                        .setTypeName(".test.Address"))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("tags").setNumber(6)
                        .setType(Type.TYPE_STRING).setLabel(Label.LABEL_REPEATED))
                .addField(DescriptorProtos.FieldDescriptorProto.newBuilder()
                        .setName("payload").setNumber(7)
                        .setType(Type.TYPE_BYTES).setLabel(Label.LABEL_OPTIONAL))
                .build();

        // --- File descriptor ---
        DescriptorProtos.FileDescriptorProto fileProto = DescriptorProtos.FileDescriptorProto.newBuilder()
                .setName("test.proto")
                .setPackage("test")
                .setSyntax("proto3")
                .addEnumType(statusEnum)
                .addMessageType(addressMsg)
                .addMessageType(orderMsg)
                .build();

        Descriptors.FileDescriptor fileDescriptor = Descriptors.FileDescriptor.buildFrom(
                fileProto, new Descriptors.FileDescriptor[0]);

        statusEnumDescriptor = fileDescriptor.findEnumTypeByName("Status");
        addressDescriptor    = fileDescriptor.findMessageTypeByName("Address");
        orderRequestDescriptor = fileDescriptor.findMessageTypeByName("OrderRequest");
    }

    // -------------------------------------------------------------------------
    // Test infrastructure
    // -------------------------------------------------------------------------

    private FieldConstraintsAspect aspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature methodSignature;

    @BeforeEach
    void setUp() {
        aspect = new FieldConstraintsAspect();
    }

    /** Minimal stub controller with annotated methods for mocking. */
    @SuppressWarnings("unused")
    static class StubController {

        @FieldConstraints({
                @FieldRule(field = "status_str", values = {"ACTIVE", "INACTIVE"})
        })
        void allowedValues(@RequestBody Object body) {}

        @FieldConstraints({
                @FieldRule(field = "name", min = 2, max = 10)
        })
        void lengthConstraint(@RequestBody Object body) {}

        @FieldConstraints({
                @FieldRule(field = "priority", values = {"ACTIVE", "INACTIVE"})
        })
        void enumConstraint(@RequestBody Object body) {}

        @FieldConstraints({
                @FieldRule(field = "address.city", values = {"NYC", "LON", "BLR"})
        })
        void nestedField(@RequestBody Object body) {}

        @FieldConstraints({
                @FieldRule(field = "tags", min = 1, max = 5)
        })
        void repeatedField(@RequestBody Object body) {}

        @FieldConstraints({
                @FieldRule(field = "payload", max = 4)
        })
        void bytesField(@RequestBody Object body) {}

        @FieldConstraints({
                @FieldRule(field = "address", values = {"ANYTHING"})
        })
        void unsetMessageField(@RequestBody Object body) {}
    }

    /**
     * Wires up the mock JoinPoint so the aspect sees the given method and request body.
     * All stub methods in {@link StubController} carry method-level {@code @FieldConstraints},
     * so the aspect never falls back to {@code pjp.getTarget()} — no stub needed there.
     */
    private void setupJoinPoint(String methodName, Object requestBody) throws NoSuchMethodException {
        Method method = StubController.class.getDeclaredMethod(methodName, Object.class);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{requestBody});
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Nested
    class AllowedValuesOnStringField {

        @Test
        void validValue_passes() throws Throwable {
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("status_str"), "ACTIVE")
                    .build();
            setupJoinPoint("allowedValues", msg);

            assertThatCode(() -> aspect.validate(joinPoint)).doesNotThrowAnyException();
        }

        @Test
        void invalidValue_throwsBodyValidationException() throws Throwable {
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("status_str"), "PENDING")
                    .build();
            setupJoinPoint("allowedValues", msg);

            assertThatThrownBy(() -> aspect.validate(joinPoint))
                    .isInstanceOf(BodyValidationException.class)
                    .satisfies(ex -> {
                        BodyValidationException bve = (BodyValidationException) ex;
                        assertThat(bve.getFieldErrors()).containsKey("status_str");
                    });
        }

        @Test
        void unsetField_proto3Default_emptyStringFails() throws Throwable {
            // Proto3 string default is "" — not in allowed values, so validation fires
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor).build();
            setupJoinPoint("allowedValues", msg);

            assertThatThrownBy(() -> aspect.validate(joinPoint))
                    .isInstanceOf(BodyValidationException.class);
        }
    }

    @Nested
    class LengthConstraintOnStringField {

        @Test
        void withinBounds_passes() throws Throwable {
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("name"), "Alice")
                    .build();
            setupJoinPoint("lengthConstraint", msg);

            assertThatCode(() -> aspect.validate(joinPoint)).doesNotThrowAnyException();
        }

        @Test
        void tooShort_throwsBodyValidationException() throws Throwable {
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("name"), "A")
                    .build();
            setupJoinPoint("lengthConstraint", msg);

            assertThatThrownBy(() -> aspect.validate(joinPoint))
                    .isInstanceOf(BodyValidationException.class)
                    .satisfies(ex -> {
                        BodyValidationException bve = (BodyValidationException) ex;
                        assertThat(bve.getFieldErrors()).containsKey("name");
                        assertThat(bve.getFieldErrors().get("name").get(0)).contains("2");
                    });
        }

        @Test
        void tooLong_throwsBodyValidationException() throws Throwable {
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("name"), "ThisNameIsTooLong")
                    .build();
            setupJoinPoint("lengthConstraint", msg);

            assertThatThrownBy(() -> aspect.validate(joinPoint))
                    .isInstanceOf(BodyValidationException.class)
                    .satisfies(ex -> {
                        BodyValidationException bve = (BodyValidationException) ex;
                        assertThat(bve.getFieldErrors()).containsKey("name");
                        assertThat(bve.getFieldErrors().get("name").get(0)).contains("10");
                    });
        }
    }

    @Nested
    class EnumField {

        @Test
        void validEnumName_passes() throws Throwable {
            Descriptors.EnumValueDescriptor active = statusEnumDescriptor.findValueByName("ACTIVE");
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("priority"), active)
                    .build();
            setupJoinPoint("enumConstraint", msg);

            assertThatCode(() -> aspect.validate(joinPoint)).doesNotThrowAnyException();
        }

        @Test
        void unknownEnumValue_notInAllowedList_fails() throws Throwable {
            // UNKNOWN (0) is the proto3 default and is not in {ACTIVE, INACTIVE}
            Descriptors.EnumValueDescriptor unknown = statusEnumDescriptor.findValueByName("UNKNOWN");
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("priority"), unknown)
                    .build();
            setupJoinPoint("enumConstraint", msg);

            assertThatThrownBy(() -> aspect.validate(joinPoint))
                    .isInstanceOf(BodyValidationException.class)
                    .satisfies(ex -> {
                        BodyValidationException bve = (BodyValidationException) ex;
                        assertThat(bve.getFieldErrors()).containsKey("priority");
                        assertThat(bve.getFieldErrors().get("priority").get(0)).contains("UNKNOWN");
                    });
        }
    }

    @Nested
    class NestedMessageFieldViaDotNotation {

        @Test
        void nestedStringField_validValue_passes() throws Throwable {
            DynamicMessage address = DynamicMessage.newBuilder(addressDescriptor)
                    .setField(addressDescriptor.findFieldByName("city"), "NYC")
                    .build();
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("address"), address)
                    .build();
            setupJoinPoint("nestedField", msg);

            assertThatCode(() -> aspect.validate(joinPoint)).doesNotThrowAnyException();
        }

        @Test
        void nestedStringField_invalidValue_fails() throws Throwable {
            DynamicMessage address = DynamicMessage.newBuilder(addressDescriptor)
                    .setField(addressDescriptor.findFieldByName("city"), "PARIS")
                    .build();
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("address"), address)
                    .build();
            setupJoinPoint("nestedField", msg);

            assertThatThrownBy(() -> aspect.validate(joinPoint))
                    .isInstanceOf(BodyValidationException.class)
                    .satisfies(ex -> {
                        BodyValidationException bve = (BodyValidationException) ex;
                        assertThat(bve.getFieldErrors()).containsKey("address.city");
                    });
        }

        @Test
        void unsetMessageField_treatedAsNull_skipsValidation() throws Throwable {
            // "address" message field not set → hasField returns false → null → rule skipped
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor).build();
            setupJoinPoint("unsetMessageField", msg);

            assertThatCode(() -> aspect.validate(joinPoint)).doesNotThrowAnyException();
        }
    }

    @Nested
    class RepeatedField {

        @Test
        void repeatedFieldWithinBounds_passes() throws Throwable {
            FieldDescriptor tagsFd = field("tags");
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .addRepeatedField(tagsFd, "a")
                    .addRepeatedField(tagsFd, "b")
                    .addRepeatedField(tagsFd, "c")
                    .build();
            setupJoinPoint("repeatedField", msg);

            assertThatCode(() -> aspect.validate(joinPoint)).doesNotThrowAnyException();
        }

        @Test
        void emptyRepeatedField_belowMin_fails() throws Throwable {
            // min=1, but no tags set → List is empty (size 0 < 1)
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor).build();
            setupJoinPoint("repeatedField", msg);

            assertThatThrownBy(() -> aspect.validate(joinPoint))
                    .isInstanceOf(BodyValidationException.class)
                    .satisfies(ex -> {
                        BodyValidationException bve = (BodyValidationException) ex;
                        assertThat(bve.getFieldErrors()).containsKey("tags");
                    });
        }

        @Test
        void repeatedFieldExceedsMax_fails() throws Throwable {
            FieldDescriptor tagsFd = field("tags");
            DynamicMessage.Builder builder = DynamicMessage.newBuilder(orderRequestDescriptor);
            for (int i = 0; i < 6; i++) {
                builder.addRepeatedField(tagsFd, "tag" + i);
            }
            setupJoinPoint("repeatedField", builder.build());

            assertThatThrownBy(() -> aspect.validate(joinPoint))
                    .isInstanceOf(BodyValidationException.class)
                    .satisfies(ex -> {
                        BodyValidationException bve = (BodyValidationException) ex;
                        assertThat(bve.getFieldErrors()).containsKey("tags");
                        assertThat(bve.getFieldErrors().get("tags").get(0)).contains("5");
                    });
        }
    }

    @Nested
    class BytesField {

        @Test
        void bytesWithinMaxLength_passes() throws Throwable {
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("payload"), com.google.protobuf.ByteString.copyFrom(new byte[]{1, 2, 3}))
                    .build();
            setupJoinPoint("bytesField", msg);

            assertThatCode(() -> aspect.validate(joinPoint)).doesNotThrowAnyException();
        }

        @Test
        void bytesExceedingMaxLength_fails() throws Throwable {
            DynamicMessage msg = DynamicMessage.newBuilder(orderRequestDescriptor)
                    .setField(field("payload"), com.google.protobuf.ByteString.copyFrom(new byte[]{1, 2, 3, 4, 5}))
                    .build();
            setupJoinPoint("bytesField", msg);

            assertThatThrownBy(() -> aspect.validate(joinPoint))
                    .isInstanceOf(BodyValidationException.class)
                    .satisfies(ex -> {
                        BodyValidationException bve = (BodyValidationException) ex;
                        assertThat(bve.getFieldErrors()).containsKey("payload");
                    });
        }
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static FieldDescriptor field(String name) {
        return orderRequestDescriptor.findFieldByName(name);
    }
}
