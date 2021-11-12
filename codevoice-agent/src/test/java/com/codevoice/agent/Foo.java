package com.codevoice.agent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Foo {
    private String bar = "abc";
    public Foo() {
        System.out.println("foo");
        System.out.println(String.class.getClassLoader());
    }

    public static void bar() {
        System.out.println("bar");
    }

    public static void main(String[] args) {
        for (Field allDeclaredField : getAllDeclaredFields(SFoo.class)) {
            System.out.println(allDeclaredField);
        }
    }

    private static List<Field> getAllDeclaredFields(Class<?> type) {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields;
    }

    public class SFoo extends Foo {
        private String sbar = "abc";

    }
}
