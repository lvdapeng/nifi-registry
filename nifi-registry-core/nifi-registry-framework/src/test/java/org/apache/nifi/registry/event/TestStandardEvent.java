/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.registry.event;

import org.apache.nifi.registry.hook.Event;
import org.apache.nifi.registry.hook.EventField;
import org.apache.nifi.registry.hook.EventFieldName;
import org.apache.nifi.registry.hook.EventType;
import org.junit.Assert;
import org.junit.Test;

public class TestStandardEvent {

    @Test(expected = IllegalStateException.class)
    public void testInvalidEvent() {
        final Event event = new StandardEvent.Builder()
                .eventType(EventType.CREATE_BUCKET)
                .build();

        event.validate();
    }

    @Test
    public void testGetFieldWhenDoesNotExist() {
        final Event event = new StandardEvent.Builder()
                .eventType(EventType.CREATE_BUCKET)
                .build();

        final EventField field = event.getField(EventFieldName.BUCKET_ID);
        Assert.assertNull(field);
    }

}
