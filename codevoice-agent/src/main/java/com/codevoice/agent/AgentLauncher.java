package com.codevoice.agent;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;

public class AgentLauncher {

    private static ClassLoader classLoader;

    public static void premain(String args, Instrumentation instrumentation) {
        doMain(args, instrumentation, true);
    }

    public static void agentmain(String args, Instrumentation instrumentation) {
        doMain(args, instrumentation, false);
    }

    private static synchronized void doMain(String args, Instrumentation instrumentation, boolean premain) {
        try {
            File agentJarFile = getAgentJarFile();

            classLoader = new AgentClassLoader(agentJarFile, null, "");

            Class.forName("com.codevoice.agent.core.CodeVoiceAgent")
                    .getMethod("initialize", String.class, Instrumentation.class, File.class, boolean.class)
                    .invoke(null, args, instrumentation, agentJarFile, premain);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static File getAgentJarFile() throws URISyntaxException {
        ProtectionDomain protectionDomain = AgentLauncher.class.getProtectionDomain();
        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource == null) {
            throw new IllegalStateException(String.format("unable to find agent location, protectionDomain=%s", protectionDomain));
        }
        URL location = codeSource.getLocation();
        if (location == null) {
            throw new IllegalStateException(String.format("unable to find agent location, codeSource=%s", codeSource));
        }

        File agentJarFile = new File(location.toURI());
        if (!agentJarFile.getName().endsWith(".jar")) {
            throw new IllegalStateException(String.format("Not a agent jar file, file=%s", agentJarFile));
        }

        return agentJarFile.getAbsoluteFile();
    }

}
