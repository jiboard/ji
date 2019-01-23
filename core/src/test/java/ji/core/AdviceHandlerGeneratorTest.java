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

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.Advice.OnMethodEnter;
import net.bytebuddy.asm.Advice.Origin;
import org.junit.Test;

import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static org.junit.Assert.assertEquals;

public class AdviceHandlerGeneratorTest {
    @Test
    public void should_create_handlers() {
        final AdviceHandlerGenerator generator = new AdviceHandlerGenerator(
                new ByteBuddy(),
                isAnnotatedWith(OnMethodEnter.class),
                d -> "key",
                d -> "Bar"
        );

        final Map<String, Dispatcher.Handler> handlers = generator.f(new Foo());
        assertEquals(handlers.size(), 1);
        assertEquals(handlers.get("key").execute("method"), "method");
    }

    public static class Foo {
        @OnMethodEnter
        public String enter(@Origin("#m") String method) {
            return method;
        }
    }
}