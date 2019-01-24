package ji.core;

import com.typesafe.config.ConfigFactory;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.junit.Test;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PickTest {
    final TypeDescription td = TypeDescription.ForLoadedType.of(Foo.class);
    final ByteBuddy bb = new ByteBuddy();

    @Test
    public void should_build_empty_arguments() throws Exception {
        final Object[] args = Pick.BuildParameters.Generate.Default
                .of(bb, Pick.BuildParameters.GetValues.CONF)
                .f(td.getDeclaredMethods().filter(named("foo")).getOnly())
                .success()
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance()
                .f(ConfigFactory.empty());

        assertThat(args.length, is(0));
    }

    @Test
    public void should_build_arguments_by_config() throws Exception {
        final Object[] args = Pick.BuildParameters.Generate.Default
                .of(bb, Pick.BuildParameters.GetValues.CONF)
                .f(td.getDeclaredMethods().filter(named("bar")).getOnly())
                .success()
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance()
                .f(ConfigFactory.parseString("a = a"));

        assertThat(args[0], is("a"));
    }

    @Test
    public void should_build_arguments_by_ref() throws Exception {
        final Gather.Ref ref = mock(Gather.Ref.class);
        when(ref.f("java.lang.Object#")).thenReturn("a");

        final Object[] args = Pick.BuildParameters.Generate.Default
                .of(bb, Pick.BuildParameters.GetValues.INJECT)
                .f(td.getDeclaredMethods().filter(named("baz")).getOnly())
                .success()
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance()
                .f(ref);

        assertThat(args[0], is("a"));
    }

    @Test
    public void should_complain_missing_annotation() {
        final Exception fail = Pick.BuildParameters.Generate.Default
                .of(bb, Pick.BuildParameters.GetValues.INJECT)
                .f(td.getDeclaredMethods().filter(named("qux")).getOnly())
                .fail();

        assertTrue(fail instanceof IllegalStateException);
        assertThat(fail.getMessage(), is("None ofAnnotationType(is(interface ji.core.Plugin$Inject))"));
    }

    @Test
    public void should_call_foo() throws Exception {
        final String foo = Pick.CallMethod.Generate.Default
                .of(bb, GetString.class)
                .f(td.getDeclaredMethods().filter(named("foo")).getOnly())
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance()
                .f(new Object[]{});
        assertThat(foo, is("foo"));
    }

    @Test
    public void should_create_bar() throws Exception {
        final Bar bar = Pick.CallMethod.Generate.Default
                .of(bb, GetBar.class)
                .f(TypeDescription.ForLoadedType.of(Bar.class).getDeclaredMethods().filter(isConstructor()).getOnly())
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance()
                .f(new Object[]{});

        assertNotNull(bar);
    }

    @Test
    public void should_pick_bar() {
        final String bar = Pick.Default.of(bb, GetString.class, Pick.BuildParameters.GetValues.CONF)
                                       .f(td.getDeclaredMethods().filter(named("bar")).getOnly())
                                       .f(ConfigFactory.parseString("a = bar"))
                                       .success();
        assertThat(bar, is("bar"));
    }

    public static final class Foo {
        public static String foo() {return "foo";}

        public static String bar(@Plugin.Conf("a") String a) {return a;}

        public static Object baz(@Plugin.Inject Object a) {return a;}

        public static Object qux(Object a) {return a;}
    }

    public static final class Bar {}

    interface GetString extends Pick.CallMethod<String> {}

    interface GetBar extends Pick.CallMethod<Bar> {}
}