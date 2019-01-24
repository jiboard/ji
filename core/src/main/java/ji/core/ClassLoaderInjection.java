/*
 * Copyright 2019 Zhong Lunfu
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ji.core;

import com.google.common.annotations.VisibleForTesting;
import fj.F;
import fj.P;
import fj.P2;
import fj.Try;
import fj.data.HashMap;
import fj.data.List;
import fj.data.Validation;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;

import static net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.Target.BOOTSTRAP;
import static net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.of;


final class ClassLoaderInjection {
    static Iterable<Class<?>> appendBootstrapClassLoaderSearchBy(Instrumentation inst) {
        final ClassInjector injector = of(new File(AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> System.getProperty("java.io.tmpdir"))
        ), BOOTSTRAP, inst);
        final ClassLoaderInjection injection = new ClassLoaderInjection(injector);
        return injection.inject(AutoServices.classesAnnotated(Target.class));
    }

    private final ClassInjector injector;

    @VisibleForTesting
    ClassLoaderInjection(ClassInjector injector) {
        this.injector = injector;
    }

    @VisibleForTesting
    Iterable<Class<?>> inject(Iterable<String> names) {
        return injector.injectRaw(types(names)).values();
    }

    private Map<String, byte[]> types(Iterable<String> names) {
        final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(getClass().getClassLoader());

        final F<String, Validation<IOException, byte[]>> bytes = Try.f(n -> locator.locate(n).resolve());

        final List<P2<String, byte[]>> p2s = List
                .iterableList(names)
                .map(name -> P.p(name, bytes.f(name)))
                .filter(Filters.successOrWarn(s -> "Located class " + s, s -> "Failed to locate class " + s))
                .map(p -> p.map2(v -> v.success()));

        return HashMap.iterableHashMap(p2s).toMap();
    }

    interface Target {}
}
