package com.codevoice.agent.sdk;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import javax.annotation.Nullable;
import java.security.ProtectionDomain;

public abstract class CodeVoiceInstrumentation {

    /**
     * 根据class名称预过滤，由于{@link #getTypeMatcher()}会较慢一些
     * @return
     */
    public ElementMatcher<? super NamedElement> getTypeMatcherPreFilter() {
        return ElementMatchers.any();
    }

    /**
     * 通过类型进行筛选的matcher
     * 为了更加高效，可以尽量先使用比如{@link ElementMatchers#nameStartsWith(String)}}和
     * {@link ElementMatchers#isInterface()}等预筛选
     * @return
     */
    public abstract ElementMatcher<? super TypeDescription> getTypeMatcher();

    public ElementMatcher.Junction<ProtectionDomain> getProtectionDomainPostFilter() {
        return ElementMatchers.any();
    }

    public ElementMatcher.Junction<ClassLoader> getClassLoaderMatcher() {
        return ElementMatchers.any();
    }

    /**
     * 方法过滤matcher
     * @return
     */
    public abstract ElementMatcher<? super MethodDescription> getMethodMatcher();


    public String getAdviceClassName() {
        return getClass().getName() + "$AdviceClass";
    }

    @Nullable
    public Advice.OffsetMapping.Factory<?> getOffsetMapping() {
        return null;
    }

    //todo:
    public void onTypeMatch(TypeDescription typeDescription, ClassLoader classLoader,
                            ProtectionDomain protectionDomain, @Nullable Class<?> classBeingRedefined) {
    }

}
