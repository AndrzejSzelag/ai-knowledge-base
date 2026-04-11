package pl.szelag.ai_knowledge_base.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converts a float[] embedding vector to/from a DB string format:
 * [0.1,0.2,0.3].
 */
@Converter
public class VectorConverter implements AttributeConverter<float[], String> {

    /** float[] → "[0.1,0.2,0.3]" */
    @Override
    public String convertToDatabaseColumn(float[] embedding) {
        if (embedding == null)
            return null;

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0)
                sb.append(",");
            sb.append(embedding[i]);
        }
        return sb.append("]").toString();
    }

    /** "[0.1,0.2,0.3]" → float[] */
    @Override
    public float[] convertToEntityAttribute(String dbData) {
        if (dbData == null)
            return null;

        // Strip brackets and whitespace before splitting
        String trimmed = dbData.replaceAll("[\\[\\]\\s]", "");
        if (trimmed.isEmpty())
            return new float[0];

        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }
}