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

import com.google.common.base.Charsets;
import fj.*;
import fj.data.*;
import fj.data.Iteratee.Input;
import fj.data.Iteratee.IterV;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.Class;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;

import static java.util.Collections.list;

final class AutoServices {

    static List<String> classesAnnotated(Class<?> cls) {
        return classAnnotated(cls, cls.getClassLoader());
    }

    static List<String> classAnnotated(Class<?> cls, ClassLoader loader) {
        final F<URL, Validation<IOException, BufferedReader>> reader = Try.f(url -> {
            final URLConnection connection = url.openConnection();
            final InputStream stream = connection.getInputStream();
            return new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8));
        });

        final F<BufferedReader, Validation<IOException, List<String>>> readLines = Try.f(r -> accumulate().f(r).run().run());

        final List<URL> urls = Option.some(resources(loader, "META-INF/services/" + cls.getName()))
                                     .filter(Patterns::successOrWarn)
                                     .bind(p -> p._2().toOption())
                                     .map(List::iterableList)
                                     .orSome(List.nil());

        return Function.andThen(Patterns.safeMap(reader), Patterns.safeMap(readLines)).f(urls).bind(Function.identity());
    }

    private static P2<String, Validation<IOException, ArrayList<URL>>> resources(ClassLoader loader, String name) {
        return P.p(name, Try.f(() -> list(loader.getResources(name))).f());
    }

    private static F<BufferedReader, IO<IterV<String, List<String>>>> accumulate() {
        return r -> IOFunctions.<List<String>>lineReader().f(r).f(IterV.cont(step(List.nil())));
    }

    private static <A> F<Input<A>, IterV<A, List<A>>> step(List<A> acc) {
        final F0<IterV<A, List<A>>> empty = P.lazy(() -> IterV.cont(step(acc)));
        final F0<F<A, IterV<A, List<A>>>> el = P.lazy(() -> e -> IterV.cont(step(acc.cons(e))));
        final F0<IterV<A, List<A>>> eof = P.lazy(() -> IterV.done(acc, Input.eof()));
        return i -> i.apply(empty, el, eof);
    }

    private AutoServices() {}
}
