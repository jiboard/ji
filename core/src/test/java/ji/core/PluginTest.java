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

import com.google.auto.service.AutoService;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static net.bytebuddy.matcher.ElementMatchers.nameEndsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;

public class PluginTest {

    @Test
    public void should_capture_method() throws Exception {

    }


    @AutoService(Plugin.class)
    public static final class Foo {
        @Plugin.Transform(with = CaptureMethod.class)
        public static Coordinate fooBar() {
            return new Coordinate() {
                @Override
                public ElementMatcher<? super TypeDescription> type() {
                    return nameEndsWith("ji.core.PluginTest$Foo");
                }

                @Override
                public ElementMatcher<? super MethodDescription> method() {
                    return named("bar");
                }

            };
        }

        @Plugin.Export
        public static AtomicReference<String> ref() {
            return new AtomicReference<>();
        }


        public static class CaptureMethod {
            private final AtomicReference<String> ref;

            public CaptureMethod(@Plugin.Import AtomicReference<String> ref) {this.ref = ref;}

            @Advice.OnMethodEnter
            public void enter(@Advice.Origin("#m") String method) {
                ref.set(method);
            }
        }
    }

}