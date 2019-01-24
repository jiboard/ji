package ji.core;

import com.google.auto.service.AutoService;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import fj.Ord;
import fj.P;
import fj.P2;
import fj.data.List;
import fj.data.TreeMap;
import ji.loader.Compoundable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.Test;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicReference;

import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class MainTest {

    @Test
    public void should_capture_name_of_method_called() throws Exception {
        final Instrumentation inst = ByteBuddyAgent.install();

        final ByteBuddy bb = new ByteBuddy();
        final P2<Gather.Exports, Gather.Transforms> pair =
                List.iterableList(TypeDescription.ForLoadedType.of(Foo.class).getDeclaredMethods())
                    .foldLeft(Main.gather(bb, new MockComp()), P.p(c -> TreeMap.empty(Ord.stringOrd), c -> r -> a -> a));

        final Config config = ConfigFactory.empty();
        pair._2()
            .f(config)
            .f(k -> pair._1().f(config).get().f(k).some())
            .f(new AgentBuilder.Default(bb).with(Main.LoggerListener.DEFAULT)).installOn(inst);

        final Class<?> bar = getClass().getClassLoader().loadClass("ji.core.MainTest$Bar");
        bar.getDeclaredMethod("baz", String.class).invoke(null, "123");
        assertThat(Foo.ref().get(), is("baz"));
    }


    @AutoService(Plugin.class)
    public static final class Foo {
        public static final AtomicReference<String> REF = new AtomicReference<>();

        @Plugin.Transform(with = CaptureMethod.class)
        public static ElementMatcher<? super TypeDescription> bar() {
            return nameEndsWith("Bar");
        }

        @Plugin.Export
        public static AtomicReference<String> ref() {
            return REF;
        }

        public static class CaptureMethod implements Plugin.Matchable {
            private final AtomicReference<String> ref;

            public CaptureMethod(@Plugin.Inject AtomicReference<String> ref) {this.ref = ref;}

            @Advice.OnMethodEnter
            public void enter(@Advice.Origin("#m") String method) {
                ref.set(method);
            }

            @Override
            public ElementMatcher<? super MethodDescription> method() {
                return named("baz");
            }
        }

    }

    public static final class Bar {
        public static String baz(String s) {
            return s;
        }
    }

    static final class MockComp implements Compoundable {

        @Override
        public ClassLoader include(ClassLoader cl) {
            return cl;
        }
    }
}