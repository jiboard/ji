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

        final F<URL, Validation<IOException, BufferedReader>> reader = Try.f(url -> {
            final URLConnection connection = url.openConnection();
            final InputStream stream = connection.getInputStream();
            return new BufferedReader(new InputStreamReader(stream, Charsets.UTF_8));
        });

        return Option.some(resources(cls.getClassLoader(), "META-INF/services/" + cls.getName()))
                     .filter(successOrWarn())
                     .bind(p -> p._2().toOption())
                     .map(List::iterableList)
                     .orSome(List.nil())
                     .map(u -> P.p(u, reader.f(u)))
                     .filter(successOrWarn())
                     .map(p -> P.p(p._1(), readLines().f(p._2().success())))
                     .filter(successOrWarn())
                     .map(p -> p._2().success())
                     .bind(Function.identity());
    }

    private static P2<String, Validation<IOException, ArrayList<URL>>> resources(ClassLoader loader, String name) {
        return P.p(name, Try.f(() -> list(loader.getResources(name))).f());
    }

    private static <A, B, E extends Exception> F<P2<A, Validation<E, B>>, Boolean> successOrWarn() {
        return Filters.successOrWarn(s -> "Read " + s, s -> "Failed to read " + s);
    }

    private static F<BufferedReader, Validation<IOException, List<String>>> readLines() {
        return Try.f(r -> accumulate().f(r).run().run());
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
