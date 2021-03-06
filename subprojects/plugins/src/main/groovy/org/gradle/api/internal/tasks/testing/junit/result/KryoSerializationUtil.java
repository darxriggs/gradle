/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.tasks.testing.junit.result;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.gradle.internal.UncheckedException;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public abstract class KryoSerializationUtil {

    private KryoSerializationUtil() {
    }

    public static void writeString(String string, Charset charset, Output output) {
        byte[] bytes;
        try {
            bytes = string.getBytes(charset.name());
        } catch (UnsupportedEncodingException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        output.writeInt(bytes.length, true);
        output.writeBytes(bytes);
    }

    public static String readString(Charset charset, Input input) {
        int length = input.readInt(true);
        byte[] bytes = new byte[length];
        input.readBytes(bytes);
        try {
            return new String(bytes, charset.name());
        } catch (UnsupportedEncodingException e) {
            // shouldn't happen
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public static void skipNext(Input input) {
        int length = input.readInt(true);
        input.skip(length);
    }

}
