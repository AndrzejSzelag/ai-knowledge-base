package pl.szelag.ai_knowledge_base.entity;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class VectorConverterTest {

    private final VectorConverter converter = new VectorConverter();

    // -------------------------------------------------------------------------
    // convertToDatabaseColumn
    // -------------------------------------------------------------------------

    @Test
    void convertToDatabaseColumn_nullInput_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertToDatabaseColumn_emptyArray_returnsEmptyBrackets() {
        assertThat(converter.convertToDatabaseColumn(new float[0])).isEqualTo("[]");
    }

    @Test
    void convertToDatabaseColumn_singleValue_returnsCorrectFormat() {
        assertThat(converter.convertToDatabaseColumn(new float[] { 1.5f })).isEqualTo("[1.5]");
    }

    @Test
    void convertToDatabaseColumn_multipleValues_returnsCommaSeparated() {
        assertThat(converter.convertToDatabaseColumn(new float[] { 1.0f, 2.5f, 3.0f }))
                .isEqualTo("[1.0,2.5,3.0]");
    }

    @Test
    void convertToDatabaseColumn_zeroVector_returnsZeros() {
        assertThat(converter.convertToDatabaseColumn(new float[] { 0.0f, 0.0f }))
                .isEqualTo("[0.0,0.0]");
    }

    @Test
    void convertToDatabaseColumn_negativeValues_handledCorrectly() {
        assertThat(converter.convertToDatabaseColumn(new float[] { -1.0f, -0.5f }))
                .isEqualTo("[-1.0,-0.5]");
    }

    // -------------------------------------------------------------------------
    // convertToEntityAttribute
    // -------------------------------------------------------------------------

    @Test
    void convertToEntityAttribute_nullInput_returnsNull() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void convertToEntityAttribute_emptyBrackets_returnsEmptyArray() {
        float[] result = converter.convertToEntityAttribute("[]");
        assertThat(result).isNotNull().isEmpty();
    }

    @Test
    void convertToEntityAttribute_singleValue_returnsCorrectFloat() {
        float[] result = converter.convertToEntityAttribute("[1.5]");
        assertThat(result).hasSize(1);
        assertThat(result[0]).isEqualTo(1.5f);
    }

    @Test
    void convertToEntityAttribute_multipleValues_returnsCorrectFloats() {
        float[] result = converter.convertToEntityAttribute("[1.0,2.5,3.0]");
        assertThat(result).containsExactly(1.0f, 2.5f, 3.0f);
    }

    @Test
    void convertToEntityAttribute_negativeValues_parsedCorrectly() {
        float[] result = converter.convertToEntityAttribute("[-1.0,-0.5]");
        assertThat(result).containsExactly(-1.0f, -0.5f);
    }

    @Test
    void convertToEntityAttribute_withWhitespace_strippedCorrectly() {
        float[] result = converter.convertToEntityAttribute("[ 1.0, 2.0 ]");
        assertThat(result).containsExactly(1.0f, 2.0f);
    }

    // -------------------------------------------------------------------------
    // Round-trip
    // -------------------------------------------------------------------------

    @Test
    void roundTrip_encodeThenDecode_returnsOriginalValues() {
        float[] original = { 0.1f, 0.5f, -0.3f, 1.0f, 0.0f };
        String encoded = converter.convertToDatabaseColumn(original);
        float[] decoded = converter.convertToEntityAttribute(encoded);
        assertThat(decoded).containsExactly(original);
    }

    @Test
    void roundTrip_1024dimensions_preservesAllValues() {
        float[] original = new float[1024];
        for (int i = 0; i < original.length; i++) {
            original[i] = i * 0.001f;
        }
        String encoded = converter.convertToDatabaseColumn(original);
        float[] decoded = converter.convertToEntityAttribute(encoded);
        assertThat(decoded).containsExactly(original);
    }
}