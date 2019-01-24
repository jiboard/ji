package ji.core;

import fj.F;
import fj.Function;
import fj.P;
import fj.P2;
import fj.data.HashMap;
import fj.data.List;
import fj.data.Validation;

import java.util.Map;

public final class Patterns {

    public static <A, B, E extends Exception> F<List<A>, Map<A, B>> safeMapToHashMap(F<A, Validation<E, B>> f) {
        return Function.andThen(Patterns.safeMap0(f), Patterns::asMap);
    }

    public static <A, B, E extends Exception> F<List<A>, List<B>> safeMap(F<A, Validation<E, B>> f) {
        return Function.andThen(Patterns.safeMap0(f), Patterns::success);
    }

    public static <A, B, E extends Exception> Boolean successOrWarn(P2<A, Validation<E, B>> pair) {
        if (pair._2().isSuccess()) {
            Logger.singleton().debug(() -> "Mapped " + pair._1());
            return true;
        } else {
            Logger.singleton().warn(() -> "Ignored " + pair._1(), pair._2().fail());
            return false;
        }
    }

    public static <A, B> Map<A, B> asMap(Iterable<P2<A, B>> pairs) {
        return HashMap.iterableHashMap(pairs).toMap();
    }

    private static <A, B> List<B> success(List<P2<A, B>> l) {
        return l.map(P2::_2);
    }

    private static <A, B, E extends Exception> F<List<A>, List<P2<A, B>>> safeMap0(F<A, Validation<E, B>> f) {
        return a -> a.map(i -> P.p(i, f.f(i)))
                     .filter(Patterns::successOrWarn)
                     .map(p -> p.map2(v -> v.success()));
    }

    private Patterns() {}
}
