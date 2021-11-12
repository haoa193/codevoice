package com.codevoice.agent.playground;

import com.codevoice.agent.AbstractInstrumentationTest;
import org.junit.jupiter.api.Test;

class PlaygroundInstrumentationTest extends AbstractInstrumentationTest {

    @Test
    public void test() {
//        testWithByteBuddy("Michael");

        System.out.println(testFWithByteBuddy("Bob"));

//        testWithNoByteBuddy();
    }
//
//    public void testWithByteBuddy(String name) {
//        System.out.println("with agent");
//    }

    public String testFWithByteBuddy(String name) {
        System.out.println("with agent");
        return "Again Hello," + name;
    }
//
//    public void testWithNoByteBuddy() {
//        System.out.println("hello, without agent");
//    }
}