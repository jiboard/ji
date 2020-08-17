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

import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.lang.annotation.*;

public interface Plugin {

    /**
     * Indicate to inject a object with qualified identity.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Inject {
        /**
         * @return identity
         * @see Export
         */
        String value() default "";

    }

    /**
     * Indicate to export a singleton object for injection
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Export {
        /**
         * @return identity for qualification when it has multiple objects of the same type.
         * @see Inject
         */
        String value() default "";
    }

    /**
     * Indicate to transform with advice classes.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @interface Transform {
        Class<? extends Matchable>[] with();
    }

    /**
     * Indicate to assign a value from {@link com.typesafe.config.Config}.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.PARAMETER, ElementType.TYPE})
    @interface Conf {
        /**
         * @return key of config item.
         */
        String value();
    }

    interface Matchable {
        ElementMatcher<MethodDescription> method();
    }
}
