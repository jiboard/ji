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
import fj.data.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.method.ParameterDescription;
import net.bytebuddy.description.modifier.MethodManifestation;
import net.bytebuddy.description.modifier.ModifierContributor.ForMethod;
import net.bytebuddy.description.modifier.Ownership;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.DynamicType.Builder;
import net.bytebuddy.dynamic.DynamicType.Builder.MethodDefinition.ParameterDefinition.Simple;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.implementation.MethodCall.ArgumentLoader.ForMethodParameterArray;
import net.bytebuddy.implementation.MethodCall.ArgumentLoader.ForStackManipulation;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;

import static net.bytebuddy.implementation.MethodCall.ArgumentLoader;
import static net.bytebuddy.implementation.MethodCall.invoke;

final class InlineAdviceGenerator implements F<Class<?>, DynamicType.Unloaded<?>> {

    private final ByteBuddy byteBuddy;
    private final ElementMatcher<MethodDescription.InDefinedShape> adviceMethod;
    private final F<MethodDescription, String> key;
    private final F<TypeDescription, String> name;

    InlineAdviceGenerator(
            ByteBuddy byteBuddy,
            ElementMatcher<MethodDescription.InDefinedShape> adviceMethod,
            F<TypeDescription, String> name,
            F<MethodDescription, String> key
    ) {
        this.byteBuddy = byteBuddy;
        this.adviceMethod = adviceMethod;
        this.key = key;
        this.name = name;
    }

    @Override
    public DynamicType.Unloaded<?> f(Class<?> c) {
        final TypeDescription desc = TypeDescription.ForLoadedType.of(c);
        final Builder<?> b = byteBuddy.subclass(Object.class).name(name.f(desc));
        return List.iterableList(desc.getDeclaredMethods().filter(adviceMethod))
                   .foldLeft(this::defineMethod, b)
                   .make();
    }

    private Builder<?> defineMethod(Builder<?> b, MethodDescription.InDefinedShape desc) {
        final ForMethod[] modifiers = {Ownership.STATIC, Visibility.PUBLIC, MethodManifestation.FINAL};
        final Simple<?> i = b.defineMethod(desc.getName(), desc.getReturnType(), modifiers);
        return List.iterableList(desc.getParameters())
                   .foldLeft(this::withParameter, i)
                   .intercept(call(desc))
                   .annotateMethod(desc.getDeclaredAnnotations());
    }

    private Implementation call(MethodDescription.InDefinedShape desc) {
        try {
            final ArgumentLoader.Factory name = ForStackManipulation.of(key.f(desc));
            return invoke(Dispatcher.class.getDeclaredMethod("execute", String.class, Object[].class))
                    .with(name, ForMethodParameterArray.ForInstrumentedMethod.INSTANCE)
                    .withAssigner(Assigner.DEFAULT, Assigner.Typing.DYNAMIC);
        } catch (NoSuchMethodException e) {
            return FixedValue.nullValue();
        }
    }

    private Simple<?> withParameter(Simple<?> i, ParameterDescription.InDefinedShape desc) {
        return i.withParameter(desc.getType()).annotateParameter(desc.getDeclaredAnnotations());
    }

}
