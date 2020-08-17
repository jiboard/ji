/*
 * Copyright 2020 Zhong Lunfu
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

import fj.F;
import fj.Try;
import fj.data.List;
import fj.data.Validation;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassInjector;

import java.io.IOException;
import java.util.Map;


final class ClassLoaderInjection {

    private final ClassInjector injector;

    ClassLoaderInjection(ClassInjector injector) {
        this.injector = injector;
    }

    Iterable<Class<?>> inject(Iterable<String> names) {
        return injector.injectRaw(types(names)).values();
    }

    private Map<String, byte[]> types(Iterable<String> names) {
        final ClassFileLocator locator = ClassFileLocator.ForClassLoader.of(getClass().getClassLoader());
        final F<String, Validation<IOException, byte[]>> bytes = Try.f(n -> locator.locate(n).resolve());
        return Functional.safeMapToHashMap(bytes).f(List.iterableList(names));
    }

    interface Target {}
}
