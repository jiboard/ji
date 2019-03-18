package ji.core;

import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import fj.*;
import fj.data.List;
import fj.data.TreeMap;
import fj.data.Validation;
import ji.core.Pick.BuildParameters.ByRef;
import ji.core.Pick.BuildParameters.GetValues;
import ji.loader.Compoundable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * A function to gather something described by {@link MethodDescription}.
 *
 * @param <A> accumulation of gathering.
 * @param <E> Exception maybe happen.
 */
interface Gather<A, E extends Exception> extends F2<MethodDescription, A, Validation<E, A>> {

    /**
     * Gather object return by method annotated with {@link ji.core.Plugin.Export}.
     */
    interface Export extends Gather<Exports, Exception> {

        final class Default implements Export {

            private final Pick<Config, Object> pick;

            static Default of(ByteBuddy bb) {
                return new Default(Pick.Default.of(bb, GetExportObject.class, GetValues.CONF));
            }

            @VisibleForTesting
            Default(Pick<Config, Object> pick) {
                this.pick = pick;
            }

            @Override
            public Validation<Exception, Exports> f(MethodDescription md, Exports pre) {
                return AnnotationAccessor.EXPORT.f(md).map(av -> av.resolve(String.class)).map(s -> gather(md, s, pre));
            }

            Exports gather(MethodDescription md, String scope, Exports pre) {
                return config -> {
                    final TreeMap<String, Object> map = pre.f(config);
                    return pick.f(md).f(config).map(o -> map.set(Ref.KEY.f(md.getReturnType(), scope), o)).on(e -> {
                        Logger.LOG.warn(e, "Ignore %s", md);
                        return map;
                    });
                };
            }
        }

        interface GetExportObject extends Pick.CallMethod<Object> {}
    }

    /**
     * Gather {@link Define} by meta of method annotated with {@link ji.core.Plugin.Transform}.
     */
    interface Transform extends Gather<Transforms, Exception> {

        final class Default implements Transform {

            private final Pick<Config, ElementMatcher<? super TypeDescription>> ptdm;
            private final Pick.BuildParameters.Generate<ByRef> args;
            private final Pick.CallMethod.Generate<GetAdvice> cons;
            private final F<TypeDescription, Hint> hint;

            static Default of(ByteBuddy bb, Compoundable comp) {
                return new Default(
                        Pick.Default.of(bb, GetTypeMatcher.class, GetValues.CONF),
                        Pick.BuildParameters.Generate.Default.of(bb, GetValues.INJECT),
                        Pick.CallMethod.Generate.Default.of(bb, GetAdvice.class),
                        td -> Hint.Default.of(bb, comp, td));
            }

            @VisibleForTesting
            Default(
                    Pick<Config, ElementMatcher<? super TypeDescription>> ptdm,
                    Pick.BuildParameters.Generate<ByRef> args,
                    Pick.CallMethod.Generate<GetAdvice> cons,
                    F<TypeDescription, Hint> hint) {
                this.ptdm = ptdm;
                this.args = args;
                this.cons = cons;
                this.hint = hint;
            }

            @Override
            public Validation<Exception, Transforms> f(MethodDescription md, Transforms pre) {
                return AnnotationAccessor.TRANSFORM.f(md).map(av -> av.resolve(TypeDescription[].class)).map(tds -> gather(md, tds, pre));
            }

            private Transforms gather(MethodDescription md, TypeDescription[] tds, Transforms pre) {
                return config -> ref -> ab -> ptdm.f(md).f(config).map(tm -> {
                    final List<Transformer> transformers = Functional.safeMap(transformerWith(ref)).f(List.list(tds));
                    final Transformer transformer = new Transformer.Compound(transformers.toJavaList());
                    return pre.f(config).f(ref).f(ab).type(tm).transform(transformer).asTerminalTransformation();
                }).on(e -> {
                    Logger.LOG.warn(e, "Ignored %s", md);
                    return pre.f(config).f(ref).f(ab);
                });
            }

            private F<TypeDescription, Validation<Exception, Transformer>> transformerWith(Ref ref) {
                return td -> {
                    final ElementMatcher<? super MethodDescription> matcher = ElementMatchers.isConstructor();
                    final MethodList<MethodDescription.InDefinedShape> methodList = td.getDeclaredMethods().filter(matcher);
                    final F0<Exception> failure = () -> new IllegalStateException("Supposed to has only one constructor");
                    return Functional.validation(methodList.size() == 1, methodList::getOnly, failure)
                                     .map(md -> P.p(args.f(md), cons.f(md)))
                                     .bind(p -> p.map1(parameters(ref))._1().map(a -> P.p(a, p._2())))
                                     .map(p -> create(p._1(), p._2()))
                                     .map(f -> hint.f(td).f(f));
                };
            }

            private F<Validation<Exception, Unloaded<ByRef>>, Validation<Exception, Object[]>> parameters(Ref ref) {
                return v -> v.bind(Try.f(u -> Dynamic.instance(u, getClass().getClassLoader())))
                             .bind(Try.f(f -> f.f(ref)));
            }

            private F<ClassLoader, Validation<Exception, Plugin.Matchable>> create(Object[] parameters, Unloaded<GetAdvice> unloaded) {
                return Try.f(cl -> Dynamic.instance(unloaded, cl).f(parameters));
            }

        }


        interface GetTypeMatcher extends Pick.CallMethod<ElementMatcher<? super TypeDescription>> {}

        interface GetAdvice extends Pick.CallMethod<Plugin.Matchable> {}
    }

    /**
     * A function to define {@link AgentBuilder} and return a new instance.
     */
    interface Define extends F<AgentBuilder, AgentBuilder> {}

    /**
     * A function to get exported object by key.
     */
    interface Ref extends F<String, Object> {
        F2<TypeDescription.Generic, String, String> KEY = (generic, scope) -> generic.getTypeName() + "#" + scope;
    }

    interface Exports extends F<Config, TreeMap<String, Object>> {}

    interface Transforms extends F<Config, F<Ref, Define>> {}
}
