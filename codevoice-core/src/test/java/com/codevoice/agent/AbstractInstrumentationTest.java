package com.codevoice.agent;

import com.codevoice.agent.core.CodeVoiceAgent;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

public abstract class AbstractInstrumentationTest {

    @BeforeAll
    public static synchronized void before() {
        CodeVoiceAgent.initInstrumentation(ByteBuddyAgent.install());
    }

    @AfterAll
    public static synchronized void afterAll() {
        CodeVoiceAgent.reset();
    }

}
