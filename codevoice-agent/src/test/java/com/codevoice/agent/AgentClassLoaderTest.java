package com.codevoice.agent;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

class AgentClassLoaderTest {

    public void test() {
        System.out.println(String.class.getClassLoader());

    }
    @Test
    public void testLoadClass(@TempDir File tmp) throws Exception {
        System.out.println(tmp.getAbsoluteFile());
        final File jar = createJar(tmp, List.of(Foo.class, AgentClassLoader.class));
        final AgentClassLoader agentClassLoader = new AgentClassLoader(jar, null, "");

        Class<?> aClass = agentClassLoader.loadClass(Foo.class.getName());

        Assertions.assertThat(aClass).isNotNull();
        Assertions.assertThat(aClass).isNotSameAs(Foo.class);

        //useless code
        aClass.getDeclaredConstructor(null).newInstance();
//        final Foo o = (Foo)aClass.newInstance();

        Class.forName("com.codevoice.agent.Foo", true, agentClassLoader)
                .getMethod("bar")
                .invoke(null);

    }

    private File createJar(File folder, List<Class<?>> classes) throws IOException {
        File file = new File(folder, "test.jar");
        Assertions.assertThat(file.createNewFile()).isTrue();

        try (final JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(file))) {
            for (Class<?> aClass : classes) {
                jarOutputStream.putNextEntry(new JarEntry(aClass.getName().replace('.', '/').concat(".class")));
                jarOutputStream.write(aClass.getResourceAsStream(aClass.getSimpleName() + ".class").readAllBytes());
            }

        }
        return file;
    }
}