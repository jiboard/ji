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
import fj.P2;
import fj.data.Validation;

final class Filters {

    static <A, B, E extends Exception> F<P2<A, Validation<E, B>>, Boolean> successOrWarn(F<A, String> debug, F<A, String> warn) {
        return p -> {
            if (p._2().isSuccess()) {
                Logger.singleton().debug(() -> debug.f(p._1()));
                return true;
            } else {
                Logger.singleton().warn(() -> warn.f(p._1()), p._2().fail());
                return false;
            }
        };
    }

    private Filters() {}
}
