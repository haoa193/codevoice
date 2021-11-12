package com.codevoice.agent.playground;

import com.codevoice.agent.sdk.CodeVoiceInstrumentation;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.bytebuddy.matcher.ElementMatchers.*;

public class PlaygroundInstrumentation extends CodeVoiceInstrumentation {

    @Override
    public ElementMatcher<? super TypeDescription> getTypeMatcher() {
        return nameStartsWith("com.codevoice.agent.playground")
                .and(not(isInterface()));
    }

    @Override
    public ElementMatcher<? super MethodDescription> getMethodMatcher() {
        return nameEndsWith("WithByteBuddy");
    }

    public static class AdviceClass {

        private static final Logger LOGGER = LoggerFactory.getLogger(AdviceClass.class);

        @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
        public static void enter() {
            LOGGER.debug("----->enter");
        }

//        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
//        public static void exit() {
//            System.out.println("----->exit");
//        }

        @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
        public static void exit2(@Advice.Return String result, @Advice.This Object self) {
            LOGGER.debug("this:{}", self.getClass().toString());
            LOGGER.debug("----->exit, return value:{}", result);
        }
    }


}
