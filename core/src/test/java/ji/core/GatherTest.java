package ji.core;

import com.typesafe.config.ConfigFactory;
import fj.Ord;
import fj.data.TreeMap;
import fj.data.Validation;
import ji.core.Gather.Transform.GetAdvice;
import ji.core.Pick.BuildParameters.ByRef;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.Test;
import org.mockito.ArgumentMatchers;

import static ji.core.Pick.BuildParameters.GetValues.INJECT;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class GatherTest {

    final TypeDescription td = TypeDescription.ForLoadedType.of(Foo.class);

    @Test
    public void should_gather_method_annotated_with_export() {
        final MethodDescription desc = td.getDeclaredMethods().filter(named("foo")).getOnly();

        final Object foo = new Gather.Export.Default(md -> config -> Validation.success("foo"))
                .f(desc, config -> TreeMap.empty(Ord.stringOrd))
                .success()
                .f(ConfigFactory.empty())
                .get("java.lang.String#")
                .some();

        assertThat(foo, is("foo"));
    }

    @Test
    public void should_ignore_method_annotated_with_export() {
        final MethodDescription desc = td.getDeclaredMethods().filter(named("foo")).getOnly();

        final TreeMap<String, Object> map = new Gather.Export.Default(md -> config -> Validation.fail(MockError.SINGLETON))
                .f(desc, config -> TreeMap.empty(Ord.stringOrd))
                .success()
                .f(ConfigFactory.empty());

        assertTrue(map.isEmpty());
    }

    @Test
    public void should_gather_method_annotated_with_transform() {
        final MethodDescription desc = td.getDeclaredMethods().filter(named("bar")).getOnly();

        final ByteBuddy bb = new ByteBuddy();
        final Pick.CallMethod.Generate<GetAdvice> cons = Pick.CallMethod.Generate.Default.of(bb, GetAdvice.class);
        final Pick.BuildParameters.Generate<ByRef> args = Pick.BuildParameters.Generate.Default.of(bb, INJECT);

        final Hint hint = mock(Hint.class);
        when(hint.f(ArgumentMatchers.any())).thenReturn(AgentBuilder.Transformer.NoOp.INSTANCE);

        final AgentBuilder mab = mock(AgentBuilder.class);
        final AgentBuilder.Identified.Narrowable narrowable = mock(AgentBuilder.Identified.Narrowable.class);
        final AgentBuilder.Identified.Extendable extendable = mock(AgentBuilder.Identified.Extendable.class);
        when(mab.type(named(""))).thenReturn(narrowable);
        when(narrowable.transform(ArgumentMatchers.any())).thenReturn(extendable);

        new Gather.Transform.Default(md -> config -> Validation.success(named("")), args, cons, d -> hint)
                .f(desc, config -> ref -> ab -> ab)
                .success()
                .f(ConfigFactory.empty())
                .f(s -> "")
                .f(mab);

        verify(mab).type(named(""));
        verify(narrowable).transform(new AgentBuilder.Transformer.Compound(AgentBuilder.Transformer.NoOp.INSTANCE));
        verify(extendable).asTerminalTransformation();
    }

    public static final class Foo {
        @Plugin.Export
        public static String foo() {return "foo";}

        @Plugin.Transform(with = Bar.class)
        public static ElementMatcher<? super TypeDescription> bar() {
            return named("");
        }

        public static final class Bar implements Plugin.Matchable {

            @Override
            public ElementMatcher<? super MethodDescription> method() {
                return named("");
            }
        }
    }

}