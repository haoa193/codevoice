package com.codevoice.agent.core;

import com.codevoice.agent.core.bytebuddy.ClassLoaderNameMatcher;
import com.codevoice.agent.core.bytebuddy.MinimumClassFileVersionValidator;
import com.codevoice.agent.core.bytebuddy.PatchBytecodeVersionTo51Transformer;
import com.codevoice.agent.core.bytebuddy.RootPackageCustomLocator;
import com.codevoice.agent.sdk.CodeVoiceInstrumentation;
import com.codevoice.agent.util.DependencyInjectingServiceLoader;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.ResettableClassFileTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.TypeConstantAdjustment;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.utility.JavaModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.bytebuddy.agent.builder.AgentBuilder.*;
import static net.bytebuddy.matcher.ElementMatchers.*;

public class CodeVoiceAgent {

    private static File agentJarFile;
    private static Instrumentation instrumentation;
    private static ResettableClassFileTransformer resettableClassFileTransformer;
    private static Logger logger;

    public static void initialize(final String agentArguments,
                                  final Instrumentation instrumentation,
                                  final File agentJarFile,
                                  final boolean premain
    ) {

        System.out.println(String.format("agentArguments:%s", agentArguments));

        CodeVoiceAgent.agentJarFile = agentJarFile;

        initInstrumentation(instrumentation, premain);

    }

    public static void initInstrumentation(Instrumentation instrumentation) {
        initInstrumentation(instrumentation, false);
    }

    public static void initInstrumentation(Instrumentation instrumentation, boolean premain) {

        List<CodeVoiceInstrumentation> instrumentations = loadInstrumentations(instrumentation, premain);

        // POOL_ONLY because we don't want to cause eager linking on startup as the class path may not be complete yet
        AgentBuilder agentBuilder = initAgentBuilder(instrumentation, instrumentations,
                DescriptionStrategy.Default.POOL_ONLY, premain);

        CodeVoiceAgent.instrumentation = instrumentation;

        resettableClassFileTransformer = agentBuilder.installOn(CodeVoiceAgent.instrumentation);
    }

    private static AgentBuilder initAgentBuilder(Instrumentation instrumentation,
                                                 List<CodeVoiceInstrumentation> instrumentations,
                                                 DescriptionStrategy descriptionStrategy,
                                                 boolean premain) {

        final ByteBuddy byteBuddy = new ByteBuddy()
                .with(FailSafeDeclaredMethodsCompiler.INSTANCE);

        AgentBuilder agentBuilder = getAgentBuilder(byteBuddy, descriptionStrategy, premain);
        int numberOfAdvices = 0;

        for (CodeVoiceInstrumentation advice : instrumentations) {

            agentBuilder = applyAdvice(agentBuilder, advice, advice.getTypeMatcher());

            numberOfAdvices++;
        }
        final Logger logger = getLogger();

        logger.debug("total advices:{}", numberOfAdvices);

        return agentBuilder;
    }

    private static AgentBuilder applyAdvice(AgentBuilder agentBuilder, CodeVoiceInstrumentation instrumentation,
                                            ElementMatcher<? super TypeDescription> typeMatcher) {
        final Logger logger = getLogger();

        logger.debug("applying instrumentation:{}", instrumentation.getClass().getName());
        final boolean classLoadingMatchingPreFilter = true;
        final boolean typeMatchingWithNamePreFilter = true;
        final ElementMatcher.Junction<ClassLoader> classLoaderMatcher = instrumentation.getClassLoaderMatcher();
        final ElementMatcher<? super NamedElement> typeMatcherPreFilter = instrumentation.getTypeMatcherPreFilter();
        final ElementMatcher.Junction<ProtectionDomain> versionPostFilter = instrumentation.getProtectionDomainPostFilter();
        final ElementMatcher<? super MethodDescription> methodMatcher = new ElementMatcher.Junction.Conjunction<>(instrumentation.getMethodMatcher(), not(isAbstract()));

        return agentBuilder.type(new RawMatcher() {
                    @Override
                    public boolean matches(TypeDescription typeDescription, ClassLoader classLoader,
                                           JavaModule module, Class<?> classBeingRedefined, ProtectionDomain protectionDomain) {
                        long start = System.nanoTime();

                        if (classLoadingMatchingPreFilter && !classLoaderMatcher.matches(classLoader)) {
                            return false;
                        }
                        if (typeMatchingWithNamePreFilter && !typeMatcherPreFilter.matches(typeDescription)) {
                            return false;
                        }

                        boolean typeMatches;
                        try {
                            typeMatches = typeMatcher.matches(typeDescription) && versionPostFilter.matches(protectionDomain);
                        } catch (Exception e) {
                            typeMatches = false;
                        }
                        if (typeMatches) {
                            logger.debug("Type match for instrumentation {}: {} matches {}",
                                    instrumentation.getClass().getSimpleName(), typeMatcher, typeDescription);
                            try {
                                //todo:
                                instrumentation.onTypeMatch(typeDescription, classLoader, protectionDomain, classBeingRedefined);
                            } catch (Exception e) {
                                e.printStackTrace();
                                typeMatches = false;
                            }
                        }
                        return typeMatches;
                    }
                })
                .transform(new PatchBytecodeVersionTo51Transformer())
                .transform(getTransformer(instrumentation, methodMatcher))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription,
                                                            ClassLoader classLoader, JavaModule module) {
                        return builder.visit(MinimumClassFileVersionValidator.V1_4)
                                // As long as we allow 1.4 bytecode, we need to add this constant pool adjustment as well
                                .visit(TypeConstantAdjustment.INSTANCE);
                    }
                });
    }

    private static AgentBuilder.Transformer.ForAdvice getTransformer(final CodeVoiceInstrumentation instrumentation,
                                                                     final ElementMatcher<? super MethodDescription> methodMatcher) {

        //todo:简单版本
        final Logger logger = getLogger();

        return new Transformer.ForAdvice()
                .advice(new ElementMatcher<MethodDescription>() {
                    @Override
                    public boolean matches(MethodDescription target) {
                        boolean matches;

                        try {
                            matches = methodMatcher.matches(target);
                        } catch (Exception e) {
                            matches = false;
                            logger.error("", e);
                        }
                        return matches;
                    }
                }, instrumentation.getAdviceClassName())
                .include(ClassLoader.getSystemClassLoader(), instrumentation.getClass().getClassLoader())
                .withExceptionHandler(Advice.ExceptionHandler.Default.PRINTING)
                ;
    }

    private static AgentBuilder getAgentBuilder(ByteBuddy byteBuddy, DescriptionStrategy descriptionStrategy, boolean premain) {
        LocationStrategy locationStrategy = LocationStrategy.ForClassLoader.WEAK;
        if (agentJarFile != null) {
            try {
                locationStrategy = new LocationStrategy.Compound(
                        // it's important to first try loading from the agent jar and not the class loader of the instrumented class
                        // the latter may not have access to the agent resources:
                        // when adding the agent to the bootstrap CL (appendToBootstrapClassLoaderSearch)
                        // the bootstrap CL can load its classes but not its resources
                        // the application class loader may cache the fact that a resource like AbstractSpan.class can't be resolved
                        // and also refuse to load the class
                        new LocationStrategy.Simple(ClassFileLocator.ForJarFile.of(agentJarFile)),
                        LocationStrategy.ForClassLoader.WEAK,
                        new LocationStrategy.Simple(new RootPackageCustomLocator("java.", ClassFileLocator.ForClassLoader.ofBootLoader()))
                );
            } catch (IOException e) {
                e.printStackTrace();
//                logger.warn("Failed to add ClassFileLocator for the agent jar. Some instrumentations may not work", e);
            }
        }
        return new Default(byteBuddy)
                .with(RedefinitionStrategy.RETRANSFORMATION)
                // when runtime attaching, only retransform up to 100 classes at once and sleep 100ms in-between as retransformation causes a stop-the-world pause
                .with(premain ? RedefinitionStrategy.BatchAllocator.ForTotal.INSTANCE : RedefinitionStrategy.BatchAllocator.ForFixedSize.ofSize(100))
                .with(premain ? RedefinitionStrategy.Listener.NoOp.INSTANCE : RedefinitionStrategy.Listener.Pausing.of(100, TimeUnit.MILLISECONDS))
                .with(new RedefinitionStrategy.Listener.Adapter() {
                    @Override
                    public Iterable<? extends List<Class<?>>> onError(int index, List<Class<?>> batch, Throwable throwable,
                                                                      List<Class<?>> types) {
//                        logger.warn("Error while redefining classes {}", throwable.getMessage());
//                        logger.debug(throwable.getMessage(), throwable);
                        System.out.println("Error while redefining classes ");
                        return super.onError(index, batch, throwable, types);
                    }
                })
                .with(descriptionStrategy)
                .with(locationStrategy)
//                .with(new ErrorLoggingListener())
                // ReaderMode.FAST as we don't need to read method parameter names
//                .with(useTypePoolCache
//                        ? new LruTypePoolCache(TypePool.Default.ReaderMode.FAST).scheduleEntryEviction()
//                        : PoolStrategy.Default.FAST)
                .ignore(any(), ClassLoaderNameMatcher.isReflectionClassLoader())
                .or(any(), ClassLoaderNameMatcher.classLoaderWithName("org.codehaus.groovy.runtime.callsite.CallSiteClassLoader"))
                .or(nameStartsWith("co.elastic.apm.agent.shaded"))
                .or(nameStartsWith("org.aspectj."))
                .or(nameStartsWith("org.groovy."))
                .or(nameStartsWith("com.p6spy."))
                .or(nameStartsWith("net.bytebuddy."))
                .or(nameStartsWith("org.stagemonitor."))
                .or(nameStartsWith("com.newrelic."))
                .or(nameStartsWith("com.dynatrace."))
                // AppDynamics
                .or(nameStartsWith("com.singularity."))
                .or(nameStartsWith("com.instana."))
                .or(nameStartsWith("datadog."))
                .or(nameStartsWith("org.glowroot."))
                .or(nameStartsWith("com.compuware."))
                .or(nameStartsWith("io.sqreen."))
                .or(nameStartsWith("com.contrastsecurity."))
                .or(nameContains("javassist"))
                .or(nameContains(".asm."))
//                .or(anyMatch(coreConfiguration.getDefaultClassesExcludedFromInstrumentation()))
//                .or(anyMatch(coreConfiguration.getClassesExcludedFromInstrumentation()))
                .disableClassFormatChanges();
    }

    /**
     * 加载插件实现
     *
     * @param instrumentation
     * @param premain
     * @return
     */
    private static List<CodeVoiceInstrumentation> loadInstrumentations(Instrumentation instrumentation, boolean premain) {
        List<ClassLoader> pluginClassLoaders = new ArrayList<>();
        pluginClassLoaders.add(CodeVoiceAgent.class.getClassLoader());
        //todo:其他外部的

        return DependencyInjectingServiceLoader.load(CodeVoiceInstrumentation.class, pluginClassLoaders);
    }

    public static void reset() {
        if (resettableClassFileTransformer != null) {
            try {
                resettableClassFileTransformer.reset(instrumentation, RedefinitionStrategy.RETRANSFORMATION);
            } catch (Exception e) {
                e.printStackTrace();
            }
            resettableClassFileTransformer = null;
        }
    }

    private static Logger getLogger() {
        if (logger == null) {
            // lazily init logger to allow the tracer builder to init the logging config first
            logger = LoggerFactory.getLogger(CodeVoiceAgent.class);
        }
        return logger;
    }
}
