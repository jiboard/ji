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

import fj.F;
import fj.data.Validation;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationList;
import net.bytebuddy.description.annotation.AnnotationSource;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.annotation.Annotation;

import static net.bytebuddy.matcher.ElementMatchers.annotationType;
import static net.bytebuddy.matcher.ElementMatchers.named;

enum AnnotationAccessor implements F<AnnotationSource, Validation<Exception, AnnotationValue<?, ?>>> {

    CONF(Plugin.Conf.class, "value"),
    EXPORT(Plugin.Export.class, "value"),
    INJECT(Plugin.Inject.class, "value"),
    TRANSFORM(Plugin.Transform.class, "with");

    private final ElementMatcher<? super AnnotationDescription> matcher;
    private final MethodDescription.InDefinedShape method;

    AnnotationAccessor(Class<? extends Annotation> ann, String method) {
        final TypeDescription td = TypeDescription.ForLoadedType.of(ann);
        this.matcher = annotationType(td);
        this.method = td.getDeclaredMethods().filter(named(method)).getOnly();
    }

    @Override
    public Validation<Exception, AnnotationValue<?, ?>> f(AnnotationSource annotationSource) {
        final AnnotationList list = annotationSource.getDeclaredAnnotations().filter(matcher);
        return Functional.validation(list.size() == 1, () -> list.getOnly().getValue(method), () -> new IllegalStateException("None " + matcher));
    }
}
