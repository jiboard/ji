
/*
 *  Copyright 2019 Zhong Lunfu
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package ji.core;

import com.google.auto.service.AutoService;
import fj.data.List;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class AutoServicesTest {
    @Test
    public void should_get_classes() {
        assertThat(AutoServices.classesAnnotated(Foo.class), is(List.arrayList(Bar.class.getName())));
    }

    private interface Foo {}

    @AutoService(Foo.class)
    private static class Bar implements Foo {}
}