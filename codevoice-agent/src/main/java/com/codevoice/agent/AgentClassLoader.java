package com.codevoice.agent;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class AgentClassLoader extends URLClassLoader {

    private final Manifest manifest;
    private final URL jarUrl;

    public AgentClassLoader(File agentJarFile, ClassLoader parent, String prefix) throws IOException {
        super(new URL[]{agentJarFile.toURI().toURL()}, parent);
        this.jarUrl = agentJarFile.toURI().toURL();
        try (JarFile jarFile = new JarFile(agentJarFile, false)) {
            this.manifest = jarFile.getManifest();
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        byte[] classBytes = getClassBytes(name);

        if (classBytes == null) {
            throw new ClassNotFoundException(name);
        }
        //todo: definePackage 定义包名称
        definePackage(name);

        return super.defineClass(name, classBytes, 0, classBytes.length,
                AgentClassLoader.class.getProtectionDomain());//todo:最后一个参数意义？
    }

    private void definePackage(String name) {
        String packageName = getPackageName(name);
        if (packageName != null && getPackage(packageName) != null) {
            if (manifest != null) {
                definePackage(name, manifest, jarUrl);
            } else {
                definePackage(packageName, null, null, null, null, null, null, null);
            }
        }
    }

    public String getPackageName(String className) {
        int i = className.lastIndexOf('.');
        if (i != -1) {
            return className.substring(0, i);
        }
        return null;
    }


    private byte[] getClassBytes(String name) throws ClassNotFoundException {
        try (InputStream is = getResourceAsStream(name.replace(".", "/").concat(".class"));
        ) {
            if (is != null) {
                try(BufferedInputStream bis = new BufferedInputStream(is)){
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int ch;
                    while ((ch = bis.read()) != -1) {
                        baos.write(ch);
                    }
                    return baos.toByteArray();
                }
            }
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
        return null;
    }
}
