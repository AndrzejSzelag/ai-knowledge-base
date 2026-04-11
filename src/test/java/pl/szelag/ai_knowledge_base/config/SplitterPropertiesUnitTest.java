package pl.szelag.ai_knowledge_base.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SplitterPropertiesUnitTest {

    @Test
    void shouldComputeOverlapFromRatio_whenOverlapIsNull() {
        SplitterProperties p = new SplitterProperties();
        p.setChunkSize(300);
        p.setChunkOverlap(null);
        p.setOverlapRatio(0.2);

        assertThat(p.getEffectiveChunkOverlap()).isEqualTo(60);
    }

    @Test
    void shouldUseManualOverlap_whenProvided() {
        SplitterProperties p = new SplitterProperties();
        p.setChunkSize(300);
        p.setChunkOverlap(50);
        p.setOverlapRatio(0.2);

        assertThat(p.getEffectiveChunkOverlap()).isEqualTo(50);
    }
}