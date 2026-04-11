package pl.szelag.ai_knowledge_base.config;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import pl.szelag.ai_knowledge_base.service.KnowledgeQueryService;

@ExtendWith(MockitoExtension.class)
class AIWarmupRunnerTest {

    @Mock private KnowledgeQueryService knowledgeQueryService;
    @Mock private ApplicationArguments applicationArguments;

    @InjectMocks
    private AIWarmupRunner warmupRunner;

    @Test
    @DisplayName("run — calls warmup() and completes without exception on success")
    void run_warmupSucceeds_completesWithoutException() throws Exception {
        // WHEN & THEN — no exception must propagate out of run()
        assertThatCode(() -> warmupRunner.run(applicationArguments))
                .doesNotThrowAnyException();

        // warmup() must be called exactly once
        verify(knowledgeQueryService).warmup();
    }

    @Test
    @DisplayName("run — swallows exception and does not rethrow when warmup() fails")
    void run_warmupThrows_exceptionSwallowed() throws Exception {
        // GIVEN — warmup() simulates a DB connectivity failure at startup
        doThrow(new RuntimeException("Connection refused"))
                .when(knowledgeQueryService).warmup();

        // WHEN & THEN — run() must catch the exception; startup must not be blocked
        assertThatCode(() -> warmupRunner.run(applicationArguments))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("run — warmup() is invoked exactly once per run() call")
    void run_alwaysInvokesWarmupExactlyOnce() throws Exception {
        // GIVEN — two sequential run() calls simulate e.g. testing or restart scenarios
        warmupRunner.run(applicationArguments);
        warmupRunner.run(applicationArguments);

        // THEN — each run() triggers exactly one warmup(); total = 2
        verify(knowledgeQueryService, times(2)).warmup();
    }

    @Test
    @DisplayName("run — completes within a reasonable time budget (< 500 ms for a mocked warmup)")
    void run_mockedWarmup_completesWithinTimeBudget() throws Exception {
        // Use nanoTime() — monotonic clock, consistent with the runner's own timing approach.
        // Measures only runner overhead since warmup() is mocked.
        long startNano = System.nanoTime();

        // WHEN
        warmupRunner.run(applicationArguments);

        // THEN
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
        assertThat(elapsedMs).isLessThan(500L);
    }
}