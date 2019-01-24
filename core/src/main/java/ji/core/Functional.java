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
