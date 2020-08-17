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

import fj.*;
import fj.data.Either;
import fj.data.HashMap;
import fj.data.List;
import fj.data.Validation;

import java.util.Map;

import static ji.core.Logger.LOG;

public final class Functional {

    public static <A, B, E extends Exception> F<List<A>, Map<A, B>> safeMapToHashMap(F<A, Validation<E, B>> f) {
        return Function.andThen(Functional.safeMap0(f), Functional::asMap);
    }

    public static <A, B, E extends Exception> F<List<A>, List<B>> safeMap(F<A, Validation<E, B>> f) {
        return Function.andThen(Functional.safeMap0(f), Functional::success);
    }

    public static <A, B, E extends Exception> Boolean successOrWarn(P2<A, Validation<E, B>> pair) {
        if (pair._2().isSuccess()) {
            LOG.debug("Mapped %s", pair._1());
            return true;
        } else {
            LOG.warn(pair._2().fail(), "Ignored %s", pair._1());
            return false;
        }
    }

    public static <A, B> Map<A, B> asMap(Iterable<P2<A, B>> pairs) {
        return HashMap.iterableHashMap(pairs).toMap();
    }

    public static <A> Validation<Exception, A> validation(boolean condition, F0<A> success, F0<Exception> failure) {
        return Validation.validation(Either.iif(condition, success, failure));
    }

    private static <A, B> List<B> success(List<P2<A, B>> l) {
        return l.map(P2::_2);
    }

    private static <A, B, E extends Exception> F<List<A>, List<P2<A, B>>> safeMap0(F<A, Validation<E, B>> f) {
        return a -> a.map(i -> P.p(i, f.f(i)))
                     .filter(Functional::successOrWarn)
                     .map(p -> p.map2(v -> v.success()));
    }

    private Functional() {}
}
