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
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.Origin;
import net.bytebuddy.dynamic.DynamicType;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static org.junit.Assert.assertEquals;

public class InlineAdviceGeneratorTest {
    @Test
    public void should_generate_inline_advice_class() throws Exception {
        final String key = "key";
        final String bar = "Bar";
        final InlineAdviceGenerator generator = new InlineAdviceGenerator(
                new ByteBuddy(), isAnnotatedWith(OnMethodEnter.class), d -> bar, d -> key
        );
        final DynamicType.Unloaded<?> unloaded = generator.f(Foo.class);
        final Class<?> generated = unloaded.load(getClass().getClassLoader()).getLoaded();
        final Method enter = generated.getDeclaredMethod("enter", String.class);
        Dispatcher.register(key, args -> args[0]);

        assertEquals(generated.getName(), bar);
        assertEquals(enter.getReturnType(), String.class);
        assertEquals(enter.getModifiers(), Modifier.FINAL | Modifier.STATIC | Modifier.PUBLIC);
        List.arrayList(enter.getAnnotations()).foreach(a -> {
            OnMethodEnter e = (OnMethodEnter) a;
            assertEquals(e.suppress(), Throwable.class);
            return Unit.unit();
        });
        assertEquals(List.arrayList(enter.getParameterTypes()), List.arrayList(String.class));
        assertEquals(List.arrayList(enter.getParameterAnnotations()[0]).map(Annotation::annotationType), List.arrayList(Origin.class));
        assertEquals(enter.invoke(null, "m"), "m");
    }

    public final static class Foo {
        @OnMethodEnter(suppress = Throwable.class)
        public String enter(@Origin("#m") String method) { return method;}
    }

}