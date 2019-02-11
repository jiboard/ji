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
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import fj.F;
import fj.Ord;
import fj.P;
import fj.P2;
import fj.data.List;
import fj.data.Option;
import fj.data.TreeMap;
import ji.core.Gather.Export;
import ji.core.Gather.Exports;
import ji.core.Gather.Transform;
import ji.core.Gather.Transforms;
import ji.loader.Compoundable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.security.AccessController;
import java.security.PrivilegedAction;

import static ji.core.AutoServices.classesAnnotated;
import static ji.core.Logger.LOG;
import static net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.Target.BOOTSTRAP;
import static net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.of;
import static net.bytebuddy.matcher.ElementMatchers.*;

public final class Main {


    public static void premain(final String args, final Instrumentation inst) {
        LOG.info("Inject bootstrap classloader with %s", appendBootstrapClassLoaderSearchBy(inst));

        final ByteBuddy bb = new ByteBuddy();

        final ClassLoader loader = bb.getClass().getClassLoader();

        final TypePool pool = TypePool.Default.of(loader);

        final Compoundable comp = (Compoundable) loader;

        final Config config = ConfigFactory.load();

        final P2<Exports, Transforms> init = P.p(conf -> TreeMap.empty(Ord.stringOrd), conf -> ref -> ab -> ab);

        final P2<Gather.Ref, Transforms> pair = classesAnnotated(Plugin.class)
                .map(n -> pool.describe(n).resolve())
                .map(Main::declaredMethods)
                .bind(List::iterableList)
                .foldLeft(gather(bb, comp), init)
                .map1(f -> f.f(config))
                .map1(m -> k -> m.get(k).some());

        pair._2().f(config).f(pair._1()).f(ab(bb)).installOn(inst);
    }

    private static MethodList<MethodDescription.InDefinedShape> declaredMethods(TypeDescription t) {
        LOG.info("Gather %s", t);
        return t.getDeclaredMethods();
    }

    @VisibleForTesting
    static F<P2<Exports, Transforms>, F<MethodDescription.InDefinedShape, P2<Exports, Transforms>>> gather(ByteBuddy bb, Compoundable comp) {
        final Export.Default export = Export.Default.of(bb);
        final Transform.Default transform = Transform.Default.of(bb, comp);

        return pair -> md -> {

            if (!md.isStatic()) {
                LOG.debug("Ignored non-static method %s", md);
                return pair;
            }

            if (!md.isPublic()) {
                LOG.debug("Ignored non-public method %s", md);
                return pair;
            }

            if (md.getReturnType().equals(TypeDescription.Generic.VOID)) {
                LOG.debug("Ignored return void method %s", md);
                return pair;
            }

            final Option<P2<Exports, Transforms>> exportsMayChanged = export.f(md, pair._1()).map(e -> P.p(e, pair._2())).toOption();
            final Option<P2<Exports, Transforms>> transformsMayChanged = transform.f(md, pair._2()).map(t -> P.p(pair._1(), t)).toOption();

            return exportsMayChanged.orElse(transformsMayChanged).orSome(() -> {
                LOG.debug("Ignored invalid annotated method %s", md);
                return pair;
            });
        };

    }

    private static AgentBuilder ab(ByteBuddy bb) {
        return new AgentBuilder.Default(bb)
                .with(LoggerListener.DEFAULT)
                .ignore(any(), isBootstrapClassLoader().or(isExtensionClassLoader()).or(is(Main.class.getClassLoader())))
                .or(isSynthetic())
                .or(nameStartsWith("sun.reflect."))
                ;
    }

    @VisibleForTesting
    static Iterable<Class<?>> appendBootstrapClassLoaderSearchBy(Instrumentation inst) {
        final ClassInjector injector = of(new File(AccessController.doPrivileged(
                (PrivilegedAction<String>) () -> System.getProperty("java.io.tmpdir"))
        ), BOOTSTRAP, inst);
        final ClassLoaderInjection injection = new ClassLoaderInjection(injector);
        return injection.inject(classesAnnotated(ClassLoaderInjection.Target.class));
    }


    static final class LoggerListener implements AgentBuilder.Listener {

        static final AgentBuilder.Listener DEFAULT = new LoggerListener();

        @Override
        public void onDiscovery(String name, ClassLoader cl, JavaModule m, boolean loaded) {
            LOG.debug("DISCOVERY %s [%s, %s, loaded=%b]%n", name, cl, m, loaded);
        }

        @Override
        public void onTransformation(TypeDescription desc, ClassLoader cl, JavaModule m, boolean loaded, DynamicType dt) {
            LOG.info("TRANSFORM %s [%s, %s, loaded=%b]%n", desc.getName(), cl, m, loaded);
        }

        @Override
        public void onIgnored(TypeDescription desc, ClassLoader cl, JavaModule m, boolean loaded) {
            LOG.debug("IGNORE %s [%s, %s, loaded=%b]%n", desc.getName(), cl, m, loaded);
        }

        @Override
        public void onError(String name, ClassLoader cl, JavaModule m, boolean loaded, Throwable cause) {
            LOG.warn(cause, "ERROR %s [%s, %s, loaded=%b]%n", name, cl, m, loaded);
        }

        @Override
        public void onComplete(String name, ClassLoader cl, JavaModule m, boolean loaded) {
            LOG.debug("COMPLETE %s [%s, %s, loaded=%b]%n", name, cl, m, loaded);
        }

        private LoggerListener() {}
    }

    private Main() {}
}
