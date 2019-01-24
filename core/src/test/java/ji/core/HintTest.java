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

import fj.Unit;
import fj.data.List;
import fj.data.Validation;
import ji.core.Hint.GenerateHandler;
import ji.core.Hint.GenerateInlineClass;
import ji.loader.Compoundable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.Origin;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class HintTest {
    @Test
    public void should_generate_advice_inline_class() throws Exception {
        final GenerateInlineClass.Default generate = GenerateInlineClass.Default.of(new ByteBuddy());
        final DynamicType.Unloaded<?> unloaded = generate.f(TypeDescription.ForLoadedType.of(Foo.class));
        final Class<?> generated = unloaded.load(getClass().getClassLoader(), ClassLoadingStrategy.Default.INJECTION).getLoaded();
        final Method enter = generated.getDeclaredMethod("enter", String.class);
        Dispatcher.register("ji.core.HintTest$Foo#enter", args -> args[0]);

        assertThat(generated.getName(), is("ji.core.HintTest$Foo$InlineClass"));
        assertEquals(String.class, enter.getReturnType());
        assertThat(enter.getModifiers(), is(Modifier.FINAL | Modifier.STATIC | Modifier.PUBLIC));

        List.arrayList(enter.getAnnotations()).foreach(a -> {
            OnMethodEnter e = (OnMethodEnter) a;
            assertEquals(Throwable.class, e.suppress());
            return Unit.unit();
        });

        assertThat(List.arrayList(enter.getParameterTypes()), is(List.arrayList(String.class)));
        assertThat(List.arrayList(enter.getParameterAnnotations()[0]).map(Annotation::annotationType), is(List.arrayList(Origin.class)));
        assertThat(enter.invoke(null, "m"), is("m"));
    }

    @Test
    public void should_create_advice_handlers() {
        final GenerateHandler.Default generate = GenerateHandler.Default.of(new ByteBuddy());
        final TypeDescription desc = TypeDescription.ForLoadedType.of(Foo.class);
        final Hint.Registry registry = generate.f(desc);
        registry.f(new Foo());

        assertThat(Dispatcher.execute("ji.core.HintTest$Foo#enter", "method"), is("method"));
    }

    @Test
    public void should_failed_to_transform() {
        final Compoundable comp = mock(Compoundable.class);
        final ClassFileLocator locator = mock(ClassFileLocator.class);
        final Hint.Registry registry = mock(Hint.Registry.class);
        final DynamicType.Builder builder = mock(DynamicType.Builder.class);
        final TypeDescription td = mock(TypeDescription.class);
        final ClassLoader loader = getClass().getClassLoader();

        final DynamicType.Builder<?> result = new Hint.Default(comp, locator, "inline", registry)
                .f(cl -> Validation.fail(MockError.SINGLETON))
                .transform(builder, td, loader, null);

        assertThat(result, is(builder));
        verify(comp).include(loader);
    }

    final static class Foo {
        @OnMethodEnter(suppress = Throwable.class)
        String enter(@Origin("#m") String method) { return method;}
    }

}