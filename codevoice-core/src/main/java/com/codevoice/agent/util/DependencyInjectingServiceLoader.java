package com.codevoice.agent.util;

import com.codevoice.agent.sdk.CodeVoiceInstrumentation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class DependencyInjectingServiceLoader<T> {
    private List<T> instances = new ArrayList<>();
    private final Class<T> clazz;
    private final Set<Class<?>> implementationClassCache;
    private final Set<URL> resourcePathCache;


    private DependencyInjectingServiceLoader(Class<T> clazz, List<ClassLoader> classLoaders) {
        this.clazz = clazz;
        implementationClassCache = new HashSet<>();
        resourcePathCache = new HashSet<>();

        try {
            for (ClassLoader classLoader : classLoaders) {
                final Enumeration<URL> resources = getServiceDescriptors(classLoader, clazz);

                instantiate(classLoader, getImplementations(resources));

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void instantiate(ClassLoader classLoader, Set<String> implementations) {

        for (String implementation : implementations) {
            T instance = instantiate(classLoader, implementation);
            if (instance != null) {
                instances.add(instance);
            }
        }
    }

    private T instantiate(ClassLoader classLoader, String implementation) {
        try {
            final Class<?> implementationClass = Class.forName(implementation, true, classLoader);
            if (!implementationClassCache.add(implementationClass)) {
                return null;
            }
            //todo: 近考虑了无参构造器
            final Constructor<?> constructor = implementationClass.getConstructor();

            return clazz.cast(constructor.newInstance());

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(String.format("Unable to instantiate class:%s, error:%s", implementation, e.getMessage()));
        }
    }

    /**
     * 获取插件的实现类名全称
     * @param resources
     * @return
     * @throws IOException
     */
    private Set<String> getImplementations(Enumeration<URL> resources) throws IOException {
        Set<String> implementations = new LinkedHashSet<>();
        while (resources.hasMoreElements()) {
            final URL url = resources.nextElement();
            if (resourcePathCache.add(url)) {
                try (final InputStream inputStream = url.openStream()) {
                    final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
                    while (bufferedReader.ready()) {
                        final String line = bufferedReader.readLine().trim();
                        if (!line.startsWith("#") && !line.isEmpty()) {
                            implementations.add(line);
                        }
                    }
                }
            }
        }
        return implementations;
    }

    private Enumeration<URL> getServiceDescriptors(ClassLoader classLoader, Class<T> clazz) throws IOException {
        if (classLoader != null) {
            return classLoader.getResources("META-INF/services/" + clazz.getName());
        } else {
            return ClassLoader.getSystemResources("/META-INF/services/" + clazz.getName());
        }
    }

    public static <T> List<CodeVoiceInstrumentation> load(Class<T> clazz, List<ClassLoader> classLoaders) {
        return new DependencyInjectingServiceLoader(clazz, classLoaders).instances;
    }
}
