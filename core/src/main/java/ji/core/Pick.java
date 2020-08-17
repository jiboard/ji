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

import com.typesafe.config.Config;
import fj.*;
import fj.data.List;
import fj.data.TreeMap;
import fj.data.Validation;
import fj.function.Try1;
import ji.core.Pick.BuildParameters.GetValues;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.TypeManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.StackManipulation;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.implementation.bytecode.collection.ArrayFactory;
import net.bytebuddy.implementation.bytecode.constant.TextConstant;
import net.bytebuddy.implementation.bytecode.member.MethodInvocation;
import net.bytebuddy.implementation.bytecode.member.MethodReturn;
import net.bytebuddy.implementation.bytecode.member.MethodVariableAccess;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.description.type.TypeDescription.ForLoadedType.of;
import static net.bytebuddy.description.type.TypeDescription.Generic.OBJECT;
import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * A function to call the method described by {@link MethodDescription}, which parameters were given by other object.
 *
 * @param <A> object can provide value of parameter.
 * @param <B> object return by called method.
 */
interface Pick<A, B> extends F<MethodDescription, F<A, Validation<Exception, B>>> {

    /**
     * Generate a dynamic function object to call.
     *
     * @param <A>
     * @param <B>
     * @param <C>
     * @param <D>
     */
    final class Default<A extends CallMethod<B>, B, C, D extends BuildParameters<C>> implements Pick<C, B> {

        private final ByteBuddy bb;
        private final GetValues<D> getValues;
        private final java.lang.Class<A> callMethodClass;

        static <A extends CallMethod<B>, B, C, D extends BuildParameters<C>> Default<A, B, C, D> of(
                ByteBuddy bb, java.lang.Class<A> callMethodClass, GetValues<D> getValues
        ) { return new Default<>(bb, callMethodClass, getValues);}

        private Default(ByteBuddy bb, java.lang.Class<A> callMethodClass, GetValues<D> getValues) {
            this.bb = bb;
            this.getValues = getValues;
            this.callMethodClass = callMethodClass;
        }

        @Override
        public F<C, Validation<Exception, B>> f(MethodDescription md) {
            final ClassLoader loader = getClass().getClassLoader();
            return c -> {
                final Unloaded<A> method = CallMethod.Generate.Default.of(bb, callMethodClass).f(md);
                return BuildParameters.Generate.Default.of(bb, getValues).f(md)
                                                       .bind(Try.f(u -> Dynamic.instance(u, loader)))
                                                       .bind(Try.f(a -> a.f(c)))
                                                       .bind(Try.f(a -> Dynamic.instance(method, loader).f(a)));
            };
        }

    }


    /**
     * A function tries to transform a given object to parameter array.
     *
     * @param <T>
     */
    interface BuildParameters<T> extends Try1<T, Object[], Exception> {

        /**
         * A function tries to generate {@link StackManipulation} by {@link ParameterDescription}.
         *
         * @param <T>
         */
        abstract class GetValues<T extends BuildParameters<?>> implements F<ParameterDescription, Validation<Exception, StackManipulation>> {

            /**
             * Get parameter's value from {@link Config}.
             */
            static final GetValues<BuildParameters.ByConfig> CONF = new GetValues<BuildParameters.ByConfig>(AnnotationAccessor.CONF, BuildParameters.ByConfig.class) {
                final TypeDescription td = of(Config.class);
                final TreeMap<TypeDescription.Generic, MethodDescription> methods = getMethodOfConfig();

                @Override
                protected F<String, StackManipulation> call(TypeDescription.Generic g) {
                    final MethodDescription md = methods.get(g).orElse(methods.get(OBJECT)).some();
                    return s -> new StackManipulation.Compound(
                            MethodVariableAccess.REFERENCE.loadFrom(1),
                            new TextConstant(s),
                            MethodInvocation.invoke(md).virtual(td)
                    );
                }

                private TreeMap<TypeDescription.Generic, MethodDescription> getMethodOfConfig() {
                    final ElementMatcher<MethodDescription> matcher = nameStartsWith("get")
                            .and(takesArguments(1))
                            .and(takesArgument(0, String.class))
                            .and(not(nameContains("Duration")))
                            .and(not(nameContains("Config")))
                            .and(not(nameContains("Milliseconds")))
                            .and(not(nameContains("Nanoseconds")))
                            .and(not(nameContains("Bytes")))
                            .and(not(nameContains("Value")));

                    return TreeMap.iterableTreeMap(
                            Ord.contramap(Object::toString, Ord.stringOrd),
                            List.iterableList(td.getDeclaredMethods().filter(matcher))
                                .map(m -> P.p(m.getReturnType(), m))
                    );
                }

            };

            /**
             * Get parameter's value from {@link Gather.Ref}.
             */
            static final GetValues<BuildParameters.ByRef> INJECT = new GetValues<BuildParameters.ByRef>(AnnotationAccessor.INJECT, BuildParameters.ByRef.class) {
                final TypeDescription td = of(Gather.Ref.class);
                final MethodDescription.InDefinedShape method = of(F.class)
                        .getDeclaredMethods()
                        .filter(named("f"))
                        .getOnly();

                @Override
                protected F<String, StackManipulation> call(TypeDescription.Generic g) {
                    return s -> new StackManipulation.Compound(
                            MethodVariableAccess.REFERENCE.loadFrom(1),
                            new TextConstant(Gather.TypeNamingStrategy.DEFAULT.f(g, s)),
                            MethodInvocation.invoke(method).virtual(td)
                    );
                }

            };

            private final AnnotationAccessor aa;
            private final java.lang.Class<T> support;

            private GetValues(AnnotationAccessor aa, java.lang.Class<T> support) {
                this.aa = aa;
                this.support = support;
            }

            @Override
            public final Validation<Exception, StackManipulation> f(ParameterDescription pd) {
                return aa.f(pd).map(a -> a.resolve(String.class)).map(s -> call(pd.getType()).f(s));
            }

            final java.lang.Class<T> support() {
                return support;
            }

            protected abstract F<String, StackManipulation> call(TypeDescription.Generic g);
        }


        /**
         * A function to generate the dynamic type of {@link BuildParameters}.
         *
         * @param <T>
         */
        interface Generate<T extends BuildParameters<?>> extends F<MethodDescription, Validation<Exception, Unloaded<T>>> {

            /**
             * Default implementation.
             *
             * @param <T>
             */
            final class Default<T extends BuildParameters<?>> implements BuildParameters.Generate<T> {

                private final ByteBuddy bb;
                private final GetValues<T> gv;

                static <T extends BuildParameters<?>> Default<T> of(ByteBuddy bb, GetValues<T> gv) {
                    return new Default<>(bb, gv);
                }

                private Default(ByteBuddy bb, GetValues<T> gv) {
                    this.bb = bb;
                    this.gv = gv;
                }

                @Override
                public Validation<Exception, Unloaded<T>> f(MethodDescription md) {
                    return Validation.sequence(Semigroup.firstSemigroup(), List.iterableList(md.getParameters()).map(gv::f))
                                     .map(List::toJavaList)
                                     .map(mc -> bb.subclass(gv.support())
                                                  .modifiers(Visibility.PUBLIC, TypeManifestation.FINAL)
                                                  .method(named("f"))
                                                  .intercept(newArray(mc))
                                                  .make());
                }

                private Implementation newArray(java.util.List<StackManipulation> values) {
                    return new Implementation.Simple(ArrayFactory.forType(OBJECT).withValues(values), MethodReturn.REFERENCE);
                }
            }
        }

        interface ByConfig extends BuildParameters<Config> {}

        interface ByRef extends BuildParameters<Gather.Ref> {}
    }

    /**
     * A function to call a method with object array as parameters.
     *
     * @param <T>
     */
    interface CallMethod<T> extends F<Object[], T> {

        /**
         * A function to generate the dynamic type of {@link CallMethod}.
         *
         * @param <T>
         */
        interface Generate<T extends CallMethod<?>> extends F<MethodDescription, Unloaded<T>> {

            /**
             * Default implementation.
             *
             * @param <T>
             */
            final class Default<T extends CallMethod<?>> implements CallMethod.Generate<T> {

                private final DynamicType.Builder<T> builder;

                static <T extends CallMethod<?>> Default<T> of(ByteBuddy bb, java.lang.Class<T> clazz) {
                    return new Default<>(bb.subclass(clazz).modifiers(Visibility.PUBLIC, TypeManifestation.FINAL));
                }

                private Default(DynamicType.Builder<T> builder) {
                    this.builder = builder;
                }

                @Override
                public Unloaded<T> f(MethodDescription md) {
                    return builder.method(named("f"))
                                  .intercept(call(md))
                                  .make();
                }

                private Implementation call(MethodDescription md) {
                    final int size = md.getParameters().size();
                    final MethodCall invoke = md.isConstructor() ? MethodCall.construct(md) : MethodCall.invoke(md);
                    return invoke.withArgumentArrayElements(0, size).withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC);
                }
            }


        }
    }
}
