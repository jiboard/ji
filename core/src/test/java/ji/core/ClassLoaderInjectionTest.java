/*
 *  Copyright 2019 Zhong Lunfu
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package ji.core;

import fj.data.List;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation;
import org.junit.Test;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.Target.BOOTSTRAP;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class ClassLoaderInjectionTest {

    @Test
    public void should_inject_classes() {
        final Instrumentation inst = ByteBuddyAgent.install();
        final ClassInjector injector = UsingInstrumentation.of(new File(AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> System.getProperty("java.io.tmpdir"))
        ), BOOTSTRAP, inst);
        final String name = "ji.core.Dispatcher";
        final Iterable<Class<?>> injected = new ClassLoaderInjection(injector).inject(List.arrayList(name, "non.existed"));
        assertThat(List.iterableList(injected).head().getName(), is(name));
    }
}