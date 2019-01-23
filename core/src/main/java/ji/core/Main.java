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
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.Target.BOOTSTRAP;

public final class Main {

    public static void premain(final String args, final Instrumentation inst) {
        Logger.singleton().info(() -> "Injected classes " + appendBootstrapClassLoaderSearchBy(inst));
    }

    @VisibleForTesting
    static Iterable<Class<?>> appendBootstrapClassLoaderSearchBy(Instrumentation inst) {
        final ClassInjector injector = UsingInstrumentation.of(new File(AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> System.getProperty("java.io.tmpdir"))
        ), BOOTSTRAP, inst);
        final ClassLoaderInjection injection = new ClassLoaderInjection(injector);
        return injection.inject(AutoServices.classesAnnotated(ClassLoaderInjection.Target.class));
    }

    private Main() {}
}
