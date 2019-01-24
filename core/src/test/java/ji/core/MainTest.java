package ji.core;

import fj.data.List;
import net.bytebuddy.agent.ByteBuddyAgent;
import org.junit.Test;

import java.lang.instrument.Instrumentation;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class MainTest {
    @Test
    public void should_inject_target_classes() {
        final Instrumentation inst = ByteBuddyAgent.install();
        final Iterable<Class<?>> injected = Main.appendBootstrapClassLoaderSearchBy(inst);
        assertThat(
                List.iterableList(injected).map(Class::getSimpleName),
                is(List.arrayList("Handler", "Dispatcher", "DefaultHandler"))
        );
    }

}