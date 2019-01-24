package ji.core;

import com.google.common.annotations.VisibleForTesting;
import fj.*;
import fj.data.List;
import fj.data.Validation;
import ji.loader.Compoundable;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.Transformer.ForAdvice;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.MethodManifestation;
import net.bytebuddy.description.modifier.ModifierContributor.ForMethod;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Simple;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall.ArgumentLoader;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.Class;

import static java.util.Collections.singletonMap;
import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.INJECTION;
import static net.bytebuddy.implementation.MethodCall.invoke;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;

/**
 * A function to build a {@link net.bytebuddy.agent.builder.AgentBuilder.Transformer}.
 */
interface Hint extends F<F<ClassLoader, Validation<Exception, Plugin.Matchable>>, AgentBuilder.Transformer> {

    ElementMatcher<? super MethodDescription> METHOD_MATCHER = isAnnotatedWith(Advice.OnMethodEnter.class)
            .or(isAnnotatedWith(Advice.OnMethodExit.class));

    F<MethodDescription, String> DEFAULT_KEY = md -> md.getDeclaringType().getTypeName() + "#" + md.getName();

    /**
     *
     */
    final class Default implements Hint {

        static Default of(ByteBuddy bb, Compoundable comp, TypeDescription td) {
            final DynamicType.Unloaded<?> unloaded = GenerateInlineClass.Default.of(bb).f(td);
            final String inlineClass = unloaded.getTypeDescription().getTypeName();
            final Registry registry = GenerateHandler.Default.of(bb).f(td);
            final ClassFileLocator locator = new ClassFileLocator.Simple(singletonMap(inlineClass, unloaded.getBytes()));
            return new Default(comp, locator, inlineClass, registry);
        }

        private final Compoundable comp;
        private final ClassFileLocator locator;
        private final String inlineClass;
        private final Registry registry;

        @VisibleForTesting
        Default(Compoundable comp, ClassFileLocator locator, String inlineClass, Registry registry) {
            this.comp = comp;
            this.locator = locator;
            this.inlineClass = inlineClass;
            this.registry = registry;
        }

        @Override
        public AgentBuilder.Transformer f(F<ClassLoader, Validation<Exception, Plugin.Matchable>> advice) {
            return (b, td, cl, m) -> advice.f(comp.include(cl)).validation(
                    e -> {
                        Logger.LOG.warn(e, "Failed to transform %s", td);
                        return b;
                    },
                    o -> {
                        registry.f(o);
                        return new ForAdvice().include(locator).advice(o.method(), inlineClass).transform(b, td, cl, m);
                    }
            );
        }
    }

    /**
     * Generate inline class for advice by {@link net.bytebuddy.agent.builder.AgentBuilder}.
     *
     * @see Advice.OnMethodEnter#inline()
     * @see Advice.OnMethodExit#inline()
     */
    interface GenerateInlineClass extends F<TypeDescription, DynamicType.Unloaded<?>> {

        final class Default implements GenerateInlineClass {

            private static final F<TypeDescription, String> NAME_CLASS = td -> td.getTypeName() + "$InlineClass";

            static Default of(ByteBuddy bb) {
                return new Default(bb, DEFAULT_KEY, METHOD_MATCHER, NAME_CLASS);
            }

            private final ByteBuddy bb;
            private final F<MethodDescription, String> key;
            private final F<TypeDescription, String> name;
            private final ElementMatcher<? super MethodDescription> adviceMethod;

            private Default(
                    ByteBuddy bb,
                    F<MethodDescription, String> key,
                    ElementMatcher<? super MethodDescription> adviceMethod,
                    F<TypeDescription, String> name
            ) {
                this.bb = bb;
                this.key = key;
                this.name = name;
                this.adviceMethod = adviceMethod;
            }

            @Override
            public DynamicType.Unloaded<?> f(TypeDescription td) {
                final DynamicType.Builder<?> b = bb.subclass(Object.class).name(name.f(td));
                return List.iterableList(td.getDeclaredMethods().filter(adviceMethod))
                           .foldLeft(this::defineMethod, b)
                           .make();
            }

            private DynamicType.Builder<?> defineMethod(DynamicType.Builder<?> b, MethodDescription md) {
                final ForMethod[] modifiers = {Ownership.STATIC, Visibility.PUBLIC, MethodManifestation.FINAL};
                final Simple<?> i = b.defineMethod(md.getName(), md.getReturnType(), modifiers);
                return List.iterableList(md.getParameters())
                           .foldLeft(this::withParameter, i)
                           .intercept(call(md))
                           .annotateMethod(md.getDeclaredAnnotations());
            }

            private Implementation call(MethodDescription md) {
                try {
                    final ArgumentLoader.Factory name = ArgumentLoader.ForStackManipulation.of(key.f(md));
                    return invoke(Dispatcher.class.getDeclaredMethod("execute", String.class, Object[].class))
                            .with(name, ArgumentLoader.ForMethodParameterArray.ForInstrumentedMethod.INSTANCE)
                            .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC);
                } catch (NoSuchMethodException e) {
                    return FixedValue.nullValue();
                }
            }

            private Simple<?> withParameter(Simple<?> i, ParameterDescription pd) {
                return i.withParameter(pd.getType()).annotateParameter(pd.getDeclaredAnnotations());
            }
        }
    }

    /**
     * Generate handlers for registering to {@link Dispatcher}.
     *
     * @see Dispatcher.Handler
     */
    interface GenerateHandler extends F<TypeDescription, Registry> {

        final class Default implements GenerateHandler {
            private static final String FIELD_ADVICE = "advice";
            private static final F<MethodDescription, String> NAME_CLASS =
                    md -> md.getDeclaringType().getTypeName() + "$Handler$" + md.getName();

            static Default of(ByteBuddy bb) {
                return new Default(bb, DEFAULT_KEY, METHOD_MATCHER, NAME_CLASS);
            }

            private final ByteBuddy bb;
            private final F<MethodDescription, String> key;
            private final F<MethodDescription, String> name;
            private final ElementMatcher<? super MethodDescription> adviceMethod;

            Default(
                    ByteBuddy bb,
                    F<MethodDescription, String> key,
                    ElementMatcher<? super MethodDescription> adviceMethod,
                    F<MethodDescription, String> name
            ) {
                this.bb = bb;
                this.key = key;
                this.name = name;
                this.adviceMethod = adviceMethod;
            }

            @Override
            public Registry f(TypeDescription td) {
                final List<P2<String, DynamicType.Unloaded<Object>>> p2s =
                        List.iterableList(td.getDeclaredMethods().filter(adviceMethod))
                            .map(d -> P.p(key.f(d), make(d)));
                return advice -> {
                    final Class<?> adviceClass = advice.getClass();
                    return p2s.map(p -> p.map2(u -> u.load(adviceClass.getClassLoader(), INJECTION).getLoaded()))
                              .map(p -> p.map2(Try.f(c -> c.getDeclaredConstructor(adviceClass).newInstance(advice))))
                              .foldLeft((u, p) -> {
                                  Dispatcher.register(p._1(), (Dispatcher.Handler) p._2().success());
                                  return u;
                              }, Unit.unit())
                            ;
                };
            }

            private DynamicType.Unloaded<Object> make(MethodDescription md) {
                return bb.subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                         .implement(Dispatcher.Handler.class)
                         .name(name.f(md))
                         .defineField(FIELD_ADVICE, md.getDeclaringType(), Visibility.PRIVATE, FieldManifestation.FINAL)
                         .defineConstructor(Visibility.PUBLIC)
                         .withParameter(md.getDeclaringType())
                         .intercept(setField())
                         .method(isDeclaredBy(Dispatcher.Handler.class))
                         .intercept(call(md))
                         .make();
            }

            private Implementation call(MethodDescription md) {
                return invoke(md)
                        .onField(FIELD_ADVICE)
                        .withArgumentArrayElements(0, md.getParameters().size())
                        .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC);
            }

            private Implementation setField() {
                try {
                    return invoke(Object.class.getDeclaredConstructor())
                            .andThen(FieldAccessor.ofField(FIELD_ADVICE).setsArgumentAt(0));
                } catch (Exception e) {
                    // No chance to be here.
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    interface Registry extends F<Object, Unit> {}
}
