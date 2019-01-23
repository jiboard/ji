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

import fj.F;
import fj.P;
import fj.data.HashMap;
import fj.data.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.FieldManifestation;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType.Unloaded;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import java.util.Map;

import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;

final class AdviceHandlerGenerator implements F<Object, Map<String, Dispatcher.Handler>> {

    private static final String FIELD_ADVICE = "advice";

    private final ByteBuddy byteBuddy;
    private final ElementMatcher<? super MethodDescription.InDefinedShape> adviceMethod;
    private final F<MethodDescription, String> key;
    private final F<MethodDescription, String> name;

    AdviceHandlerGenerator(
            ByteBuddy byteBuddy,
            ElementMatcher<? super MethodDescription.InDefinedShape> adviceMethod,
            F<MethodDescription, String> key,
            F<MethodDescription, String> name) {
        this.byteBuddy = byteBuddy;
        this.adviceMethod = adviceMethod;
        this.key = key;
        this.name = name;
    }

    @Override
    public Map<String, Dispatcher.Handler> f(Object advice) {
        final TypeDescription desc = TypeDescription.ForLoadedType.of(advice.getClass());

        return HashMap.iterableHashMap(
                List.iterableList(desc.getDeclaredMethods().filter(adviceMethod))
                    .map(d -> P.p(key.f(d), make(d)))
                    .map(p -> p.map2(u -> instance(u, advice)))
        ).toMap();
    }

    private Unloaded<Object> make(MethodDescription desc) {
        return byteBuddy.subclass(Object.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
                        .implement(Dispatcher.Handler.class)
                        .name(name.f(desc))
                        .defineField(FIELD_ADVICE, desc.getDeclaringType(), Visibility.PRIVATE, FieldManifestation.FINAL)
                        .defineConstructor(Visibility.PUBLIC)
                        .withParameter(desc.getDeclaringType())
                        .intercept(setField())
                        .method(isDeclaredBy(Dispatcher.Handler.class))
                        .intercept(call(desc))
                        .make();
    }

    private Implementation call(MethodDescription desc) {
        return MethodCall.invoke(desc)
                         .onField(FIELD_ADVICE)
                         .withArgumentArrayElements(0, desc.getParameters().size())
                         .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC);
    }

    private Implementation setField() {
        try {
            return MethodCall.invoke(Object.class.getDeclaredConstructor())
                             .andThen(FieldAccessor.ofField(FIELD_ADVICE).setsArgumentAt(0));
        } catch (Exception e) {
            // No chance to be here.
            throw new IllegalStateException(e);
        }
    }

    private Dispatcher.Handler instance(Unloaded<?> unloaded, Object advice) {
        final Class<?> adviceClass = advice.getClass();
        final Class<?> c = unloaded.load(adviceClass.getClassLoader()).getLoaded();
        try {
            return (Dispatcher.Handler) c.getConstructor(adviceClass).newInstance(advice);
        } catch (Exception e) {
            // No chance to be here.
            throw new IllegalStateException(e);
        }
    }
}
